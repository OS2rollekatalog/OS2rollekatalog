package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.api.model.ManagerDTO;
import dk.digitalidentity.rc.controller.api.model.OrgUnitDTO;
import dk.digitalidentity.rc.controller.api.model.OrganisationDTO;
import dk.digitalidentity.rc.controller.api.model.OrganisationImportResponse;
import dk.digitalidentity.rc.controller.api.model.PositionDTO;
import dk.digitalidentity.rc.controller.api.model.UserDTO;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.Notification;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.dao.model.enums.NotificationEntityType;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import dk.digitalidentity.rc.service.model.MovedPostion;
import dk.digitalidentity.rc.service.model.OrgUnitWithNewAndOldParentDTO;
import dk.digitalidentity.rc.service.model.OrgUnitWithTitlesDTO;
import dk.digitalidentity.rc.service.model.OrganisationChangeEvents;
import dk.digitalidentity.rc.service.model.UserDeletedEvent;
import dk.digitalidentity.rc.service.model.UserMovedPositions;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
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
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private NotificationService notificationService;

	@Autowired
	private EmailTemplateService emailTemplateService;

	@Autowired
	private EmailQueueService emailQueueService;
	
	@Transactional(rollbackFor = Exception.class)
	public OrganisationImportResponse fullSync(OrganisationDTO organisation, Domain domain) throws Exception {
		boolean isPrimaryDomain = DomainService.isPrimaryDomain(domain);
		events.set(new OrganisationChangeEvents());

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
			List<User> existingUsers = userService.getAllIncludingInactive(domain);
			List<OrgUnit> existingOrgUnits = orgUnitService.getAllIncludingInactive();

			// this modifies existingOrgUnits, so it now contains everything - only if primary domain
			if (isPrimaryDomain) {
				processOrgUnits(newOrgUnits, existingOrgUnits, response);
			}

			// this modifies existingUsers, so it now contains everything
			processUsers(newUsers, existingUsers, existingOrgUnits, response, false, domain);

			// both existing OrgUnits and Users have been merged with new OrgUnits and Users at this point - only if primary domain
			if (isPrimaryDomain) {
				setManagers(organisation.getOrgUnits(), existingOrgUnits, existingUsers);
			}

			// if everything went okay so far, fire of all collected events
			processEvents();
			
			return response;
		}
		finally {
			events.remove();
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public OrganisationImportResponse deltaSync(List<UserDTO> users, Domain domain) throws Exception {
		events.set(new OrganisationChangeEvents());
		
		try {
			OrganisationImportResponse response = new OrganisationImportResponse();

			// validate that the payload is sane enough to start processing
			ValidationResult validation = validateUsers(users, null);
			if (validation != null && !validation.isValid()) {
				log.warn("Rejecting payload: " + validation.message);
				throw new Exception(validation.message);
			}

			// convert payload to internal data structure with all relationships established
			List<User> newUsers = new ArrayList<>();
			List<OrgUnit> existingOrgUnits = orgUnitService.getAllIncludingInactive();

			// map user DTO to User objects
			for (UserDTO userDTO : users) {
				User user = mapUserDTOToUser(userDTO, existingOrgUnits);

				newUsers.add(user);
			}

			// read existing data from database (have to read them all, as extUuid might have changed... well, we could ready by UserID I guess)
			List<User> existingUsers = userService.getAllIncludingInactive(domain);

			// this modifies existingUsers, so it now contains everything
			processUsers(newUsers, existingUsers, existingOrgUnits, response, true, domain);

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
		
		// get all titles for mapping purposes
		List<Title> titles = titleService.getAll();

		// validation ensures that this will work, so we can safely call get() on the rootDTO
		Optional<OrgUnitDTO> rootDTO = organisation.getOrgUnits().stream().filter(ou -> ou.getParentOrgUnitUuid() == null).findFirst();
		OrgUnit root = mapOrgUnitDTOToOrgUnit(rootDTO.get(), null, itSystems, titles);

		buildOrgUnitTree(root, organisation.getOrgUnits(), itSystems, titles);
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
			if (orgUnitDTO.getManager() != null && StringUtils.hasLength(orgUnitDTO.getManager().getUuid()) && StringUtils.hasLength(orgUnitDTO.getManager().getUserId())) {
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
	private void buildOrgUnitTree(OrgUnit parent, List<OrgUnitDTO> orgUnits, List<ItSystem> itSystems, List<Title> titles) {		
		for (OrgUnitDTO orgUnit : orgUnits) {
			if (orgUnit.getParentOrgUnitUuid() != null && orgUnit.getParentOrgUnitUuid().equals(parent.getUuid())) {
				OrgUnit ou = mapOrgUnitDTOToOrgUnit(orgUnit, parent, itSystems, titles);

				parent.getChildren().add(ou);

				buildOrgUnitTree(ou, orgUnits, itSystems, titles);
			}
		}
	}

	private User mapUserDTOToUser(UserDTO userDTO, List<OrgUnit> newOrgUnits) {
		User user = new User();
		user.setDeleted(false);
		user.setEmail(userDTO.getEmail());
		user.setNemloginUuid(userDTO.getNemloginUuid());
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
		user.setDisabled(userDTO.isDisabled());
		
		if (userDTO.getPositions() != null) {
			for (PositionDTO positionDTO : userDTO.getPositions()) {
				Position position = new Position();
				position.setCreated(new Date());
				position.setName(positionDTO.getName());
				position.setUserRoleAssignments(new ArrayList<>());
				position.setRoleGroupAssignments(new ArrayList<>());
				position.setUser(user);
				// support old clients that do not send doNotInherit on positions by using the value from userDTO
				position.setDoNotInherit(positionDTO.getDoNotInherit() != null ? positionDTO.getDoNotInherit() : userDTO.isDoNotInherit());
				
				for (OrgUnit orgUnit : newOrgUnits) {
					// we ignore positionDTOs that points to non-existing OrgUnits
					if (orgUnit.getUuid().equals(positionDTO.getOrgUnitUuid())) {				
						position.setOrgUnit(orgUnit);
	
						user.getPositions().add(position);
						break;
					}
				}
				
				if (configuration.getTitles().isEnabled() && StringUtils.hasLength(positionDTO.getTitleUuid())) {
					position.setTitle(titleService.getByUuid(positionDTO.getTitleUuid()));
				}
			}
		}

		if (!configuration.getIntegrations().getKle().isUiEnabled()) {
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

	private OrgUnit mapOrgUnitDTOToOrgUnit(OrgUnitDTO orgUnit, OrgUnit parent, List<ItSystem> itSystems, List<Title> titles) {
		OrgUnit ou = new OrgUnit();
		ou.setActive(true);
		ou.setChildren(new ArrayList<>());
		ou.setRoleGroupAssignments(new ArrayList<>());
		ou.setUserRoleAssignments(new ArrayList<>());
		ou.setKles(new ArrayList<>());
		ou.setLastUpdated(new Date());
		ou.setName(orgUnit.getName());
		ou.setParent(parent);
		ou.setUuid(orgUnit.getUuid());
		ou.setTitles(new ArrayList<>());

		ou.setLevel(OrgUnitLevel.NONE);
		if (configuration.getOrganisation().isGetLevelsFromApi() && orgUnit.getLevel() != null) {
			ou.setLevel(orgUnit.getLevel());
		}

		if (!configuration.getIntegrations().getKle().isUiEnabled()) {
			ou.setInheritKle(orgUnit.isInheritKle());
			
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

		if (orgUnit.getTitleIdentifiers() != null) {
			for (String titleUuid : orgUnit.getTitleIdentifiers()) {
				for (Title title : titles) {
					if (Objects.equals(title.getUuid(), titleUuid)) {
						// only add if not already added
						if (ou.getTitles().stream().noneMatch(t -> Objects.equals(t.getUuid(), titleUuid))) {
							ou.getTitles().add(title);
						}
						
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
			Iterable<OrgUnit> resultToBeCreated = orgUnitService.save(toBeCreated);
			for (OrgUnit orgUnit : resultToBeCreated) {
				existingOrgUnits.add(orgUnit);				
				events.get().getNewOrgUnits().add(orgUnit);
				
				for (Title title : orgUnit.getTitles()) {
					addNewEmptyTitlesToEvent(orgUnit, title);
				}
			}
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

						if (!configuration.getIntegrations().getKle().isUiEnabled()) {
							existingOrgUnit.setInheritKle(orgUnitToUpdate.isInheritKle());
							existingOrgUnit.getKles().clear();
							existingOrgUnit.getKles().addAll(orgUnitToUpdate.getKles());

							// fix back-references
							for (KLEMapping kleMapping : existingOrgUnit.getKles()) {
								kleMapping.setOrgUnit(existingOrgUnit);
							}
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
						
						if (hasTitleChanges(existingOrgUnit, orgUnitToUpdate)) {
							checkOrgUnitForNewEmptyTitles(existingOrgUnit, orgUnitToUpdate);
							existingOrgUnit.setTitles(orgUnitToUpdate.getTitles());
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

	
	private void processUsers(List<User> newUsers, List<User> existingUsers, List<OrgUnit> existingOrgUnits, OrganisationImportResponse response, boolean deltaSync, Domain domain) {
		List<User> toBeCreated = new ArrayList<>();
		List<User> toBeUpdated = new ArrayList<>();
		List<User> toBeDeleted = new ArrayList<>();
		
		identifyUserChanges(newUsers, existingUsers, toBeCreated, toBeUpdated, toBeDeleted, domain, deltaSync);
		
		response.setUsersCreated(toBeCreated.size());
		response.setUsersDeleted(toBeDeleted.size());
		response.setUsersUpdated(toBeUpdated.size());

		// get list of all MANUAL itSystems
		List<ItSystem> simpleItSystems = itSystemService.getBySystemType(ItSystemType.MANUAL);
		final List<User> usersWithAttachedPositions = new ArrayList<>();

		if (toBeCreated.size() > 0) {
			log.info("Creating " + toBeCreated.size() + " Users");

			for (User userToCreate : toBeCreated) {
				log.info("Creating: " + userToCreate.getUserId());
				// if the user we are going to create, has a position, move the pointer
				// to an existing OrgUnit, otherwise Hibernate is going to throw a hissy fit
				if (userToCreate.getPositions() != null) {
					List<Position> positions = userToCreate.getPositions();
					userToCreate.setPositions(new ArrayList<>());
					
					for (Position position : positions) {
						for (OrgUnit existingOrgUnit : existingOrgUnits) {
							if (position.getOrgUnit().getUuid().equals(existingOrgUnit.getUuid())) {

								checkOrgUnitForNewTitles(position, existingOrgUnit);
								
								position.setOrgUnit(existingOrgUnit);
								addPosition(userToCreate, position);

								break;
							}
						}						
					}
				}
				usersWithAttachedPositions.add(userService.save(userToCreate));
			}

			log.info("Saving users to be created: " + toBeCreated.stream().map(User::getUserId).collect(Collectors.joining(",")));

			existingUsers.addAll(usersWithAttachedPositions);
		}
		
		if (toBeUpdated.size() > 0) {
			log.info("Updating " + toBeUpdated.size() + " Users");

			for (User userToUpdate : toBeUpdated) {
				for (User existingUser : existingUsers) {
					if (hasSameKey(existingUser, userToUpdate)) {
						log.info("Updating: " + existingUser.getUserId());

						// do it like this to trigger interceptors
						if (existingUser.isDeleted()) {
							userService.activateUser(existingUser);
						}

						existingUser.setEmail(userToUpdate.getEmail());
						existingUser.setNemloginUuid(userToUpdate.getNemloginUuid());
						existingUser.setExtUuid(userToUpdate.getExtUuid());
						existingUser.setLastUpdated(new Date());
						existingUser.setName(userToUpdate.getName());
						existingUser.setPhone(userToUpdate.getPhone());
						existingUser.setUserId(userToUpdate.getUserId());
						existingUser.setCpr(userToUpdate.getCpr());
						
						// check whether the user was deleted or disabled
						if (!(existingUser.isDisabled() || existingUser.isDeleted()) && (userToUpdate.isDisabled() || userToUpdate.isDeleted())) {
							handleUserDeletedEvent(simpleItSystems, existingUser);
						}

						existingUser.setDisabled(userToUpdate.isDisabled());

						if (!configuration.getIntegrations().getKle().isUiEnabled()) {
							existingUser.getKles().clear();
							existingUser.getKles().addAll(userToUpdate.getKles());

							// fix back-references
							for (UserKLEMapping kleMapping : existingUser.getKles()) {
								kleMapping.setUser(existingUser);
							}
						}

						if (includePositions(domain) && differentPositions(userToUpdate, existingUser)) {
							UserMovedPositions movedPositionEvent = null;
							if (!existingUser.getUserRoleAssignments().isEmpty() || !existingUser.getRoleGroupAssignments().isEmpty()) {
								movedPositionEvent = new UserMovedPositions();
								movedPositionEvent.setUser(existingUser);
							}

							// do the switcheroo to make Hibernate happy on all the userToUpdate's positions
							for (Position userToUpdatePosition : userToUpdate.getPositions()) {
								for (OrgUnit existingOrgUnit : existingOrgUnits) {
									if (userToUpdatePosition.getOrgUnit().getUuid().equals(existingOrgUnit.getUuid())) {
										checkOrgUnitForNewTitles(userToUpdatePosition, existingOrgUnit);

										userToUpdatePosition.setOrgUnit(existingOrgUnit);

										break;
									}
								}

								userToUpdatePosition.setUser(existingUser);
							}

							// if there are new positions add them to existing object
							for (Position newPosition : userToUpdate.getPositions()) {
								if (!containsPosition(newPosition, existingUser.getPositions())) {
									checkOrgUnitForNewTitles(newPosition, newPosition.getOrgUnit());
									addPosition(existingUser, newPosition);

									if (movedPositionEvent != null) {
										movedPositionEvent.getNewPositions().add(new MovedPostion(newPosition.getOrgUnit().getName(), newPosition.getName()));
									}
								}
							}

							// remove if existing user has positions that are not in the new one
							List<Position> positionsToRemove = new ArrayList<>();
							for (Position existingPosition : existingUser.getPositions()) {
								if (!containsPosition(existingPosition, userToUpdate.getPositions())) {
									positionsToRemove.add(existingPosition);

									if (movedPositionEvent != null) {
										movedPositionEvent.getOldPositions().add(new MovedPostion(existingPosition.getOrgUnit().getName(), existingPosition.getName()));
									}
								}
							}

							for (Position positionToRemove : positionsToRemove) {
								userService.removePosition(existingUser, positionToRemove);
							}

							if (movedPositionEvent != null && !movedPositionEvent.getNewPositions().isEmpty() && !movedPositionEvent.getOldPositions().isEmpty()) {
								events.get().getUsersMovedPostions().add(movedPositionEvent);
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
				handleUserDeletedEvent(simpleItSystems, userToDelete);
				
				userToDelete.setDeleted(true);
				userService.save(userToDelete);
			}
		}
	}

	private void handleUserDeletedEvent(List<ItSystem> simpleItSystems, User userToDelete) {
		if (simpleItSystems.size() == 0) {
			return;
		}

		// if user has roles in simple itSystems
		List<UserRole> userRoles = userService.getAllUserRoles(userToDelete, simpleItSystems);
		if (!userRoles.isEmpty()) {
			List<ItSystem> itSystemsUserHasRoleIn = userRoles.stream().map(ur -> ur.getItSystem()).filter(distinctByKey(i -> i.getId())).toList();
			
			for (ItSystem itSystem : itSystemsUserHasRoleIn) {
				UserDeletedEvent userDeletedEvent = new UserDeletedEvent();
				userDeletedEvent.setUser(userToDelete);
				userDeletedEvent.setEmail(itSystem.getEmail());
				userDeletedEvent.setItSystemName(itSystem.getName());

				events.get().getDeletedUsers().add(userDeletedEvent);
			}
		}
	}

	public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
		Map<Object, Boolean> seen = new ConcurrentHashMap<>();

		return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
	}

	private void checkOrgUnitForNewTitles(Position position, OrgUnit orgUnit) {
		if (configuration.getTitles().isEnabled() && position.getTitle() != null) {

			// quick sanity check
			if (position.getTitle() == null) {
				return;
			}

			// find existing
			List<String> existingTitleUuids = orgUnitService.getTitles(orgUnit)
					.stream()
					.map(t -> t.getUuid())
					.collect(Collectors.toList());
			existingTitleUuids.addAll(orgUnit.getTitles().stream().map(t -> t.getUuid()).collect(Collectors.toList()));

			// if not present, we got a new title
			if (!existingTitleUuids.contains(position.getTitle().getUuid())) {
				boolean found = false;

				for (OrgUnitWithTitlesDTO dto : events.get().getOrgUnitsWithNewTitles()) {
					if (dto.getOrgUnit().getUuid().equals(orgUnit.getUuid())) {
						dto.getNewTitles().add(position.getTitle());
						found = true;
						break;
					}
				}
				
				if (!found) {
					OrgUnitWithTitlesDTO dto = new OrgUnitWithTitlesDTO();
					dto.setOrgUnit(orgUnit);
					dto.getNewTitles().add(position.getTitle());
					
					events.get().getOrgUnitsWithNewTitles().add(dto);
				}
			}
		}
	}
	
	private void checkOrgUnitForNewEmptyTitles(OrgUnit existingOrgUnit, OrgUnit newOrgUnit) {
		if (configuration.getTitles().isEnabled()) {

			// find existing
			List<String> existingTitleUuids = orgUnitService.getTitles(existingOrgUnit)
					.stream()
					.map(t -> t.getUuid())
					.collect(Collectors.toList());
			existingTitleUuids.addAll(existingOrgUnit.getTitles().stream().map(t -> t.getUuid()).collect(Collectors.toList()));

			for (Title newOrgUnitTitle : newOrgUnit.getTitles()) {
				// if not present, we got a new title
				if (!existingTitleUuids.contains(newOrgUnitTitle.getUuid())) {
					boolean found = false;

					for (OrgUnitWithTitlesDTO dto : events.get().getOrgUnitsWithNewTitles()) {
						if (dto.getOrgUnit().getUuid().equals(existingOrgUnit.getUuid())) {
							dto.getNewTitles().add(newOrgUnitTitle);
							found = true;
							break;
						}
					}
					
					if (!found) {
						OrgUnitWithTitlesDTO dto = new OrgUnitWithTitlesDTO();
						dto.setOrgUnit(existingOrgUnit);
						dto.getNewTitles().add(newOrgUnitTitle);
						
						events.get().getOrgUnitsWithNewTitles().add(dto);
					}
				}
			}
		}
	}
	
	private void addNewEmptyTitlesToEvent(OrgUnit orgUnit, Title title) {
		boolean found = false;
		for (OrgUnitWithTitlesDTO dto : events.get().getOrgUnitsWithNewTitles()) {
			if (dto.getOrgUnit().getUuid().equals(orgUnit.getUuid())) {
				dto.getNewTitles().add(title);
				found = true;
				break;
			}
		}
		
		if (!found) {
			OrgUnitWithTitlesDTO dto = new OrgUnitWithTitlesDTO();
			dto.setOrgUnit(orgUnit);
			dto.getNewTitles().add(title);
			
			events.get().getOrgUnitsWithNewTitles().add(dto);
		}
	}
	
	
	private void identifyUserChanges(List<User> newUsers, List<User> existingUsers, List<User> toBeCreated, List<User> toBeUpdated, List<User> toBeDeleted, Domain domain, boolean deltaSync) {
		// find those that needs to be created or updated
		for (User newUser : newUsers) {
			boolean found = false;

			for (User existingUser : existingUsers) {
				if (hasSameKey(existingUser, newUser)) {
					found = true;
					
					// if the existing user is deleted AND the extUuid has changed, we delete from our DB, and perform
					// a full create of the new user (to reflect that the source-system did a physical delete/create)
					if (existingUser.isDeleted() && !Objects.equals(existingUser.getExtUuid(), newUser.getExtUuid())) {
						userService.delete(existingUser);
						found = false;
					}
					else {
						if (existingUser.isDeleted()) {
							toBeUpdated.add(newUser);
						}
						else if (!Objects.equals(existingUser.getExtUuid(), newUser.getExtUuid())) {
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
						else if (!Objects.equals(existingUser.getNemloginUuid(), newUser.getNemloginUuid())) {
							toBeUpdated.add(newUser);
						}
						else if (!configuration.getIntegrations().getKle().isUiEnabled() && hasKleChanges(newUser, existingUser)) {
							toBeUpdated.add(newUser);
						}
						else if (includePositions(domain) && differentPositions(newUser, existingUser)) {
							toBeUpdated.add(newUser);
						}
						else if (existingUser.isDisabled() != newUser.isDisabled()) {
							toBeUpdated.add(newUser);
						}
					}

					break;
				}
			}
			
			if (!found) {
				User userToCreate = new User();
				userToCreate.setDeleted(false);
				userToCreate.setEmail(newUser.getEmail());
				userToCreate.setNemloginUuid(newUser.getNemloginUuid());
				userToCreate.setExtUuid(newUser.getExtUuid());
				userToCreate.setKles(newUser.getKles());
				userToCreate.setName(newUser.getName());
				userToCreate.setPhone(newUser.getPhone());
				userToCreate.setUserId(newUser.getUserId());
				userToCreate.setUuid(newUser.getUuid());
				userToCreate.setRoleGroupAssignments(new ArrayList<>());
				userToCreate.setUserRoleAssignments(new ArrayList<>());
				userToCreate.setPositions(new ArrayList<>());
				userToCreate.setDisabled(newUser.isDisabled());
				userToCreate.setCpr(newUser.getCpr());
				userToCreate.setDomain(domain);

				// we need to tell Hibernate that we intend to persist this newly created object, otherwise it does not
				// exist in the Hibernate context, and intercepted events (like the addPosition below), which has side-effects
				// like persisting other objects that references users (and then it needs to exists i the hibernate context first)
				userService.save(userToCreate);

				if (includePositions(domain)) {
					for (Position p : newUser.getPositions()) {
						addPosition(userToCreate, p);
					}
				}

				toBeCreated.add(userToCreate);
			}
		}
		
		if (!deltaSync) {
			// find those that should be set to deleted
			for (User existingUser : existingUsers) {
				boolean found = false;

				// ignore those that are already deleted
				if (existingUser.isDeleted()) {
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
	}

	private boolean includePositions(Domain domain) {
		return DomainService.isPrimaryDomain(domain) || configuration.getOrganisation().isIncludePositionsFromSecondaryDomains();
	}

	private static boolean hasSameKey(ManagerDTO managerDto, User manager) {
		if (managerDto.getUserId().equalsIgnoreCase(manager.getUserId()) && managerDto.getUuid().equals(manager.getExtUuid())) {
			return true;
		}
		
		return false;
	}

	private static boolean hasSameKey(User user1, User user2) {
		if (user1.getUserId().equalsIgnoreCase(user2.getUserId())) {
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
					else if (!configuration.getIntegrations().getKle().isUiEnabled() && hasKleChanges(newOrgUnit, existingOrgUnit)) {
						toBeUpdated.add(newOrgUnit);
					}
					else if (configuration.getOrganisation().isGetLevelsFromApi() && differentOrgUnitLevels(existingOrgUnit, newOrgUnit)) {
						toBeUpdated.add(newOrgUnit);
					} 
					else if (hasTitleChanges(existingOrgUnit, newOrgUnit)) {
						toBeUpdated.add(newOrgUnit);
					}
					
					break;
				}
			}
			
			if (!found) {
				// because of cascade-saving of children (we really need to get rid of that),
				// we need to make sure that any EXISTING ous that are children of the NEW
				// ou, are attached correctly
				if (newOrgUnit.getChildren() != null && newOrgUnit.getChildren().size() > 0) {
					List<OrgUnit> newChildren = new ArrayList<OrgUnit>();

					for (Iterator<OrgUnit> iterator = newOrgUnit.getChildren().iterator(); iterator.hasNext();) {
						OrgUnit child = iterator.next();

						for (OrgUnit existingOrgUnit : existingOrgUnits) {
							if (Objects.equals(child.getUuid(), existingOrgUnit.getUuid())) {
								iterator.remove();
								newChildren.add(existingOrgUnit);
								break;
							}
						}
					}
					
					newOrgUnit.getChildren().addAll(newChildren);
				}

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

	private boolean hasTitleChanges(OrgUnit existingOrgUnit, OrgUnit newOrgUnit) {
		for (Title existingOrgUnitTitles : existingOrgUnit.getTitles()) {
			boolean titleFound = false;

			for (Title newOrgUnitTitle : newOrgUnit.getTitles()) {
				if (newOrgUnitTitle.getUuid().equals(existingOrgUnitTitles.getUuid())) {
					titleFound = true;
					break;
				}
			}
			
			if (!titleFound) {
				return true;
			}
		}
		
		for (Title newOrgUnitTitle : newOrgUnit.getTitles()) {
			boolean titleFound = false;

			for (Title existingOrgUnitTitle : existingOrgUnit.getTitles()) {
				if (newOrgUnitTitle.getUuid().equals(existingOrgUnitTitle.getUuid())) {
					titleFound = true;
					break;
				}
			}
			
			if (!titleFound) {
				return true;
			}
		}
		
		return false;
	}

	private boolean hasKleChanges(OrgUnit newOrgUnit, OrgUnit existingOrgUnit) {
		if (newOrgUnit.isInheritKle() != existingOrgUnit.isInheritKle()) {
			return true;
		}
		
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
				p.isDoNotInherit() == position.isDoNotInherit() &&
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
		OrgUnitWithNewAndOldParentDTO dto = new OrgUnitWithNewAndOldParentDTO();
		dto.setOrgUnit(ou);
		dto.setOldParent(ou.getParent());
		dto.setNewParent(parent);
		
		events.get().getOrgUnitsWithNewParent().add(dto);
		
		ou.setParent(parent);
	}

	private void setOUManager(OrgUnit ou, User manager) {
		ou.setManager(manager);
	}

	private void addPosition(User user, Position position) {
		userService.addPosition(user, position);
	}

	private void processEvents() {
		Set<OrgUnit> newOrgUnits = events.get().getNewOrgUnits();
		Set<OrgUnitWithNewAndOldParentDTO> parentChangedOrgUnits = events.get().getOrgUnitsWithNewParent();
		Set<OrgUnitWithTitlesDTO> orgUnitsWithNewTitles = events.get().getOrgUnitsWithNewTitles();
		Set<UserMovedPositions> UsersWhoMovedPositions = events.get().getUsersMovedPostions();
		Set<UserDeletedEvent> deletedUsers = events.get().getDeletedUsers();

		handleNewOrgUnits(newOrgUnits);

		handleParentChange(parentChangedOrgUnits);

		handleNewTitles(orgUnitsWithNewTitles);
		
		handleUserPositionChange(UsersWhoMovedPositions);

		handleUserDeleteEvents(deletedUsers);
	}

	private void handleUserDeleteEvents(Set<UserDeletedEvent> deletedUsers) {
		if (deletedUsers.size() > 0) {
			EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.USER_WITH_MANUAL_ITSYSTEM_DELETED);
			
			if (template.isEnabled()) {
				for (UserDeletedEvent deletedEvent : deletedUsers) {
					String emailAddressesString = deletedEvent.getEmail();
					if (!StringUtils.hasLength(emailAddressesString)) {
						log.debug("No email address configured for " + deletedEvent.getItSystemName() + ". Will not send emails about deleted users.");
						continue;
					}
					String[] emailAddresses = emailAddressesString.split(";");

					User user = deletedEvent.getUser();
					
					String title = template.getTitle();
					title = title.replace(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER.getPlaceholder(), deletedEvent.getItSystemName());
					title = title.replace(EmailTemplatePlaceholder.USER_PLACEHOLDER.getPlaceholder(), user.getName() + " (" + user.getUserId() + ")");
					
					String message = template.getMessage();
					message = message.replace(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER.getPlaceholder(), deletedEvent.getItSystemName());
					message = message.replace(EmailTemplatePlaceholder.USER_PLACEHOLDER.getPlaceholder(), user.getName() + " (" + user.getUserId() + ")");

					for (String email: emailAddresses) {
						emailQueueService.queueEmail(email, title, message, template, null);
					}
				}
			}
			else {
				log.info("Email template with type " + template.getTemplateType() + " is disabled. Emails were not sent.");
			}
		}
	}

	private void handleUserPositionChange(Set<UserMovedPositions> UsersWhoMovedPositions) {
		if (settingsService.isNotificationTypeEnabled(NotificationType.USER_MOVED_POSITIONS)) {
			for (UserMovedPositions event : UsersWhoMovedPositions) {
				String oneOrMoreNew = event.getNewPositions().size() == 1 ? "Ny stilling:" : "Nye stillinger:";
				String oneOrMoreOld = event.getOldPositions().size() == 1 ? "Fratrådt stilling:" : "Fratrådte stillinger:";
				String message = "Brugeren " + event.getUser().getName() + " (" + event.getUser().getUserId() + ") har skiftet afdeling.\n" + oneOrMoreNew + "\n";
				for (MovedPostion newPosition : event.getNewPositions()) {
					message += newPosition.getPositionName() + " i " + newPosition.getOrgUnitName() + "\n";
				}
				message += "\n" + oneOrMoreOld + "\n";
				for (MovedPostion oldPosition : event.getOldPositions()) {
					message += oldPosition.getPositionName() + " i " + oldPosition.getOrgUnitName() + "\n";
				}
				
				Notification moveNotification = new Notification();
				moveNotification.setActive(true);
				moveNotification.setCreated(new Date());
				moveNotification.setMessage(message);
				moveNotification.setNotificationType(NotificationType.USER_MOVED_POSITIONS);
				moveNotification.setAffectedEntityName(event.getUser().getName());
				moveNotification.setAffectedEntityType(NotificationEntityType.USERS);
				moveNotification.setAffectedEntityUuid(event.getUser().getUuid());

				notificationService.save(moveNotification);
			}
		}
	}

	private void handleNewTitles(Set<OrgUnitWithTitlesDTO> orgUnitsWithNewTitles) {
		if (configuration.getTitles().isEnabled() && settingsService.isNotificationTypeEnabled(NotificationType.NEW_TITLE_IN_ORG_UNIT)) {
			for (OrgUnitWithTitlesDTO dto : orgUnitsWithNewTitles) {
				long userRolesWithTitlesCount = dto.getOrgUnit().getUserRoleAssignments().stream().filter(u -> !u.getTitles().isEmpty()).count();
				long roleGroupsWithTitlesCount = dto.getOrgUnit().getRoleGroupAssignments().stream().filter(r -> !r.getTitles().isEmpty()).count();
				
				if (userRolesWithTitlesCount == 0 && roleGroupsWithTitlesCount == 0) {
					continue;
				}
				
				String message = "Enheden '" + dto.getOrgUnit().getName() + "' har en eller flere roller tildelt på stillinger og har fået en eller flere nye stillinger:\n";
				for (Title title : dto.getNewTitles()) {
					message += title.getName() + "\n";
				}
				
				Notification moveNotification = new Notification();
				moveNotification.setActive(true);
				moveNotification.setCreated(new Date());
				moveNotification.setMessage(message);
				moveNotification.setNotificationType(NotificationType.NEW_TITLE_IN_ORG_UNIT);
				moveNotification.setAffectedEntityName(dto.getOrgUnit().getName());
				moveNotification.setAffectedEntityType(NotificationEntityType.OUS);
				moveNotification.setAffectedEntityUuid(dto.getOrgUnit().getUuid());

				notificationService.save(moveNotification);
			}
		}
	}

	private void handleParentChange(Set<OrgUnitWithNewAndOldParentDTO> parentChangedOrgUnits) {
		if (settingsService.isNotificationTypeEnabled(NotificationType.ORG_UNIT_NEW_PARENT)) {
			for (OrgUnitWithNewAndOldParentDTO dto : parentChangedOrgUnits) {
				UserRoleAndRoleGroupListWrapper newWrapper = new UserRoleAndRoleGroupListWrapper();
				newWrapper.setRoleGroups(new ArrayList<RoleGroup>());
				newWrapper.setUserRoles(new ArrayList<UserRole>());

				if (dto.getNewParent() != null) {
					newWrapper = inheritedRoles(dto.getNewParent(), newWrapper);
				}
				
				UserRoleAndRoleGroupListWrapper oldWrapper = new UserRoleAndRoleGroupListWrapper();
				oldWrapper.setRoleGroups(new ArrayList<RoleGroup>());
				oldWrapper.setUserRoles(new ArrayList<UserRole>());

				if (dto.getOldParent() != null) {
					oldWrapper = inheritedRoles(dto.getOldParent(), oldWrapper);
				}
				
				if (!newWrapper.getRoleGroups().isEmpty() || !newWrapper.getUserRoles().isEmpty() || !oldWrapper.getRoleGroups().isEmpty() || !oldWrapper.getUserRoles().isEmpty()) {
					List<Long> roleGroupsBefore = oldWrapper.getRoleGroups().stream().map(r -> r.getId()).collect(Collectors.toList());
					List<Long> userRolesBefore = oldWrapper.getUserRoles().stream().map(u -> u.getId()).collect(Collectors.toList());
					List<Long> roleGroupsAfter = newWrapper.getRoleGroups().stream().map(r -> r.getId()).collect(Collectors.toList());
					List<Long> userRolesAfter = newWrapper.getUserRoles().stream().map(u -> u.getId()).collect(Collectors.toList());
					
					List<RoleGroup> filteredNewRoleGroups = newWrapper.getRoleGroups().stream().filter(r -> !roleGroupsBefore.contains(r.getId())).collect(Collectors.toList());
					List<UserRole> filteredNewUserRoles = newWrapper.getUserRoles().stream().filter(u -> !userRolesBefore.contains(u.getId())).collect(Collectors.toList());
					List<RoleGroup> filteredOldRoleGroups = oldWrapper.getRoleGroups().stream().filter(r -> !roleGroupsAfter.contains(r.getId())).collect(Collectors.toList());
					List<UserRole> filteredOldNewUserRoles = oldWrapper.getUserRoles().stream().filter(u -> !userRolesAfter.contains(u.getId())).collect(Collectors.toList());
					
					String message = "Enheden '" + dto.getOrgUnit().getName() + "' har skiftet overliggende enhed og som konsekvens deraf har dens rettigheder ændret sig.\n";
					
					if (!filteredNewRoleGroups.isEmpty() || !filteredNewUserRoles.isEmpty()) {
						message += "Den har nedarvet følgende rettigheder: \n";
						
						for (RoleGroup roleGroup : filteredNewRoleGroups) {
							message += "Rollebuketten " +  roleGroup.getName() + "\n";
						}
						
						for (UserRole userRole : filteredNewUserRoles) {
							message += "Jobfunktionsrollen " +  userRole.getName() + " fra it-systemet " + userRole.getItSystem().getName() + "\n";
						}
					}
					
					if (!filteredOldRoleGroups.isEmpty() || !filteredOldNewUserRoles.isEmpty()) {
						message += "\nDen har mistet følgende nedarvede rettigheder: \n";
						for (RoleGroup roleGroup : filteredOldRoleGroups) {
							message += "Rollebuketten " +  roleGroup.getName() + "\n";
						}
						
						for (UserRole userRole : filteredOldNewUserRoles) {
							message += "Jobfunktionsrollen " +  userRole.getName() + " fra it-systemet " + userRole.getItSystem().getName() + "\n";
						}
					}
					
					Notification moveNotification = new Notification();
					moveNotification.setActive(true);
					moveNotification.setCreated(new Date());
					moveNotification.setMessage(message);
					moveNotification.setNotificationType(NotificationType.ORG_UNIT_NEW_PARENT);
					moveNotification.setAffectedEntityName(dto.getOrgUnit().getName());
					moveNotification.setAffectedEntityType(NotificationEntityType.OUS);
					moveNotification.setAffectedEntityUuid(dto.getOrgUnit().getUuid());
	
					notificationService.save(moveNotification);
				}
			}
		}
	}

	private void handleNewOrgUnits(Set<OrgUnit> newOrgUnits) {
		for (OrgUnit orgUnit : newOrgUnits) {
			if (settingsService.isNotificationTypeEnabled(NotificationType.NEW_ORG_UNIT)) {
				Notification notification = new Notification();
				notification.setActive(true);
				notification.setCreated(new Date());
				notification.setMessage("Der er oprettet en ny enhed med navnet '" + orgUnit.getName() + "'");
				notification.setNotificationType(NotificationType.NEW_ORG_UNIT);
				notification.setAffectedEntityName(orgUnit.getName());
				notification.setAffectedEntityType(NotificationEntityType.OUS);
				notification.setAffectedEntityUuid(orgUnit.getUuid());
	
				notificationService.save(notification);
			}

			if (settingsService.isNotificationTypeEnabled(NotificationType.ORG_UNIT_NEW_PARENT)) {
				UserRoleAndRoleGroupListWrapper wrapper = new UserRoleAndRoleGroupListWrapper();
				wrapper.setRoleGroups(new ArrayList<RoleGroup>());
				wrapper.setUserRoles(new ArrayList<UserRole>());

				if (orgUnit.getParent() != null) {
					wrapper = inheritedRoles(orgUnit.getParent(), wrapper);
				}
				
				if (!wrapper.getRoleGroups().isEmpty() || !wrapper.getUserRoles().isEmpty()) {
					String message = "Enheden '" + orgUnit.getName() + "' har skiftet overliggende enhed og har som konsekvens deraf nedarvet følgende rettigheder: \n";

					for (RoleGroup roleGroup : wrapper.getRoleGroups()) {
						message += "Rollebuketten " +  roleGroup.getName() + "\n";
					}
					
					for (UserRole userRole : wrapper.getUserRoles()) {
						message += "Jobfunktionsrollen " +  userRole.getName() + " fra it-systemet" + userRole.getItSystem().getName() + "\n";
					}
					
					Notification moveNotification = new Notification();
					moveNotification.setActive(true);
					moveNotification.setCreated(new Date());
					moveNotification.setMessage(message);
					moveNotification.setNotificationType(NotificationType.ORG_UNIT_NEW_PARENT);
					moveNotification.setAffectedEntityName(orgUnit.getName());
					moveNotification.setAffectedEntityType(NotificationEntityType.OUS);
					moveNotification.setAffectedEntityUuid(orgUnit.getUuid());
	
					notificationService.save(moveNotification);
				}
			}
		}
	}
	
	private UserRoleAndRoleGroupListWrapper inheritedRoles(OrgUnit parent, UserRoleAndRoleGroupListWrapper wrapper) {
		List<UserRole> userRoles = parent.getUserRoleAssignments().stream().filter(u -> u.isInherit()).map(u -> u.getUserRole()).collect(Collectors.toList());
		wrapper.getUserRoles().addAll(userRoles);

		List<RoleGroup> roleGroups = parent.getRoleGroupAssignments().stream().filter(r -> r.isInherit()).map(r -> r.getRoleGroup()).collect(Collectors.toList());
		wrapper.getRoleGroups().addAll(roleGroups);
		
		if (parent.getParent() != null) {
			return inheritedRoles(parent.getParent(), wrapper);
		}
		else {
			return wrapper;
		}
	}
	
	//// validation logic ////
	
	private ValidationResult validateOrganisation(OrganisationDTO organisation) {
		ValidationResult userValidationResult = validateUsers(organisation.getUsers(), organisation.getOrgUnits());
		if (userValidationResult != null) {
			return userValidationResult;
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
							case LEVEL_5:
								if (!parent.getLevel().equals(OrgUnitLevel.LEVEL_1) &&
										!parent.getLevel().equals(OrgUnitLevel.LEVEL_2) &&
										!parent.getLevel().equals(OrgUnitLevel.LEVEL_3) &&
										!parent.getLevel().equals(OrgUnitLevel.LEVEL_4)) {
									return new ValidationResult(false, "Orgunit has higher or same level as parent: " + orgUnit.getUuid());
								}
								break;
							case LEVEL_6:
								if (!parent.getLevel().equals(OrgUnitLevel.LEVEL_1) &&
										!parent.getLevel().equals(OrgUnitLevel.LEVEL_2) &&
										!parent.getLevel().equals(OrgUnitLevel.LEVEL_3) &&
										!parent.getLevel().equals(OrgUnitLevel.LEVEL_4) &&
										!parent.getLevel().equals(OrgUnitLevel.LEVEL_5)) {
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

	private ValidationResult validateUsers(List<UserDTO> users, List<OrgUnitDTO> orgUnits) {
		for (UserDTO user : users) {
			if (!StringUtils.hasLength(user.getUserId())) {
				return new ValidationResult(false, "Users with null/empty userId not allowed");
			}

			if (!StringUtils.hasLength(user.getExtUuid())) {
				return new ValidationResult(false, "Users with null UUID not allowed: " + user.getUserId());
			}

			if (!StringUtils.hasLength(user.getName())) {
				return new ValidationResult(false, "User with null/empty name not allowed: " + user.getUserId());
			}

			if (user.getPositions() != null) {
				for (PositionDTO position : user.getPositions()) {
					if (!StringUtils.hasLength(position.getName())) {
						return new ValidationResult(false, "Users with null/empty titles are not allowed: " + user.getUserId());
					}
					
					if (!StringUtils.hasLength(position.getOrgUnitUuid())) {
						return new ValidationResult(false, "Users with a position with null/empty orgUnit is not allowed: " + user.getUserId());
					}

					// validate against list of OrgUnits if supplied
					if (orgUnits != null) {
						boolean found = false;
						for (OrgUnitDTO orgUnit : orgUnits) {
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
			}
			
			try {
				UUID.fromString(user.getExtUuid());
			}
			catch (Exception ex) {
				return new ValidationResult(false, "Users must have a valid UUID: " + user.getUserId() + " / " + user.getExtUuid());
			}
		}

		return null;
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
