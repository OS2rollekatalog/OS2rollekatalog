package dk.digitalidentity.rc.service.dmp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.DmpQueueDao;
import dk.digitalidentity.rc.dao.model.DmpQueue;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RoleType;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.PositionService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.dmp.model.DMPApplication;
import dk.digitalidentity.rc.service.dmp.model.DMPRole;
import dk.digitalidentity.rc.service.dmp.model.DMPSetRoleAssignment;
import dk.digitalidentity.rc.service.dmp.model.DMPSetRoleAssignmentsRequest;
import dk.digitalidentity.rc.service.dmp.model.DMPUser;
import dk.digitalidentity.rc.service.model.UserWithRole;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DMPService {
	public static final String DMP_IT_SYSTEM_IDENTIFIER = "DMPSystemID";

	@Autowired
	private DMPStub stub;
	
	@Autowired
	private ItSystemService itSystemService;
	
	@Autowired
	private SystemRoleService systemRoleService;
	
	@Autowired
	private UserService userService;

	@Autowired
	private DmpQueueDao dmpQueueDao;
	
	@Autowired
	private UserRoleService userRoleService;
	
	@Autowired
	private PositionService positionService;
	
	// perform a delta sync on all userRoles
	@Transactional
	public void deltaSyncRoles() {
		List<DmpQueue> fullQueue = dmpQueueDao.findAll();
		
		if (fullQueue.isEmpty()) {
			return;
		}

		// fail or succeed, we wipe from the queue.
		// in case of failure - our nightjob will do cleanup, and in case of success we might
		// as well clear the queue now, so new changes can be added to the queue
		dmpQueueDao.deleteAll(fullQueue);

		List<User> users = fullQueue.stream().map(q -> q.getUser()).collect(Collectors.toList());
		syncRoles(users, false);
	}
	
	// perform a full sync on all userRoles
	@Transactional
	public void fullSyncRoles() {
		List<User> users = userService.getAll();
		
		syncRoles(users, true);
	}

	// compares all users in OS2rollekatalog with DMP roles against users in DMP, and deletes those that does not belong anymore
	@Transactional
	public void deleteUsers() {
		List<DMPUser> dmpUsers = stub.getUsers();
		Set<String> usersWithDmpRole = getAllUsersInItSystemWithADmpRole();

		for (DMPUser dmpUser : dmpUsers) {
			// ignore users without an externalUserId
			if (dmpUser.getExternalUserIds() == null || dmpUser.getExternalUserIds().isEmpty()) {
				continue;
			}

			// *sigh* there should only be on externalUserId, this is weird, so we just log it for debugging purposes
			if (dmpUser.getExternalUserIds().size() > 1) {
				log.warn("dmpUser " + dmpUser.getId() + " has > 1 externalUserId: " + String.join(",", dmpUser.getExternalUserIds()));
			}

			// cleanup all of them, just in case there is more than one
			for (String userId : dmpUser.getExternalUserIds()) {
				if (!usersWithDmpRole.contains(userId)) {
					stub.deleteUser(userId);
				}
			}
		}
	}

	// reads all roles from DMP, compares them with the local system, and performs necessary updates
	@Transactional(rollbackFor = Exception.class)
	public void synchronizeDMPRoles() {
		Map<String, List<DMPRole>> dmpRoleMap = new HashMap<>();
		
		// read from DMP
		List<DMPApplication> applications = stub.getApplications();
		for (DMPApplication application : applications) {
			List<DMPRole> dmpRoles = stub.getRolesForApplication(application);
			
			dmpRoleMap.put(application.getId(), dmpRoles);
		}
		
		// ensure itsystem exists
		ItSystem itSystem = itSystemService.getFirstByIdentifier(DMP_IT_SYSTEM_IDENTIFIER);
		if (itSystem == null) {
			itSystem = new ItSystem();
			itSystem.setIdentifier(DMP_IT_SYSTEM_IDENTIFIER);
			itSystem.setName("Danmarks Miljøportal");
			itSystem.setSystemType(ItSystemType.SAML);
			itSystem = itSystemService.save(itSystem);
		}
		
		// read existing roles
		List<SystemRole> systemRoles = systemRoleService.getByItSystem(itSystem);
		
		// to add/update
		List<SystemRole> toSave = new ArrayList<>();
		for (String dmpApplicationId : dmpRoleMap.keySet()) {
			for (DMPRole dmpRole : dmpRoleMap.get(dmpApplicationId)) {
				String generatedIdentifier = dmpApplicationId + ":" + dmpRole.getId();
				
				boolean found = false;
				for (SystemRole systemRole : systemRoles) {
					if (Objects.equals(systemRole.getIdentifier(), generatedIdentifier)) {
						boolean changes = false;
						
						if (!Objects.equals(dmpRole.getDescription(), systemRole.getDescription())) {
							systemRole.setDescription(dmpRole.getDescription());
							changes = true;
						}
						
						if (!Objects.equals(dmpRole.getName(), systemRole.getName())) {
							systemRole.setName(dmpRole.getName());
							changes = true;
						}

						if (changes) {
							toSave.add(systemRole);
							log.info("Updating " + systemRole.getName() + " / " + systemRole.getId());
						}
						
						found = true;
						break;
					}
				}
				
				if (!found) {
					log.info("Creating " + dmpRole.getName());
					
					SystemRole systemRole = new SystemRole();
					systemRole.setDescription(dmpRole.getDescription());
					systemRole.setIdentifier(generatedIdentifier);
					systemRole.setItSystem(itSystem);
					systemRole.setName(dmpRole.getName());
					systemRole.setRoleType(RoleType.BOTH);
					systemRole.setWeight(1);
					
					toSave.add(systemRole);
				}
			}
		}
		
		if (toSave.size() > 0) {
			systemRoleService.save(toSave);
		}
		
		// to remove
		for (SystemRole systemRole : systemRoles) {
			boolean found = false;
			
			for (String dmpApplicationId : dmpRoleMap.keySet()) {
				for (DMPRole dmpRole : dmpRoleMap.get(dmpApplicationId)) {
					String generatedIdentifier = dmpApplicationId + ":" + dmpRole.getId();
					
					if (Objects.equals(systemRole.getIdentifier(), generatedIdentifier)) {
						found = true;
						break;
					}
				}
				
				// break the outer loop as well
				if (found) {
					break;
				}
			}

			if (!found) {
				log.info("Deleting " + systemRole.getName() + " / " + systemRole.getId());
				systemRoleService.delete(systemRole);
			}
		}
	}
	
	public void queueUser(User user) {
		DmpQueue queue = new DmpQueue();
		queue.setTts(LocalDateTime.now());
		queue.setUser(user);
		queue.setUserUuid(user.getUuid());

		// because the user_uuid is the primary key, this will either perform an update or a create :)
		dmpQueueDao.save(queue);
	}

	public void queueOrgUnit(OrgUnit ou, boolean inherit) {
		List<User> users = new ArrayList<>();
		
		findUsersByOu(ou, inherit, users);
		
		for (User user : users) {
			queueUser(user);
		}
	}

	public void queueUserRole(UserRole userRole) {
		List<UserWithRole> usersWithRole = userService.getUsersWithUserRole(userRole, true);
		
		for (UserWithRole userWithUserRole : usersWithRole) {
			queueUser(userWithUserRole.getUser());
		}
	}

	private void findUsersByOu(OrgUnit ou, boolean recursive, List<User> users) {
		List<Position> positions = positionService.findByOrgUnit(ou);
		List<User> positionUsers = positions.stream().map(p -> p.getUser()).collect(Collectors.toList());
		
		// add any we have not seen before
		for (User positionUser : positionUsers) {
			if (!users.stream().anyMatch(u -> Objects.equals(u.getUuid(), positionUser.getUuid()))) {
				users.add(positionUser);
			}
		}
		
		if (recursive) {
			for (OrgUnit child : ou.getChildren()) {
				findUsersByOu(child, recursive, users);
			}
		}
	}

	private void syncRoles(List<User> users, boolean full) {
		ItSystem itSystem = itSystemService.getFirstByIdentifier(DMP_IT_SYSTEM_IDENTIFIER);
		if (itSystem == null) {
			log.warn("Not performing DMP synchronization - no DMP it-system available");
			return;
		}
		
		Map<String, Set<String>> assignedDmpRoles = new HashMap<>();
		List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);
		for (UserRole userRole : userRoles) {
			// extra systemRoles
			Set<String> systemRoles = new HashSet<>();
			for (SystemRoleAssignment systemRoleAssignment : userRole.getSystemRoleAssignments()) {
				String systemRoleIdentifier = systemRoleAssignment.getSystemRole().getIdentifier();
				String[] tokens = systemRoleIdentifier.split(":");

				// identifier is constructed as appId:roleId
				if (tokens.length > 1) {
					systemRoleIdentifier = tokens [1];
				}
				
				systemRoles.add(systemRoleIdentifier);
			}

			// skip empty userRoles
			if (systemRoles.isEmpty()) {
				continue;
			}

			// find all assignments of this userRole
			List<UserWithRole> usersWithUserRole = userService.getUsersWithUserRole(userRole, true);

			// aggregate all assigned systemRoles in the assignment map
			for (UserWithRole userWithUserRole : usersWithUserRole) {
				String userId = userWithUserRole.getUser().getUserId().toLowerCase();

				if (!assignedDmpRoles.containsKey(userId)) {
					assignedDmpRoles.put(userId, systemRoles);
				}
				else {
					assignedDmpRoles.get(userId).addAll(systemRoles);
				}
			}
		}

		// if we have 0 local assignments, we will not perform any synchronization
		if (assignedDmpRoles.size() == 0) {
			return;
		}
		
		for (User user : users) {
			String userId = user.getUserId().toLowerCase();
			
			// if we have 0 assignments on this user, the nightly job will perform cleanup
			Set<String> assignments = assignedDmpRoles.get(userId);
			if (assignments == null || assignments.size() == 0) {
				continue;
			}

			DMPSetRoleAssignmentsRequest setRoleRequest = new DMPSetRoleAssignmentsRequest();
			setRoleRequest.setUserRoleAssignments(new ArrayList<>());
			for (String assignment : assignments) {
				DMPSetRoleAssignment setRoleAssignment = new DMPSetRoleAssignment();
				setRoleAssignment.setRoleId(assignment);

				setRoleRequest.getUserRoleAssignments().add(setRoleAssignment);
			}

			// setRolesForUser will also create the user if the user does not exist
			stub.setRolesForUser(user, setRoleRequest);
		}
	}
	
	private Set<String> getAllUsersInItSystemWithADmpRole() {
		ItSystem itSystem = itSystemService.getFirstByIdentifier(DMP_IT_SYSTEM_IDENTIFIER);
		if (itSystem == null) {
			return new HashSet<>();
		}

		Set<String> users = new HashSet<>();
		List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);

		for (UserRole userRole : userRoles) {
			List<UserWithRole> usersWithRole = userService.getUsersWithUserRole(userRole, true);

			for (UserWithRole userWithRole : usersWithRole) {
				if (userWithRole.getUser().isDeleted() || userWithRole.getUser().isDisabled()) {
					continue;
				}

				users.add(userWithRole.getUser().getUserId().toLowerCase());
			}
		}

		return users;
	}
}
