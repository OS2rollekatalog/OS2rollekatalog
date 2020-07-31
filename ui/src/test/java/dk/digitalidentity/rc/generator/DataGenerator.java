package dk.digitalidentity.rc.generator;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.RoleGroupDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.PositionRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.PositionUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.service.SystemRoleService;

@Component
public class DataGenerator {
	private static final SecureRandom random = new SecureRandom();

	@Autowired
	private OrgUnitDao orgUnitDao;
	
	@Autowired
	private UserDao userDao;
	
	@Autowired
	private ItSystemDao itSystemDao;
	
	@Autowired
	private SystemRoleService systemRoleService;
	
	@Autowired
	private UserRoleDao userRoleDao;
	
	@Autowired
	private RoleGroupDao roleGroupDao;

	@Transactional
	public void run() {
		List<ItSystem> itSystems = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			ItSystem itSystem = new ItSystem();
			itSystem.setIdentifier("itsystem" + i);
			itSystem.setName("ItSystem " + i);

			itSystems.add(itSystem);
		}
		itSystemDao.saveAll(itSystems);
		
		List<SystemRole> systemRoles = new ArrayList<>();
		for (ItSystem itSystem : itSystems) {
			for (int j = 0; j < 10; j++) {
				SystemRole systemRole = new SystemRole();
				systemRole.setItSystem(itSystem);
				systemRole.setIdentifier("http://" + itSystem.getIdentifier() + "/systemrole/systemrole/" + j);
				systemRole.setName("SystemRole for " + itSystem.getName() + " #" + j);
				systemRoles.add(systemRole);
			}
		}

		systemRoleService.save(systemRoles);
		
		List<UserRole> userRoles = new ArrayList<>();
		int k = 0;
		for (ItSystem itSystem : itSystems) {
			for (SystemRole systemRole : systemRoles) {
				if (systemRole.getItSystem().getName().equals(itSystem.getName())) {
					UserRole userRole = new UserRole();
					userRole.setIdentifier("http://kommune.dk/roles/jobrole/" + itSystem.getIdentifier() + "_" + k++);
					userRole.setName(userRole.getIdentifier());
					userRole.setItSystem(itSystem);
					List<SystemRoleAssignment> systemRoleAssignments = new ArrayList<>();
					SystemRoleAssignment assignment = new SystemRoleAssignment();
					assignment.setSystemRole(systemRole);
					assignment.setUserRole(userRole);
					systemRoleAssignments.add(assignment);
					userRole.setSystemRoleAssignments(systemRoleAssignments);
					
					userRoles.add(userRole);
				}
			}
		}
		userRoleDao.saveAll(userRoles);
		
		List<RoleGroup> roleGroups = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			RoleGroup roleGroup = new RoleGroup();
			roleGroup.setUserRoleAssignments(new ArrayList<>());
			
			List<UserRole> roles = new ArrayList<>();
			for (int j = 0; j < 4; j++) { 
				int pick = random.nextInt(userRoles.size());
				roles.add(userRoles.get(pick));
			}
			
			roleGroup.setName("RoleGroup " + i);
			
			for (UserRole role : roles) {
				RoleGroupUserRoleAssignment assignment = new RoleGroupUserRoleAssignment();
				assignment.setUserRole(role);
				assignment.setRoleGroup(roleGroup);
				assignment.setAssignedByName("Systembruger");
				assignment.setAssignedByUserId("system");
				assignment.setAssignedTimestamp(new Date());
				roleGroup.getUserRoleAssignments().add(assignment);
			}
			
			roleGroups.add(roleGroup);
		}
		roleGroupDao.saveAll(roleGroups);
		
		OrgUnit root = new OrgUnit();
		root.setActive(true);
		root.setName("Root");
		root.setParent(null);
		root.setUuid(UUID.randomUUID().toString());
		// root.setKles(null);
		
		List<OrgUnit> children = new ArrayList<>();
		for (int i = 0; i < 1000; i++) {
			OrgUnit orgUnit = new OrgUnit();
			orgUnit.setParent(root);
			orgUnit.setActive(true);
			orgUnit.setName("Unit " + i);
			orgUnit.setUuid(UUID.randomUUID().toString());
			// root.setKles(null);

			List<OrgUnitRoleGroupAssignment> rgs = new ArrayList<>();
			for (int j = 0; j < 2; j++) {
				int pick = random.nextInt(roleGroups.size());
				OrgUnitRoleGroupAssignment mapping = new OrgUnitRoleGroupAssignment();
				mapping.setInherit(false);
				mapping.setRoleGroup(roleGroups.get(pick));
				mapping.setAssignedByName("Systembruger");
				mapping.setAssignedByUserId("system");
				mapping.setAssignedTimestamp(new Date());
				mapping.setOrgUnit(orgUnit);

				rgs.add(mapping);
			}
			orgUnit.setRoleGroupAssignments(rgs);
			
			List<OrgUnitUserRoleAssignment> rs = new ArrayList<>();
			for (int j = 0; j < 4; j++) {
				int pick = random.nextInt(userRoles.size());
				OrgUnitUserRoleAssignment mapping = new OrgUnitUserRoleAssignment();
				mapping.setInherit(false);
				mapping.setUserRole(userRoles.get(pick));
				mapping.setAssignedByName("Systembruger");
				mapping.setAssignedByUserId("system");
				mapping.setAssignedTimestamp(new Date());
				mapping.setOrgUnit(orgUnit);
				
				rs.add(mapping);				
			}
			orgUnit.setUserRoleAssignments(rs);

			children.add(orgUnit);
		}
		
		root.setChildren(children);
		
		orgUnitDao.save(root);
		
		for (int i = 0; i < 10000; i++) {
			User user = new User();
			user.setActive(true);
			user.setName("User " + i);
			user.setUserId("user" + i);
			user.setUuid(UUID.randomUUID().toString());
			user.setExtUuid(UUID.randomUUID().toString());

			List<RoleGroup> rgs = new ArrayList<>();
			for (int j = 0; j < 2; j++) {
				int pick = random.nextInt(roleGroups.size());
				rgs.add(roleGroups.get(pick));				
			}
			
			for (RoleGroup roleGroup : rgs) {
				UserRoleGroupAssignment assignment = new UserRoleGroupAssignment();
				assignment.setUser(user);
				assignment.setRoleGroup(roleGroup);
				user.getRoleGroupAssignments().add(assignment);
			}
			
			List<UserRole> rs = new ArrayList<>();
			for (int j = 0; j < 4; j++) {
				int pick = random.nextInt(userRoles.size());
				rs.add(userRoles.get(pick));				
			}
			
			for (UserRole userRole : rs) {
				UserUserRoleAssignment assignment = new UserUserRoleAssignment();
				assignment.setUser(user);
				assignment.setUserRole(userRole);
				assignment.setAssignedByName("Systembruger");
				assignment.setAssignedByUserId("system");
				assignment.setAssignedTimestamp(new Date());
				user.getUserRoleAssignments().add(assignment);
			}

			List<Position> positions = new ArrayList<>();
			for (int j = 0; j < 3; j++) {
				int ou = random.nextInt(1000);
				
				Position position = new Position();
				position.setName("Position " + j);
				position.setOrgUnit(children.get(ou));
				position.setUser(user);

				rgs = new ArrayList<>();
				for (int n = 0; n < 2; n++) {
					int pick = random.nextInt(roleGroups.size());
					rgs.add(roleGroups.get(pick));				
				}

				for (RoleGroup roleGroup : rgs) {
					PositionRoleGroupAssignment assignment = new PositionRoleGroupAssignment();
					assignment.setPosition(position);
					assignment.setRoleGroup(roleGroup);
					assignment.setAssignedByName("Systembruger");
					assignment.setAssignedByUserId("system");
					assignment.setAssignedTimestamp(new Date());
					position.getRoleGroupAssignments().add(assignment);
				}
				
				rs = new ArrayList<>();
				for (int n = 0; n < 4; n++) {
					int pick = random.nextInt(userRoles.size());
					rs.add(userRoles.get(pick));				
				}
				
				for (UserRole userRole : rs) {
					PositionUserRoleAssignment assignment = new PositionUserRoleAssignment();
					assignment.setPosition(position);
					assignment.setUserRole(userRole);
					assignment.setAssignedByName("Systembruger");
					assignment.setAssignedByUserId("system");
					assignment.setAssignedTimestamp(new Date());
					position.getUserRoleAssignments().add(assignment);
				}
				
				positions.add(position);
			}
			user.setPositions(positions);

			userDao.save(user);
		}
	}
}
