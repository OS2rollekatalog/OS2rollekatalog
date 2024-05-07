package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.api.dto.OrganisationV2DTO;
import dk.digitalidentity.rc.controller.api.model.FullOrgUnitAM;
import dk.digitalidentity.rc.controller.api.model.FullUserAM;
import dk.digitalidentity.rc.controller.api.model.OrgUnitAM;
import dk.digitalidentity.rc.controller.api.model.OrganisationImportResponse;
import dk.digitalidentity.rc.controller.api.model.PositionAM;
import dk.digitalidentity.rc.controller.api.model.UserAM;
import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class OrganisationImporterOld {
	@Autowired
	private UserService userService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	public OrganisationImportResponse bigImportV2(OrganisationV2DTO organisation) throws Exception {
		FullOrgUnitAM root = null;
		
		for (FullOrgUnitAM orgUnit : organisation.getOrgUnits()) {
			if (orgUnit.getParentOrgUnitUuid() == null || orgUnit.getParentOrgUnitUuid().length() == 0) {
				if (root != null) {
					throw new Exception("Rejecting payload due to > 1 root OrgUnit");
				}
				else {
					root = orgUnit;
				}
			}
		}
		
		if (root == null) {
			throw new Exception("Rejecting payload due to 0 root OrgUnits");			
		}
		
		OrgUnitAM rootOrgUnit = new OrgUnitAM();
		rootOrgUnit.setChildren(new ArrayList<>());
		rootOrgUnit.setEmployees(new ArrayList<>());
		rootOrgUnit.setKleInterest(root.getKleInterest());
		rootOrgUnit.setKlePerforming(root.getKlePerforming());
		rootOrgUnit.setName(root.getName());
		rootOrgUnit.setUuid(root.getUuid());
		rootOrgUnit.setItSystemIdentifiers(root.getItSystemIdentifiers());
		rootOrgUnit.setManagerUuid(root.getManagerUuid());
		
		buildOrgUnitTree(rootOrgUnit, organisation.getOrgUnits());
		
		mapEmployees(rootOrgUnit, organisation.getUsers());
		
		return bigImport(rootOrgUnit);
	}

	public OrganisationImportResponse bigImport(OrgUnitAM rootOrgUnit) throws Exception {
		OrganisationImportResponse response = new OrganisationImportResponse();
		List<User> users = new ArrayList<>();
		List<OrgUnit> ous = new ArrayList<>();

		// convert incoming DTOs to Entity classes with all required relationships established
		traverse(ous, users, rootOrgUnit, null);

		String errorMsg = null;
		if ((errorMsg = validateUsers(users)) != null) {
			log.warn("Rejecting payload due to bad users: " + errorMsg);
			throw new Exception(errorMsg);
		}
		else if ((errorMsg = validateOUs(ous)) != null) {
			log.warn("Rejecting payload due to bad ous: " + errorMsg);
			throw new Exception(errorMsg);
		}

		// we have to use getAll to ensure we update existing (but inactive) users
		List<User> existingUsers = userService.getAllIncludingInactive();			
		List<OrgUnit> existingOUs = orgUnitService.getAllIncludingInactive();
		
		processOrgUnits(ous, existingOUs, response);
		softDeleteOUs(ous, existingOUs, response);

		processUsers(users, existingUsers, existingOUs, response);
		softDeleteUsers(users, existingUsers, response);

		ensureUserConsistency(users, existingUsers);

		// reload and set managers
		existingUsers = userService.getAllIncludingInactive();
		existingOUs = orgUnitService.getAllIncludingInactive();

		setManagers(rootOrgUnit, existingOUs, existingUsers);
		
		return response;
	}

	private void setManagers(OrgUnitAM root, List<OrgUnit> ous, List<User> users) {
		Map<String, String> managerMap = new HashMap<>();
		populateManagerMap(root, managerMap);

		for (OrgUnit ou : ous) {
			String managerUuid = managerMap.get(ou.getUuid());

			if (managerUuid != null && (ou.getManager() == null || !ou.getManager().getUuid().equals(managerUuid))) {
				boolean found = false;

				for (User user : users) {
					if (user.getUuid().equals(managerUuid)) {
						setOUManager(ou, user);
						orgUnitService.save(ou);
						found = true;
						break;
					}
				}
				
				if (!found) {
					log.warn("OrgUnit " + ou.getUuid() + " points to non-existent manager " + managerUuid);
					if (ou.getManager() != null) {
						setOUManager(ou, null);
						orgUnitService.save(ou);
					}
				}
			}
			else if (managerUuid == null && ou.getManager() != null) {
				// make a parent-inherit check
				User manager = getParentManager(ou.getParent());
				if (manager == null || !ou.getManager().getUuid().equals(manager.getUuid())) {
					setOUManager(ou, null);
					orgUnitService.save(ou);
				}
			}
		}

		// any OU without a manager inherits from parent
		for (OrgUnit ou : ous) {
			if (ou.getManager() == null) {
				User manager = getParentManager(ou.getParent());
				if (manager != null) {
					setOUManager(ou, manager);
					orgUnitService.save(ou);
				}
			}
		}
	}

	private static void populateManagerMap(OrgUnitAM root, Map<String, String> managerMap) {
		if (StringUtils.hasLength(root.getManagerUuid())) {
			managerMap.put(root.getUuid(), root.getManagerUuid());
		}

		if (root.getChildren() != null) {
			for (OrgUnitAM ou : root.getChildren()) {
				populateManagerMap(ou, managerMap);
			}
		}
	}

	private static User getParentManager(OrgUnit orgUnit) {
		if (orgUnit == null) {
			return null;
		}
		
		if (orgUnit.getManager() != null) {
			return orgUnit.getManager();
		}
		
		return getParentManager(orgUnit.getParent());
	}

	// O(n * m) - not pretty, but okay
	private void mapEmployees(OrgUnitAM orgUnit, List<FullUserAM> users) {
		for (FullUserAM user : users) {
			if (user.getPositions() != null) {
				for (PositionAM position : user.getPositions()) {

					if (position.getOrgUnitUuid() != null && position.getOrgUnitUuid().equals(orgUnit.getUuid())) {
						UserAM u = new UserAM();
						u.setName(user.getName());
						u.setTitle(position.getName());
						u.setUser_id(user.getUser_id());
						u.setUuid(user.getUuid());
						u.setEmail(user.getEmail());
						u.setPhone(user.getPhone());
						u.setKleInterest(user.getKleInterest());
						u.setKlePerforming(user.getKlePerforming());

						orgUnit.getEmployees().add(u);
					}
				}
			}
		}
		
		for (OrgUnitAM ou : orgUnit.getChildren()) {
			mapEmployees(ou, users);
		}
	}

	// O(n^2) - not pretty, but actually not that slow either
	private void buildOrgUnitTree(OrgUnitAM root, List<FullOrgUnitAM> orgUnits) {
		
		for (FullOrgUnitAM orgUnit : orgUnits) {
			if (orgUnit.getParentOrgUnitUuid() != null && orgUnit.getParentOrgUnitUuid().equals(root.getUuid())) {
				OrgUnitAM ou = new OrgUnitAM();
				ou.setChildren(new ArrayList<>());
				ou.setEmployees(new ArrayList<>());
				ou.setKleInterest(orgUnit.getKleInterest());
				ou.setKlePerforming(orgUnit.getKlePerforming());
				ou.setName(orgUnit.getName());
				ou.setUuid(orgUnit.getUuid());
				ou.setItSystemIdentifiers(orgUnit.getItSystemIdentifiers());
				ou.setManagerUuid(orgUnit.getManagerUuid());
				
				root.getChildren().add(ou);
	
				buildOrgUnitTree(ou, orgUnits);
			}
		}
	}

	private String validateOUs(List<OrgUnit> ous) {
		for (OrgUnit orgUnit : ous) {
			if (orgUnit.getName() == null || orgUnit.getName().length() == 0) {
				return "OrgUnit with null/empty name not allowed";
			}
			
			if (orgUnit.getUuid() == null) {
				return "OrgUnit with null UUID not allowed";
			}
			
			try {
				UUID.fromString(orgUnit.getUuid());
			}
			catch (Exception ex) {
				return "OrgUnits must have a valid UUID, encountered error while paring: " + ex.getMessage();
			}
		}

		return null;
	}

	private void ensureUserConsistency(List<User> users, List<User> existingUsers) {
		for (User user : users) {
			ensureUserConsistency(user, existingUsers);
		}
	}

	// At the municipality end, the userId is often the unique key, and we use the UUID as the primary key,
	// so it happens that a municipality changes the UUID of a user, resulting in multiple rows in our database.
	// This ensures that only a single record is active at any given point (the last updated users to be specific)
	private void ensureUserConsistency(User user, List<User> existingUsers) {
		for (User existingUser : existingUsers) {
			if (existingUser.getUserId().equals(user.getUserId()) && !existingUser.getUuid().equals(user.getUuid())) {
				if (!existingUser.isDeleted()) {
					existingUser.setDeleted(true);

					userService.save(existingUser);
				}
			}
		}
	}

	private String validateUsers(List<User> users) {
		for (User user : users) {
			if (user.getName() == null || user.getName().length() == 0) {
				return "User with null/empty name not allowed";
			}

			if (user.getUserId() == null || user.getUserId().length() == 0) {
				return "Users with null/empty userId not allowed";
			}
			
			if (user.getUuid() == null) {
				return "Users with null UUID not allowed";
			}

			if (user.getPositions() == null || user.getPositions().size() == 0) {
				return "Users without a position are not allowed";
			}

			for (Position position : user.getPositions()) {
				if (position.getName() == null || position.getName().length() == 0) {
					return "Users with null/empty titles are not allowed";
				}
				
				if (position.getOrgUnit() == null) {
					return "Users with a position in inactive/non-existent OrgUnits are not allowed";
				}
			}
			
			try {
				UUID.fromString(user.getUuid());
			}
			catch (Exception ex) {
				return "Users must have a valid UUID, encountered error while paring: " + ex.getMessage();
			}
		}

		return null;
	}

	private OrgUnit traverse(List<OrgUnit> ous, List<User> users, OrgUnitAM orgUnit, OrgUnit parent) {
		OrgUnit orgUnitEntity = map(orgUnit, parent);

		// must be before recursive call otherwise the order is wrong
		ous.add(orgUnitEntity);

		loadUsers(users, orgUnit.getEmployees(), orgUnitEntity);

		// depth-first traversal of incoming tree structure
		if (orgUnit.getChildren() != null) {
			for (OrgUnitAM child : orgUnit.getChildren()) {
				traverse(ous, users, child, orgUnitEntity);
			}
		}

		return orgUnitEntity;
	}

	private void processOrgUnits(List<OrgUnit> newOus, List<OrgUnit> existingOUs, OrganisationImportResponse response) {
		long updated = 0, created = 0;

		for (OrgUnit ou : newOus) {
			boolean found = false;

			for (OrgUnit existingOU : existingOUs) {
				if (ou.getUuid().equals(existingOU.getUuid())) {
					boolean changes = false;
					found = true;

					// If existing OU was soft-deleted we restore it
					if (!existingOU.isActive()) {
						existingOU.setActive(true);
						changes = true;
					}

					// Relocate scenario
					if ((existingOU.getParent() == null && ou.getParent() != null) || 
						(existingOU.getParent() != null && ou.getParent() == null) ||
						(existingOU.getParent() != null && ou.getParent() != null && !existingOU.getParent().getUuid().equals(ou.getParent().getUuid()))) {
						setOUParent(existingOU, ou.getParent());
						changes = true;
					}

					// Rename scenario
					if (!ou.getName().equals(existingOU.getName())) {
						existingOU.setName(ou.getName());
						changes = true;
					}

					// Only if KLE management is disabled in ui
					if (!configuration.getIntegrations().getKle().isUiEnabled()) {
						// Add KLEs
						for (KLEMapping newKle : ou.getKles()) {
							boolean kleFound = false;
							for (KLEMapping oldKle : existingOU.getKles()) {
								if (kleEquals(newKle, oldKle)) {
									kleFound = true;
									break;
								}
							}

							if (!kleFound) {
								newKle.setOrgUnit(existingOU);
								existingOU.getKles().add(newKle);

								changes = true;
							}
						}

						// Remove KLEs
						for (Iterator<KLEMapping> iterator = existingOU.getKles().iterator(); iterator.hasNext(); ) {
							KLEMapping oldKle = iterator.next();

							boolean kleFound = false;
							for (KLEMapping newKle : ou.getKles()) {
								if (kleEquals(newKle, oldKle)) {
									kleFound = true;
									break;
								}
							}

							if (!kleFound) {
								iterator.remove();

								changes = true;
							}
						}
					}

					// Update existing OrgUnit with new data
					if (changes) {
						updated++;
						orgUnitService.save(existingOU);
					}

					break;
				}
			}

			// if not found we're dealing with a totally new OU add it to database
			if (!found) {
				created++;
				orgUnitService.save(ou);
			}
		}

		if (response != null) {
			response.setOusCreated(created);
			response.setOusUpdated(updated);
		}
		
		log.debug("Updated " + updated + " ou(s) and  created " + created + " ou(s)");
	}

	private void setOUParent(OrgUnit ou, OrgUnit parent) {
		ou.setParent(parent);
	}

	private void setOUManager(OrgUnit ou, User manager) {
		ou.setManager(manager);
	}

	private void addPosition(User user, Position position) {
		userService.addPosition(user, position);
	}

	private void softDeleteOUs(List<OrgUnit> newOus, List<OrgUnit> existingOUs, OrganisationImportResponse response) {
		long deleted = 0;

		for (OrgUnit existingOU : existingOUs) {
			boolean found = false;

			if (!existingOU.isActive()) {
				continue;
			}

			for (OrgUnit ou : newOus) {
				if (ou.getUuid().equals(existingOU.getUuid())) {
					found = true;
				}
			}

			if (!found) {
				existingOU.setActive(false);
				orgUnitService.save(existingOU);
				deleted++;
			}
		}
		
		if (response != null) {
			response.setOusDeleted(deleted);
		}
		
		log.debug("Deleted " + deleted + " ou(s)");
	}

	private void processUsers(List<User> newUsers, List<User> existingUsers, List<OrgUnit> existingOrgUnits, OrganisationImportResponse response) {
		long updated = 0;
		List<User> tobeAdded = new ArrayList<>();

		// full import case
		if (existingOrgUnits != null) {
			// in the case, where a user has gained a NEW position in an EXISTING orgunit,
			// then the users Position-reference points to the JSON-provided version of the OrgUnit,
			// which we need to update, so it points to the existing version from the database,
			// otherwise we cannot check against userroles/rolegroups later on
			for (User newUser : newUsers) {
				if (newUser.getPositions() != null) {
					for (Position position : newUser.getPositions()) {
						for (OrgUnit orgUnit : existingOrgUnits) {
							if (position.getOrgUnit().getUuid().equals(orgUnit.getUuid())) {
								position.setOrgUnit(orgUnit);
							}
						}
					}
				}
			}
		}
		
		for (User newUser : newUsers) {
			boolean found = false;

			for (User existingUser : existingUsers) {
				if (newUser.getUuid().equals(existingUser.getUuid())) {
					found = true;
					boolean changes = false;

					// if existing User was soft-deleted we restore it
					if (existingUser.isDeleted()) {
						existingUser.setDeleted(false);
						changes = true;
					}

					// if there are new positions add them to existing object
					for (Position newPosition : newUser.getPositions()) {
						if (!containsPosition(newPosition, existingUser.getPositions())) {
							addPosition(existingUser, newPosition);
							changes = true;
						}
					}

					// remove if existing user has positions that are not in the new one
					List<Position> positionsToRemove = new ArrayList<>();
					for (Position existingPosition : existingUser.getPositions()) {
						if (!containsPosition(existingPosition, newUser.getPositions())) {
							positionsToRemove.add(existingPosition);
							changes = true;
						}
					}
										
					for (Position positionToRemove : positionsToRemove) {
						userService.removePosition(existingUser, positionToRemove);
					}

					if (!configuration.getIntegrations().getKle().isUiEnabled()) {
						// if there are changes to the users KLE, update them
						List<UserKLEMapping> existingKles = existingUser.getKles();
						if (existingKles == null) {
							existingKles = new ArrayList<>();
						}
						
						// find KLEs to remove
						List<UserKLEMapping> klesToRemove = new ArrayList<>();
						for (UserKLEMapping existingKle : existingKles) {
							boolean foundKle = false;
	
							for (UserKLEMapping newKle : newUser.getKles()) {
								if (kleEquals(newKle, existingKle)) {
									found = true;
									break;
								}
							}
	
							if (!foundKle) {
								klesToRemove.add(existingKle);
								changes = true;
							}
						}
						
						// remove them
						for (UserKLEMapping kleToRemove : klesToRemove) {
							userService.removeKLE(existingUser, kleToRemove.getAssignmentType(), kleToRemove.getCode());
						}
						
						// find KLEs to add
						for (UserKLEMapping newKle : newUser.getKles()) {
							boolean foundKle = false;
							
							for (UserKLEMapping existingKle : existingKles) {
								if (kleEquals(newKle, existingKle)) {
									found = true;
									break;
								}
							}
							
							if (!foundKle) {
								userService.addKLE(existingUser, newKle.getAssignmentType(), newKle.getCode());
								changes = true;
							}
						}
					}
					
					if (!existingUser.getName().equals(newUser.getName())) {
						existingUser.setName(newUser.getName());
						changes = true;
					}

					if ((existingUser.getEmail() == null && newUser.getEmail() != null) ||
						(existingUser.getEmail() != null && newUser.getEmail() == null) ||
						(existingUser.getEmail() != null && newUser.getEmail() != null && !existingUser.getEmail().equals(newUser.getEmail()))) {
						existingUser.setEmail(newUser.getEmail());
						changes = true;
					}
					
					if ((existingUser.getPhone() == null && newUser.getPhone() != null) ||
						(existingUser.getPhone() != null && newUser.getPhone() == null) ||
						(existingUser.getPhone() != null && newUser.getPhone() != null && !existingUser.getPhone().equals(newUser.getPhone()))) {
						existingUser.setPhone(newUser.getPhone());
						changes = true;
					}

					if (!existingUser.getUserId().equals(newUser.getUserId())) {
						existingUser.setUserId(newUser.getUserId());
						changes = true;
					}

					if (changes) {
						updated++;
						existingUser.setLastUpdated(new Date()); // position changes does not trigger lastUpdated
						userService.save(existingUser);
					}

					break;
				}
			}

			if (!found) {
				User userToCreate = new User();
				userToCreate.setDeleted(false);
				userToCreate.setEmail(newUser.getEmail());
				userToCreate.setExtUuid(newUser.getUuid());
				userToCreate.setKles(newUser.getKles());
				userToCreate.setName(newUser.getName());
				userToCreate.setPhone(newUser.getPhone());
				userToCreate.setUserId(newUser.getUserId());
				userToCreate.setUuid(newUser.getUuid());
				userToCreate.setRoleGroupAssignments(new ArrayList<>());
				userToCreate.setUserRoleAssignments(new ArrayList<>());
				userToCreate.setPositions(new ArrayList<>());
				for (Position p : newUser.getPositions()) {
					addPosition(userToCreate, p);
				}

				tobeAdded.add(userToCreate);
			}
		}

		log.debug("Updated " + updated + " user(s) and created " + tobeAdded.size() + " user(s)");

		if (response != null) {
			response.setUsersCreated(tobeAdded.size());
			response.setUsersUpdated(updated);
		}

		// if there are 0 users in the database, we probably have a truckload of changes
		// and in this case, we can actually do a safe bulk insert with the entityManager,
		// but if we do it with existing data, we can get reference issues with relations
		// to the OU's that might not be flushed yet
		bulkInsert(tobeAdded, existingUsers.size() == 0);
	}
	
	private void bulkInsert(List<User> list, boolean flush) {
		if (flush) {
			entityManager.flush();
	
			for (int i = 0; i < list.size() ; ++i) {
				entityManager.persist(list.get(i));
				if (i % 20 == 0) {
					entityManager.flush();
					entityManager.clear();
				}
			}
			
			entityManager.flush();
			entityManager.clear();
		}
		else {
			userService.save(list);
		}
	}

	private void softDeleteUsers(List<User> newUsers, List<User> existingUsers, OrganisationImportResponse response) {
		long deleted = 0;
		
		for (User existingUser : existingUsers) {
			boolean found = false;

			if (existingUser.isDeleted()) {
				continue;
			}

			for (User newUser : newUsers) {
				if (newUser.getUuid().equals(existingUser.getUuid())) {
					found = true;
				}
			}

			if (!found) {
				//soft delete
				deleted++;
				existingUser.setDeleted(true);
				userService.save(existingUser);
			}
		}
		
		if (response != null) {
			response.setUsersDeleted(deleted);
		}

		log.debug("Deleted " + deleted + " user(s)");
	}

	private static boolean containsPosition(Position position, List<Position> positions) {
		for (Position p : positions) {
			if (p.getName().equals(position.getName())
					&& p.getOrgUnit().getUuid().equals(position.getOrgUnit().getUuid())
					&& p.getUser().getUuid().equals(position.getUser().getUuid())) {
				return true;
			}
		}

		return false;
	}


	private void loadUsers(List<User> users, List<UserAM> list, OrgUnit orgUnitEntity) {
		if (list != null) {
			for (UserAM user : list) {
				boolean userExists = false;
				User userEntity = null;
	
				if ((userEntity = getUser(users, user)) != null) {
					userExists = true;
				}
	
				if (!userExists) {
					userEntity = map(user);
				}
				
				// add KLE
				if (userEntity.getKles() == null) {
					userEntity.setKles(new ArrayList<>());
				}
				
				if (user.getKleInterest() != null) {
					for (String kle : user.getKleInterest()) {
						UserKLEMapping mapping = new UserKLEMapping();
						mapping.setAssignmentType(KleType.INTEREST);
						mapping.setCode(kle);
						mapping.setUser(userEntity);
						userEntity.getKles().add(mapping);
					}
				}

				if (user.getKlePerforming() != null) {
					for (String kle : user.getKlePerforming()) {
						UserKLEMapping mapping = new UserKLEMapping();
						mapping.setAssignmentType(KleType.PERFORMING);
						mapping.setCode(kle);
						mapping.setUser(userEntity);
						userEntity.getKles().add(mapping);
					}
				}

				createPosition(orgUnitEntity, userEntity, user.getTitle());

				if (!userExists) {
					users.add(userEntity);
				}
			}
		}
	}

	private void createPosition(OrgUnit orgUnitEntity, User userEntity, String title) {
		Position position = new Position();
		position.setName(title);
		position.setOrgUnit(orgUnitEntity);
		position.setRoleGroupAssignments(new ArrayList<>());
		position.setUserRoleAssignments(new ArrayList<>());
		position.setUser(userEntity);

		// do not use the userService addPosition method here, it has a nasty side-effect
		if (userEntity.getPositions() == null) {
			userEntity.setPositions(new ArrayList<>());
		}

		if (!userEntity.getPositions().contains(position)) {
			userEntity.getPositions().add(position);
		}
	}

	private User getUser(List<User> users, UserAM user) {
		for (Iterator<User> iterator = users.iterator(); iterator.hasNext();) {
			User u = iterator.next();

			if (u.getUuid().equals(user.getUuid())) {
				return u;
			}
		}

		return null;
	}

	private static User map(UserAM user) {
		User userEntity = new User();
		userEntity.setName(user.getName());
		userEntity.setUuid(user.getUuid());
		userEntity.setUserId(user.getUser_id());
		userEntity.setPhone(user.getPhone());
		userEntity.setEmail(user.getEmail());
		userEntity.setPositions(new ArrayList<Position>());
		userEntity.setRoleGroupAssignments(new ArrayList<>());
		userEntity.setUserRoleAssignments(new ArrayList<>());
		userEntity.setDeleted(false);

		return userEntity;
	}

	private static boolean kleEquals(KLEMapping newKle, KLEMapping oldKle) {
		if (newKle.getCode().equals(oldKle.getCode()) && newKle.getAssignmentType().equals(oldKle.getAssignmentType())) {
			return true;
		}

		return false;
	}
	
	private static boolean kleEquals(UserKLEMapping newKle, UserKLEMapping oldKle) {
		if (newKle.getCode().equals(oldKle.getCode()) && newKle.getAssignmentType().equals(oldKle.getAssignmentType())) {
			return true;
		}

		return false;
	}

	private OrgUnit map(OrgUnitAM orgUnit, OrgUnit parent) {
		OrgUnit orgUnitEntity = new OrgUnit();
		orgUnitEntity.setUuid(orgUnit.getUuid());
		orgUnitEntity.setName(orgUnit.getName());
		orgUnitEntity.setChildren(new ArrayList<OrgUnit>());
		orgUnitEntity.setKles(new ArrayList<KLEMapping>());
		orgUnitEntity.setRoleGroupAssignments(new ArrayList<>());
		orgUnitEntity.setUserRoleAssignments(new ArrayList<>());
		orgUnitEntity.setParent(parent);
		orgUnitEntity.setActive(true);
		orgUnitEntity.setLevel(OrgUnitLevel.NONE);

		handleKLEs(orgUnitEntity, orgUnit.getKleInterest(), KleType.INTEREST);
		handleKLEs(orgUnitEntity, orgUnit.getKlePerforming(), KleType.PERFORMING);

		return orgUnitEntity;
	}

	private static void handleKLEs(OrgUnit orgUnitEntity, List<String> kles, KleType kleType) {
		if (kles != null) {
			for (String kleCode : kles) {
				KLEMapping kle = new KLEMapping();
				kle.setAssignmentType(kleType);
				kle.setCode(kleCode);
				kle.setOrgUnit(orgUnitEntity);

				orgUnitEntity.getKles().add(kle);
			}
		}
	}
}
