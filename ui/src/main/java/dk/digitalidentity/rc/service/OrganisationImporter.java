package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.api.model.ManagerDTO;
import dk.digitalidentity.rc.controller.api.model.OrgUnitDTO;
import dk.digitalidentity.rc.controller.api.model.OrganisationDTO;
import dk.digitalidentity.rc.controller.api.model.OrganisationImportResponse;
import dk.digitalidentity.rc.controller.api.model.PositionDTO;
import dk.digitalidentity.rc.controller.api.model.UserDTO;
import dk.digitalidentity.rc.dao.PendingOrganisationUpdateDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.PendingOrganisationUpdate;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import dk.digitalidentity.rc.dao.model.enums.PendingOrganisationEventType;
import dk.digitalidentity.rc.service.model.OrganisationChangeEvents;
import lombok.Getter;
import lombok.extern.log4j.Log4j;

@Log4j
@Service
public class OrganisationImporter {
	private ThreadLocal<OrganisationChangeEvents> events = new ThreadLocal<>();

	@Autowired
	private UserService userService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SettingsService settingsService;
	
	@Autowired
	private TitleService titleService;
	
	@Autowired
	private PendingOrganisationUpdateDao pendingOrganisationUpdateDao;
	
	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Value("${kle.ui.enabled:false}")
	private boolean kleUiEnabled;
	
	// default values, overriden by settings later
	private boolean itSystemMarkupEnabled = false;
	private boolean organisationEventsEnabled = false;
	
	// TODO: this can be the only version we keep, once the following integrations are updated
	//       - AD Sync
	//       - SOFD V3 Sync
	//       - SOFD Core Sync
	//       - Frille Sync
	//       - Aarhus Sync
	//       - OS2mo Sync
	@Transactional(rollbackFor = Exception.class)
	public OrganisationImportResponse fullSync(OrganisationDTO organisation) throws Exception {
		events.set(new OrganisationChangeEvents());
		
		itSystemMarkupEnabled = settingsService.isItSystemMarkupEnabled();
		organisationEventsEnabled = settingsService.isOrganisationEventsEnabled();
		
		try {
			OrganisationImportResponse response = new OrganisationImportResponse();

			// validate that the payload is sane enough to start processing
			ValidationResult validation = validateOrganisation(organisation);
			if (!validation.isValid()) {
				log.warn("Rejecting payload: " + validation.message);
				throw new Exception(validation.message);
			}

			// convert payload to internal data structure with all relationships established
			List<User> newUsers = new ArrayList<>();
			List<OrgUnit> newOrgUnits = new ArrayList<>();

			mapOrganisation(organisation, newUsers, newOrgUnits);

			// read existing data from database
			List<User> existingUsers = userService.getAllIncludingInactive();			
			List<OrgUnit> existingOrgUnits = orgUnitService.getAllIncludingInactive();
			
			// this modifies existingOrgUnits, so it now contains everything
			processOrgUnits(newOrgUnits, existingOrgUnits, response);

			// this modifies existingUsers, so it now contains everything
			processUsers(newUsers, existingUsers, existingOrgUnits, response);

			// both existing OrgUnits and Users have been merged with new OrgUnits and Users at this point
			setManagers(organisation.getOrgUnits(), existingOrgUnits, existingUsers);
	
			// if everything went okay so far, fire of all collected events
			processEvents();
			
			return response;
		}
		finally {
			events.remove();
		}
	}

	private void mapOrganisation(OrganisationDTO organisation, List<User> newUsers, List<OrgUnit> newOrgUnits) {
		// get all itSystems for mapping purposes
		List<ItSystem> itSystems = itSystemService.getAll();

		// validation ensures that this will work, so we can safely call get() on the rootDTO
		Optional<OrgUnitDTO> rootDTO = organisation.getOrgUnits().stream().filter(ou -> ou.getParentOrgUnitUuid() == null).findFirst();
		OrgUnit root = mapOrgUnitDTOToOrgUnit(rootDTO.get(), null, itSystems);

		buildOrgUnitTree(root, organisation.getOrgUnits(), itSystems);
		copyTreeToList(root, newOrgUnits);

		for (UserDTO userDTO : organisation.getUsers()) {
			User user = mapUserDTOToUser(userDTO, newOrgUnits);

			newUsers.add(user);
		}
	}

	private void copyTreeToList(OrgUnit root, List<OrgUnit> newOrgUnits) {
		newOrgUnits.add(root);
		
		for (OrgUnit child : root.getChildren()) {
			copyTreeToList(child, newOrgUnits);
		}
	}

	private void setManagers(List<OrgUnitDTO> orgUnitDTOs, List<OrgUnit> existingOrgUnits, List<User> existingUsers) {
		Map<String, ManagerDTO> managerMap = new HashMap<>();
		for (OrgUnitDTO orgUnitDTO : orgUnitDTOs) {
			if (!StringUtils.isEmpty(orgUnitDTO.getManager()) && !StringUtils.isEmpty(orgUnitDTO.getManager().getUuid()) && !StringUtils.isEmpty(orgUnitDTO.getManager().getUserId())) {
				managerMap.put(orgUnitDTO.getUuid(), orgUnitDTO.getManager());
			}
		}

		for (OrgUnit existingOrgUnit : existingOrgUnits) {
			ManagerDTO managerDto = managerMap.get(existingOrgUnit.getUuid());

			if (managerDto != null && (existingOrgUnit.getManager() == null || !hasSameKey(managerDto, existingOrgUnit.getManager()))) {
				boolean found = false;

				for (User existingUser : existingUsers) {
					if (hasSameKey(managerDto, existingUser)) {
						setOUManager(existingOrgUnit, existingUser);
						orgUnitService.save(existingOrgUnit);
						found = true;
						break;
					}
				}

				if (!found) {
					log.warn("OrgUnit " + existingOrgUnit.getUuid() + " points to non-existent manager " + managerDto.getUserId());
					if (existingOrgUnit.getManager() != null) {
						setOUManager(existingOrgUnit, null);
						orgUnitService.save(existingOrgUnit);
					}
				}
			}
			else if (managerDto == null && existingOrgUnit.getManager() != null) {
				// make a parent-inherit check
				User manager = getParentManager(existingOrgUnit.getParent());
				if (manager == null || !existingOrgUnit.getManager().getUuid().equals(manager.getUuid())) {
					setOUManager(existingOrgUnit, null);
					orgUnitService.save(existingOrgUnit);
				}
			}
		}

		// any OU without a manager inherits from parent
		for (OrgUnit existingOrgUnit : existingOrgUnits) {
			if (existingOrgUnit.getManager() == null) {
				User manager = getParentManager(existingOrgUnit.getParent());
				if (manager != null) {
					setOUManager(existingOrgUnit, manager);
					orgUnitService.save(existingOrgUnit);
				}
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

	// O(n^2) - not pretty, but actually not that slow either
	private void buildOrgUnitTree(OrgUnit parent, List<OrgUnitDTO> orgUnits, List<ItSystem> itSystems) {		
		for (OrgUnitDTO orgUnit : orgUnits) {
			if (orgUnit.getParentOrgUnitUuid() != null && orgUnit.getParentOrgUnitUuid().equals(parent.getUuid())) {
				OrgUnit ou = mapOrgUnitDTOToOrgUnit(orgUnit, parent, itSystems);

				parent.getChildren().add(ou);

				buildOrgUnitTree(ou, orgUnits, itSystems);
			}
		}
	}

	private User mapUserDTOToUser(UserDTO userDTO, List<OrgUnit> newOrgUnits) {
		User user = new User();
		user.setActive(true);
		user.setEmail(userDTO.getEmail());
		user.setExtUuid(userDTO.getExtUuid());
		user.setLastUpdated(new Date());
		user.setName(userDTO.getName());
		user.setPhone(userDTO.getPhone());
		user.setPositions(new ArrayList<>());
		user.setUserRoleAssignments(new ArrayList<>());
		user.setRoleGroupAssignments(new ArrayList<>());
		user.setUserId(userDTO.getUserId());
		user.setUuid(UUID.randomUUID().toString());
		user.setCpr(userDTO.getCpr());
		user.setKles(new ArrayList<>());
		user.setAltAccounts(new ArrayList<>());
		user.setDoNotInherit(userDTO.isDoNotInherit());
		
		for (PositionDTO positionDTO : userDTO.getPositions()) {
			Position position = new Position();
			position.setCreated(new Date());
			position.setName(positionDTO.getName());
			position.setUserRoleAssignments(new ArrayList<>());
			position.setRoleGroupAssignments(new ArrayList<>());
			position.setUser(user);
			
			for (OrgUnit orgUnit : newOrgUnits) {
				if (orgUnit.getUuid().equals(positionDTO.getOrgUnitUuid())) {				
					position.setOrgUnit(orgUnit);

					// we ignore positionDTOs that points to non-existing OrgUnits
					user.getPositions().add(position);
					break;
				}
			}
			
			if (configuration.getTitles().isEnabled() && !StringUtils.isEmpty(positionDTO.getTitleUuid())) {
				position.setTitle(titleService.getByUuid(positionDTO.getTitleUuid()));
			}
		}

		if (!kleUiEnabled) {
			if (userDTO.getKleInterest() != null) {
				for (String kle : userDTO.getKleInterest()) {
					UserKLEMapping mapping = new UserKLEMapping();
					mapping.setUser(user);
					mapping.setCode(kle);
					mapping.setAssignmentType(KleType.INTEREST);
					
					user.getKles().add(mapping);
				}
			}
			
			if (userDTO.getKlePerforming() != null) {
				for (String kle : userDTO.getKlePerforming()) {
					UserKLEMapping mapping = new UserKLEMapping();
					mapping.setUser(user);
					mapping.setCode(kle);
					mapping.setAssignmentType(KleType.PERFORMING);
					
					user.getKles().add(mapping);						
				}
			}
		}
		
		return user;
	}

	private OrgUnit mapOrgUnitDTOToOrgUnit(OrgUnitDTO orgUnit, OrgUnit parent, List<ItSystem> itSystems) {
		OrgUnit ou = new OrgUnit();
		ou.setActive(true);
		ou.setChildren(new ArrayList<>());
		ou.setItSystems(new ArrayList<>());
		ou.setRoleGroupAssignments(new ArrayList<>());
		ou.setUserRoleAssignments(new ArrayList<>());
		ou.setKles(new ArrayList<>());
		ou.setLastUpdated(new Date());
		ou.setName(orgUnit.getName());
		ou.setParent(parent);
		ou.setUuid(orgUnit.getUuid());

		ou.setLevel(OrgUnitLevel.NONE);
		if (configuration.getOrganisation().isGetLevelsFromApi() && orgUnit.getLevel() != null) {
			ou.setLevel(orgUnit.getLevel());
		}

		if (!kleUiEnabled) {
			if (orgUnit.getKleInterest() != null) {
				for (String kle : orgUnit.getKleInterest()) {
					KLEMapping mapping = new KLEMapping();
					mapping.setOrgUnit(ou);
					mapping.setCode(kle);
					mapping.setAssignmentType(KleType.INTEREST);
					
					ou.getKles().add(mapping);
				}
			}
			
			if (orgUnit.getKlePerforming() != null) {
				for (String kle : orgUnit.getKlePerforming()) {
					KLEMapping mapping = new KLEMapping();
					mapping.setOrgUnit(ou);
					mapping.setCode(kle);
					mapping.setAssignmentType(KleType.PERFORMING);
					
					ou.getKles().add(mapping);						
				}
			}
		}

		if (itSystemMarkupEnabled) {
			for (Long itSystemId : orgUnit.getItSystemIdentifiers()) {
				for (ItSystem itSystem : itSystems) {
					if (itSystem.getId() == itSystemId) {
						ou.getItSystems().add(itSystem);
						break;
					}
				}
			}
		}
		
		return ou;
	}

	private void processOrgUnits(List<OrgUnit> newOrgUnits, List<OrgUnit> existingOrgUnits, OrganisationImportResponse response) {
		List<OrgUnit> toBeCreated = new ArrayList<>();
		List<OrgUnit> toBeUpdated = new ArrayList<>();
		List<OrgUnit> toBeDeleted = new ArrayList<>();
		
		identifyOrgUnitChanges(newOrgUnits, existingOrgUnits, toBeCreated, toBeUpdated, toBeDeleted);
		
		response.setOusCreated(toBeCreated.size());
		response.setOusDeleted(toBeDeleted.size());
		response.setOusUpdated(toBeUpdated.size());

		if (toBeCreated.size() > 0) {
			log.info("Creating " + toBeCreated.size() + " OrgUnits");

			for (OrgUnit orgUnitToCreate : toBeCreated) {
				if (orgUnitToCreate.getParent() != null) {
					// if the orgUnit we are going to create, has a parent that exists, move the pointer
					// to the existing object, so Hibernate does not throw a hissy fit.
					for (OrgUnit existingOrgUnit : existingOrgUnits) {
						if (orgUnitToCreate.getParent().getUuid().equals(existingOrgUnit.getUuid())) {
							orgUnitToCreate.setParent(existingOrgUnit);

							break;
						}
					}
				}
			}
			
			// bulk-save, and then copy them all to the list of existing orgUnits
			orgUnitService.save(toBeCreated);
			existingOrgUnits.addAll(toBeCreated);
		}
		
		if (toBeUpdated.size() > 0) {
			log.info("Updating " + toBeUpdated.size() + " OrgUnits");

			for (OrgUnit orgUnitToUpdate : toBeUpdated) {
				for (OrgUnit existingOrgUnit : existingOrgUnits) {
					if (existingOrgUnit.getUuid().equals(orgUnitToUpdate.getUuid())) {
						existingOrgUnit.setActive(true);
						existingOrgUnit.setLastUpdated(new Date());
						existingOrgUnit.setName(orgUnitToUpdate.getName());
						
						if (configuration.getOrganisation().isGetLevelsFromApi()) {
							existingOrgUnit.setLevel(orgUnitToUpdate.getLevel());
						}

						if (!kleUiEnabled) {
							existingOrgUnit.getKles().clear();
							existingOrgUnit.getKles().addAll(orgUnitToUpdate.getKles());

							// fix back-references
							for (KLEMapping kleMapping : existingOrgUnit.getKles()) {
								kleMapping.setOrgUnit(existingOrgUnit);
							}
						}

						if (itSystemMarkupEnabled) {
							existingOrgUnit.setItSystems(orgUnitToUpdate.getItSystems());
						}
	
						// is there a change to parent, then we need to do something about it
						if (differentParents(existingOrgUnit, orgUnitToUpdate)) {
							if (orgUnitToUpdate.getParent() == null) {
								setOUParent(existingOrgUnit, null);
							}
							else {
								for (OrgUnit parentCandidate : existingOrgUnits) {
									if (parentCandidate.getUuid().equals(orgUnitToUpdate.getParent().getUuid())) {
										setOUParent(existingOrgUnit, parentCandidate);
										break;
									}
								}
							}
						}
						
						orgUnitService.save(existingOrgUnit);
						break;
					}
				}
			}
		}
		
		if (toBeDeleted.size() > 0) {
			log.info("Deleting " + toBeDeleted.size() + " OrgUnits");
			
			for (OrgUnit orgUnitToDelete : toBeDeleted) {				
				orgUnitToDelete.setActive(false);
				orgUnitService.save(orgUnitToDelete);
			}
		}
	}
	
	private void processUsers(List<User> newUsers, List<User> existingUsers, List<OrgUnit> existingOrgUnits, OrganisationImportResponse response) {
		List<User> toBeCreated = new ArrayList<>();
		List<User> toBeUpdated = new ArrayList<>();
		List<User> toBeDeleted = new ArrayList<>();
		
		identifyUserChanges(newUsers, existingUsers, toBeCreated, toBeUpdated, toBeDeleted);
		
		response.setUsersCreated(toBeCreated.size());
		response.setUsersDeleted(toBeDeleted.size());
		response.setUsersUpdated(toBeUpdated.size());

		if (toBeCreated.size() > 0) {
			log.info("Creating " + toBeCreated.size() + " Users");

			for (User userToCreate : toBeCreated) {
				// if the user we are going to create, has a position, move the pointer
				// to an existing OrgUnit, otherwise Hibernate is going to throw a hissy fit
				if (userToCreate.getPositions() != null) {
					List<Position> positions = userToCreate.getPositions();
					userToCreate.setPositions(new ArrayList<>());
					
					for (Position position : positions) {
						for (OrgUnit existingOrgUnit : existingOrgUnits) {
							if (position.getOrgUnit().getUuid().equals(existingOrgUnit.getUuid())) {
								position.setOrgUnit(existingOrgUnit);
								addPosition(userToCreate, position);

								break;
							}
						}						
					}
				}
			}

			userService.save(toBeCreated);
			existingUsers.addAll(toBeCreated);
		}
		
		if (toBeUpdated.size() > 0) {
			log.info("Updating " + toBeUpdated.size() + " Users");

			for (User userToUpdate : toBeUpdated) {
				for (User existingUser : existingUsers) {
					if (hasSameKey(existingUser, userToUpdate)) {
						log.info("Updating: " + existingUser.getUserId());

						existingUser.setActive(true);
						existingUser.setEmail(userToUpdate.getEmail());
						existingUser.setLastUpdated(new Date());
						existingUser.setName(userToUpdate.getName());
						existingUser.setPhone(userToUpdate.getPhone());
						existingUser.setUserId(userToUpdate.getUserId());
						existingUser.setCpr(userToUpdate.getCpr());
						existingUser.setDoNotInherit(userToUpdate.isDoNotInherit());

						if (!kleUiEnabled) {
							existingUser.getKles().clear();
							existingUser.getKles().addAll(userToUpdate.getKles());

							// fix back-references
							for (UserKLEMapping kleMapping : existingUser.getKles()) {
								kleMapping.setUser(existingUser);
							}
						}

						if (differentPositions(userToUpdate, existingUser)) {
							// do the switcheroo to make Hibernate happy on all the usertoUpdate's positions
							for (Position userToUpdatePosition : userToUpdate.getPositions()) {
								for (OrgUnit existingOrgUnit : existingOrgUnits) {
									if (userToUpdatePosition.getOrgUnit().getUuid().equals(existingOrgUnit.getUuid())) {
										userToUpdatePosition.setOrgUnit(existingOrgUnit);

										break;
									}
								}
								
								userToUpdatePosition.setUser(existingUser);
							}

							// if there are new positions add them to existing object
							for (Position newPosition : userToUpdate.getPositions()) {
								if (!containsPosition(newPosition, existingUser.getPositions())) {
									addPosition(existingUser, newPosition);
								}
							}

							// remove if existing user has positions that are not in the new one
							List<Position> positionsToRemove = new ArrayList<>();
							for (Position existingPosition : existingUser.getPositions()) {
								if (!containsPosition(existingPosition, userToUpdate.getPositions())) {
									positionsToRemove.add(existingPosition);
								}
							}

							for (Position positionToRemove : positionsToRemove) {
								userService.removePosition(existingUser, positionToRemove);
							}
						}

						userService.save(existingUser);

						break;
					}
				}
			}
		}
		
		if (toBeDeleted.size() > 0) {
			log.info("Deleting " + toBeDeleted.size() + " Users");
			
			for (User userToDelete : toBeDeleted) {				
				userToDelete.setActive(false);
				userService.save(userToDelete);
			}
		}
	}
	
	private void identifyUserChanges(List<User> newUsers, List<User> existingUsers, List<User> toBeCreated, List<User> toBeUpdated, List<User> toBeDeleted) {

		// find those that needs to be created or updated
		for (User newUser : newUsers) {
			boolean found = false;

			for (User existingUser : existingUsers) {
				if (hasSameKey(existingUser, newUser)) {
					found = true;

					if (!existingUser.isActive()) {
						toBeUpdated.add(newUser);
					}
					else if (!existingUser.getName().equals(newUser.getName())) {
						toBeUpdated.add(newUser);
					}
					else if (!Objects.equals(existingUser.getCpr(), newUser.getCpr())) {
						toBeUpdated.add(newUser);
					}
					else if (!newUser.getUserId().equals(existingUser.getUserId())) {
						toBeUpdated.add(newUser);
					}
					else if (differentPhone(newUser, existingUser)) {
						toBeUpdated.add(newUser);
					}
					else if (differentEmail(newUser, existingUser)) {
						toBeUpdated.add(newUser);
					}
					else if (!kleUiEnabled && hasKleChanges(newUser, existingUser)) {
						toBeUpdated.add(newUser);
					}
					else if (differentPositions(newUser, existingUser)) {
						toBeUpdated.add(newUser);
					}
					else if (existingUser.isDoNotInherit() != newUser.isDoNotInherit()) {
						toBeUpdated.add(newUser);
					}

					break;
				}
			}
			
			if (!found) {
				User userToCreate = new User();
				userToCreate.setActive(true);
				userToCreate.setEmail(newUser.getEmail());
				userToCreate.setExtUuid(newUser.getExtUuid());
				userToCreate.setKles(newUser.getKles());
				userToCreate.setName(newUser.getName());
				userToCreate.setPhone(newUser.getPhone());
				userToCreate.setUserId(newUser.getUserId());
				userToCreate.setUuid(newUser.getUuid());
				userToCreate.setRoleGroupAssignments(new ArrayList<>());
				userToCreate.setUserRoleAssignments(new ArrayList<>());
				userToCreate.setPositions(new ArrayList<>());
				userToCreate.setDoNotInherit(newUser.isDoNotInherit());
				for (Position p : newUser.getPositions()) {
					addPosition(userToCreate, p);
				}

				toBeCreated.add(userToCreate);
			}
		}
		
		// find those that should be set to inactive
		for (User existingUser : existingUsers) {
			boolean found = false;

			// ignore those that are already inactive
			if (!existingUser.isActive()) {
				continue;
			}

			for (User newUser : newUsers) {
				if (hasSameKey(newUser, existingUser)) {
					found = true;
					break;
				}
			}
			
			if (!found) {
				toBeDeleted.add(existingUser);
			}
		}
	}
	
	private static boolean hasSameKey(ManagerDTO managerDto, User manager) {
		if (managerDto.getUserId().equals(manager.getUserId()) && managerDto.getUuid().equals(manager.getExtUuid())) {
			return true;
		}
		
		return false;
	}

	private static boolean hasSameKey(User user1, User user2) {
		if (user1.getExtUuid().equals(user2.getExtUuid()) && user1.getUserId().equals(user2.getUserId())) {
			return true;
		}

		return false;
	}

	private void identifyOrgUnitChanges(List<OrgUnit> newOrgUnits, List<OrgUnit> existingOrgUnits, List<OrgUnit> toBeCreated, List<OrgUnit> toBeUpdated, List<OrgUnit> toBeDeleted) {
		
		// find those that needs to be created or updated
		for (OrgUnit newOrgUnit : newOrgUnits) {
			boolean found = false;

			for (OrgUnit existingOrgUnit : existingOrgUnits) {
				if (existingOrgUnit.getUuid().equals(newOrgUnit.getUuid())) {
					found = true;

					if (!existingOrgUnit.isActive()) {
						toBeUpdated.add(newOrgUnit);
					}
					else if (differentParents(existingOrgUnit, newOrgUnit)) {
						toBeUpdated.add(newOrgUnit);
					}
					else if (!existingOrgUnit.getName().equals(newOrgUnit.getName())) {
						toBeUpdated.add(newOrgUnit);
					}
					else if (!kleUiEnabled && hasKleChanges(newOrgUnit, existingOrgUnit)) {
						toBeUpdated.add(newOrgUnit);
					}
					else if (itSystemMarkupEnabled && hasItSystemChanges(newOrgUnit, existingOrgUnit)) {
						toBeUpdated.add(newOrgUnit);
					}
					else if (configuration.getOrganisation().isGetLevelsFromApi() && differentOrgUnitLevels(existingOrgUnit, newOrgUnit)) {
						toBeUpdated.add(newOrgUnit);
					}
					
					break;
				}
			}
			
			if (!found) {
				toBeCreated.add(newOrgUnit);
			}
		}
		
		// find those that should be set to inactive
		for (OrgUnit existingOrgUnit : existingOrgUnits) {
			boolean found = false;

			// ignore those that are already inactive
			if (!existingOrgUnit.isActive()) {
				continue;
			}

			for (OrgUnit newOrgUnit : newOrgUnits) {
				if (existingOrgUnit.getUuid().equals(newOrgUnit.getUuid())) {
					found = true;
					break;
				}
			}
			
			if (!found) {
				toBeDeleted.add(existingOrgUnit);
			}
		}
	}
	
	//// equals() methods for detecting changes ////

	private boolean hasItSystemChanges(OrgUnit newOrgUnit, OrgUnit existingOrgUnit) {
		for (ItSystem existingOrgUnitItSystem : existingOrgUnit.getItSystems()) {
			boolean itSystemFound = false;

			for (ItSystem newOrgUnitItSystem : newOrgUnit.getItSystems()) {
				if (newOrgUnitItSystem.getId() == existingOrgUnitItSystem.getId()) {
					itSystemFound = true;
					break;
				}
			}
			
			if (!itSystemFound) {
				return true;
			}
		}
		
		for (ItSystem newOrgUnitItSystem : newOrgUnit.getItSystems()) {
			boolean itSystemFound = false;

			for (ItSystem existingOrgUnitItSystem : existingOrgUnit.getItSystems()) {
				if (newOrgUnitItSystem.getId() == existingOrgUnitItSystem.getId()) {
					itSystemFound = true;
					break;
				}
			}
			
			if (!itSystemFound) {
				return true;
			}
		}
		
		return false;
	}

	private boolean hasKleChanges(OrgUnit newOrgUnit, OrgUnit existingOrgUnit) {
		for (KLEMapping existingOrgUnitKle : existingOrgUnit.getKles()) {
			boolean kleFound = false;
			
			for (KLEMapping newOrgUnitKle : newOrgUnit.getKles()) {
				if (kleEquals(existingOrgUnitKle, newOrgUnitKle)) {
					kleFound = true;
					break;
				}
			}
			
			if (!kleFound) {
				return true;
			}
		}
		
		for (KLEMapping newOrgUnitKle : newOrgUnit.getKles()) {
			boolean kleFound = false;
			
			for (KLEMapping existingOrgUnitKle : existingOrgUnit.getKles()) {
				if (kleEquals(existingOrgUnitKle, newOrgUnitKle)) {
					kleFound = true;
					break;
				}
			}
			
			if (!kleFound) {
				return true;
			}
		}

		return false;
	}

	private boolean hasKleChanges(User newUser, User existingUser) {
		for (UserKLEMapping existingOrgUnitKle : existingUser.getKles()) {
			boolean kleFound = false;
			
			for (UserKLEMapping newOrgUnitKle : newUser.getKles()) {
				if (kleEquals(existingOrgUnitKle, newOrgUnitKle)) {
					kleFound = true;
					break;
				}
			}
			
			if (!kleFound) {
				return true;
			}
		}
		
		for (UserKLEMapping newOrgUnitKle : newUser.getKles()) {
			boolean kleFound = false;
			
			for (UserKLEMapping existingOrgUnitKle : existingUser.getKles()) {
				if (kleEquals(existingOrgUnitKle, newOrgUnitKle)) {
					kleFound = true;
					break;
				}
			}
			
			if (!kleFound) {
				return true;
			}
		}

		return false;
	}
	
	private boolean differentOrgUnitLevels(OrgUnit existingOrgUnit, OrgUnit orgUnitToUpdate) {
		return (Objects.equals(existingOrgUnit.getLevel(), orgUnitToUpdate.getLevel()) == false);
	}

	private boolean differentParents(OrgUnit existingOrgUnit, OrgUnit orgUnitToUpdate) {
		if ((existingOrgUnit.getParent() == null && orgUnitToUpdate.getParent() != null) ||
			(existingOrgUnit.getParent() != null && orgUnitToUpdate.getParent() == null) ||
			(existingOrgUnit.getParent() != null && orgUnitToUpdate.getParent() != null && !existingOrgUnit.getParent().getUuid().equals(orgUnitToUpdate.getParent().getUuid()))) {

			return true;
		}

		return false;
	}

	private boolean differentPositions(User newUser, User existingUser) {
		for (Position newPosition : newUser.getPositions()) {
			if (!containsPosition(newPosition, existingUser.getPositions())) {
				return true;
			}
		}

		for (Position existingPosition : existingUser.getPositions()) {
			if (!containsPosition(existingPosition, newUser.getPositions())) {
				return true;
			}
		}
		
		return false;
	}

	private boolean differentEmail(User newUser, User existingUser) {
		if ((existingUser.getEmail() == null && newUser.getEmail() != null) ||
			(existingUser.getEmail() != null && newUser.getEmail() == null) ||
			(existingUser.getEmail() != null && newUser.getEmail() != null && !existingUser.getEmail().equals(newUser.getEmail()))) {

			return true;
		}
		
		return false;
	}

	private boolean differentPhone(User newUser, User existingUser) {
		if ((existingUser.getPhone() == null && newUser.getPhone() != null) ||
			(existingUser.getPhone() != null && newUser.getPhone() == null) ||
			(existingUser.getPhone() != null && newUser.getPhone() != null && !existingUser.getPhone().equals(newUser.getPhone()))) {

			return true;
		}

		return false;
	}

	// TODO: this method is not 100% foolproof, but it works for most cases, and will suffice
	//       until we get a unique (external) identifier for each position
	private static boolean containsPosition(Position position, List<Position> positions) {
		for (Position p : positions) {
			if (p.getName().equals(position.getName()) &&
				p.getOrgUnit().getUuid().equals(position.getOrgUnit().getUuid()) &&
				hasSameKey(p.getUser(), position.getUser()) &&				
				(p.getTitle() == null && position.getTitle() == null || (p.getTitle() != null && position.getTitle() != null && Objects.equals(p.getTitle().getUuid(), position.getTitle().getUuid())))) {

				return true;
			}
		}

		return false;
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

	//// event code ////
	
	private void setOUParent(OrgUnit ou, OrgUnit parent) {
		ou.setParent(parent);
		
		events.get().getOusWithNewParent().add(ou);
	}

	private void setOUManager(OrgUnit ou, User manager) {
		ou.setManager(manager);
		
		events.get().getOusWithNewManager().add(ou);
	}

	private void addPosition(User user, Position position) {
		userService.addPosition(user, position);
		
		events.get().getUsersWithNewPosition().add(position);
	}

	private void processEvents() {
		if (!organisationEventsEnabled) {
			return;
		}

		Set<OrgUnit> ousWithNewManager = events.get().getOusWithNewManager();
		Set<OrgUnit> ousWithNewParent = events.get().getOusWithNewParent();
		Set<Position> usersWithNewPosition = events.get().getUsersWithNewPosition();
				
		for (OrgUnit orgUnit : ousWithNewParent) {
			PendingOrganisationUpdate event = new PendingOrganisationUpdate();
			event.setOrgUnitUuid(orgUnit.getUuid());
			event.setEventType(PendingOrganisationEventType.NEW_PARENT);		
			pendingOrganisationUpdateDao.save(event);
			
			// if the OU was moved, but didn't bring its own manager, it will most likely have a new manager as well
			if (orgUnit.getManager() == null) {
				ousWithNewManager.add(orgUnit);
			}
		}
		
		for (OrgUnit orgUnit : ousWithNewManager) {
			processOuWithNewManager(orgUnit, false);
		}

		for (Position position : usersWithNewPosition) {
			PendingOrganisationUpdate event = new PendingOrganisationUpdate();
			event.setUserUuid(position.getUser().getUuid());
			event.setOrgUnitUuid(position.getOrgUnit().getUuid());
			event.setEventType(PendingOrganisationEventType.NEW_POSITION);		
			pendingOrganisationUpdateDao.save(event);
		}
	}

	private void processOuWithNewManager(OrgUnit orgUnit, boolean isChild) {
		// for children (recursive-handling) stop once first OU with a manager is encountered
		if (isChild && orgUnit.getManager() != null) {
			return;
		}

		PendingOrganisationUpdate event = new PendingOrganisationUpdate();
		event.setOrgUnitUuid(orgUnit.getUuid());
		event.setEventType(PendingOrganisationEventType.NEW_MANAGER);		
		pendingOrganisationUpdateDao.save(event);
		
		if (orgUnit.getChildren() != null) {
			for (OrgUnit child : orgUnit.getChildren()) {
				processOuWithNewManager(child, true);
			}
		}
	}
	
	//// validation logic ////
	
	private ValidationResult validateOrganisation(OrganisationDTO organisation) {
		for (UserDTO user : organisation.getUsers()) {
			if (user.getUserId() == null || user.getUserId().length() == 0) {
				return new ValidationResult(false, "Users with null/empty userId not allowed");
			}

			if (user.getExtUuid() == null) {
				return new ValidationResult(false, "Users with null UUID not allowed: " + user.getUserId());
			}

			if (user.getName() == null || user.getName().length() == 0) {
				return new ValidationResult(false, "User with null/empty name not allowed: " + user.getUserId());
			}

			if (user.getPositions() != null) {
				for (PositionDTO position : user.getPositions()) {
					if (position.getName() == null || position.getName().length() == 0) {
						return new ValidationResult(false, "Users with null/empty titles are not allowed: " + user.getUserId());
					}
					
					if (position.getOrgUnitUuid() == null) {
						return new ValidationResult(false, "Users with a position with null/empty orgUnit is not allowed: " + user.getUserId());
					}
					
					boolean found = false;
					for (OrgUnitDTO orgUnit : organisation.getOrgUnits()) {
						if (position.getOrgUnitUuid().equals(orgUnit.getUuid())) {
							found = true;
							break;
						}
					}
					
					if (!found) {
						return new ValidationResult(false, "Users with a position in non-existent OrgUnits are not allowed: " + user.getUserId() + " / " + position.getOrgUnitUuid());
					}
				}
			}
			
			try {
				UUID.fromString(user.getExtUuid());
			}
			catch (Exception ex) {
				return new ValidationResult(false, "Users must have a valid UUID: " + user.getUserId() + " / " + user.getExtUuid());
			}
		}

		// build parent-hierarchy (we need it for validation below)
		for (OrgUnitDTO orgUnit : organisation.getOrgUnits()) {
			if (orgUnit.getParentOrgUnitUuid() != null) {
				for (OrgUnitDTO parent : organisation.getOrgUnits()) {
					if (parent.getUuid().equals(orgUnit.getParentOrgUnitUuid())) {
						orgUnit.set_parentRef(parent);
						break;
					}
				}
			}
		}

		// check that hierarchy is as it should be for levels

		for (OrgUnitDTO orgUnit : organisation.getOrgUnits()) {
			// all parents must be higher level or none
			if (orgUnit.getLevel() != null && !orgUnit.getLevel().equals(OrgUnitLevel.NONE)) {
				OrgUnitDTO parent = orgUnit.get_parentRef();
				while (parent != null) {
					if (parent.getLevel() != null && !parent.getLevel().equals(OrgUnitLevel.NONE)) {
						switch (orgUnit.getLevel()) {
							case LEVEL_1:
								// can only set level 1 if no parent has a level set
								return new ValidationResult(false, "Orgunit has higher level than parent: " + orgUnit.getUuid());
							case LEVEL_2:
								if (!parent.getLevel().equals(OrgUnitLevel.LEVEL_1)) {
									return new ValidationResult(false, "Orgunit has higher or same level as parent: " + orgUnit.getUuid());
								}
								break;
							case LEVEL_3:
								if (!parent.getLevel().equals(OrgUnitLevel.LEVEL_1) &&
									!parent.getLevel().equals(OrgUnitLevel.LEVEL_2)) {
									return new ValidationResult(false, "Orgunit has higher or same level as parent: " + orgUnit.getUuid());
								}
								break;
							case LEVEL_4:
								if (!parent.getLevel().equals(OrgUnitLevel.LEVEL_1) &&
									!parent.getLevel().equals(OrgUnitLevel.LEVEL_2) &&
									!parent.getLevel().equals(OrgUnitLevel.LEVEL_3)) {
									return new ValidationResult(false, "Orgunit has higher or same level as parent: " + orgUnit.getUuid());
								}
								break;
							case NONE:
								// not actually happening due to previous validation
								break;
						}
					}

					parent = parent.get_parentRef();
				}
			}
		}
		
		long rootCount = 0;
		for (OrgUnitDTO orgUnit : organisation.getOrgUnits()) {
			if (orgUnit.getParentOrgUnitUuid() == null) {
				rootCount++;
			}

			if (orgUnit.getUuid() == null) {
				return new ValidationResult(false, "OrgUnit with null UUID not allowed");
			}
			
			if (orgUnit.getName() == null || orgUnit.getName().length() == 0) {
				return new ValidationResult(false, "OrgUnit with null/empty name not allowed: " + orgUnit.getUuid());
			}

			try {
				UUID.fromString(orgUnit.getUuid());
			}
			catch (Exception ex) {
				return new ValidationResult(false, "OrgUnits must have a valid UUID: " + orgUnit.getUuid());
			}
		}
		
		if (rootCount != 1) {
			return new ValidationResult(false, "There must be exactly 1 root OrgUnit, but " + rootCount + " was supplied");
		}

		return new ValidationResult(true, null);
	}

	@Getter
	class ValidationResult {
		boolean valid;
		String message;
		
		ValidationResult(boolean valid, String message) {
			this.valid = valid;
			this.message = message;
		}
	}
}
