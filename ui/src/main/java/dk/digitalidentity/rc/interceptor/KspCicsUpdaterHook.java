package dk.digitalidentity.rc.interceptor;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.cics.KspCicsService;

@Component
public class KspCicsUpdaterHook implements RoleChangeHook {

	@Autowired
	private KspCicsService kspCicsService;
		
	@Autowired
	private UserService userService;
		
	@Override
	public void interceptAddRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		if (userService.hasCicsUser(user)) {
			addRoleGroupToQueue(roleGroup);
		}
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		if (userService.hasCicsUser(user)) {
			addRoleGroupToQueue(roleGroup);
		}
	}
	
	@Override
	public void interceptActivateUser(User user) {
		if (userService.hasCicsUser(user)) {
			kspCicsService.addUserToQueue(user);
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnUser(User user, UserRole userRole) {
		if (userService.hasCicsUser(user)) {
			kspCicsService.addUserRoleToQueue(userRole);
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnUser(User user, UserRole userRole) {
		if (userService.hasCicsUser(user)) {
			kspCicsService.addUserRoleToQueue(userRole);
		}
	}

	@Override
	public void interceptAddPositionOnUser(User user, Position position) {
		if (userService.hasCicsUser(user)) {
			kspCicsService.addUserToQueue(user);
		}
	}

	@Override
	public void interceptRemovePositionOnUser(User user, Position position) {
		if (userService.hasCicsUser(user)) {
			kspCicsService.addUserToQueue(user);
		}
	}

	@Override
	public void interceptAddRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup) {
		if (userService.hasCicsUser(position.getUser())) {
      		addRoleGroupToQueue(roleGroup);
		}
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup) {
		if (userService.hasCicsUser(position.getUser())) {
      		addRoleGroupToQueue(roleGroup);
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnPosition(Position position, UserRole userRole) {
		if (userService.hasCicsUser(position.getUser())) {
      		kspCicsService.addUserRoleToQueue(userRole);
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnPosition(Position position, UserRole userRole) {
		if (userService.hasCicsUser(position.getUser())) {
			kspCicsService.addUserRoleToQueue(userRole);
		}
	}

	@Override
	public void interceptAddRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, boolean inherit) {
		addRoleGroupToQueue(roleGroup);
	}
	
	@Override
	public void interceptRemoveRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup) {
		addRoleGroupToQueue(roleGroup);
	}
	
	@Override
	public void interceptAddUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole, boolean inherit) {
		kspCicsService.addUserRoleToQueue(userRole);
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole) {
		kspCicsService.addUserRoleToQueue(userRole);
	}

	@Override
	public void interceptAddUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		kspCicsService.addUserRoleToQueue(userRole);
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		kspCicsService.addUserRoleToQueue(userRole);
	}

	@Override
	public void interceptAddSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		; // UserRoles from CICS is never modified
	}

	@Override
	public void interceptRemoveSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		; // UserRoles from CICS is never modified
	}
	
	@Override
	public void interceptEditUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole) {
		kspCicsService.addUserRoleToQueue(userRole);
	}

	@Override
	public void interceptEditRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup) {
		addRoleGroupToQueue(roleGroup);
	}
	
	private void addRoleGroupToQueue(RoleGroup roleGroup) {
		if (roleGroup != null && roleGroup.getUserRoleAssignments() != null) {
			List<UserRole> userRoles = roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());

			for (UserRole userRole : userRoles) {
				kspCicsService.addUserRoleToQueue(userRole);
			}
		}
	}
}
