package dk.digitalidentity.rc.interceptor;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.ManualRolesService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.UserWithRole;

@Component
public class ManualRolesHook implements RoleChangeHook {

	@Autowired
	private UserService userService;
	
	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private ManualRolesService manualRolesService;

	@Override
	public void interceptAddRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		if (!user.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(roleGroup)) {
			manualRolesService.addUserToQueue(user, roleGroup);
		}
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		if (user.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(roleGroup)) {
			manualRolesService.addUserToQueue(user, roleGroup);
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnUser(User user, UserRole userRole) {
		if (!user.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
			manualRolesService.addUserToQueue(user, userRole);
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnUser(User user, UserRole userRole) {
		if (user.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
			manualRolesService.addUserToQueue(user, userRole);
		}
	}

	@Override
	public void interceptAddPositionOnUser(User user, Position position) {
		if (!user.getPositions().contains(position)) {
			if (!user.isDoNotInherit()) {
				manualRolesService.addUserToQueue(user, position);
			}
		}
	}

	@Override
	public void interceptRemovePositionOnUser(User user, Position position) {
		if (user.getPositions().contains(position)) {
			if (!user.isDoNotInherit()) {
				manualRolesService.addUserToQueue(user, position);
			}
		}
	}

	@Override
	public void interceptAddRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup) {
		if (!position.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(roleGroup)) {
			manualRolesService.addUserToQueue(position.getUser(), roleGroup);
		}
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup) {
		if (position.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(roleGroup)) {
			manualRolesService.addUserToQueue(position.getUser(), roleGroup);
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnPosition(Position position, UserRole userRole) {
      	if (!position.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
			manualRolesService.addUserToQueue(position.getUser(), userRole);
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnPosition(Position position, UserRole userRole) {
      	if (position.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
			manualRolesService.addUserToQueue(position.getUser(), userRole);
		}
	}

	@Override
	public void interceptAddRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, boolean inherit) {
		if (!orgUnitService.getRoleGroups(ou, true).contains(roleGroup)) {
			recursiveRoleGroupChange(ou, roleGroup, inherit);
		}
	}
	
	@Override
	public void interceptRemoveRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup) {
		boolean assigned = false;
		boolean inherit = false;

		for (OrgUnitRoleGroupAssignment roleGroupMapping : ou.getRoleGroupAssignments()) {
			if (roleGroupMapping.getRoleGroup().getId() == roleGroup.getId()) {
				assigned = true;
				
				if (roleGroupMapping.isInherit()) {
					inherit = true;
				}
			}
		}

		// there is a special case, where a parent OU has the same role assigned with the inherit flag,
		// and we now flag the user as dirty, even though the user is no such thing (not a big issue though)
		if (assigned) {
			recursiveRoleGroupChange(ou, roleGroup, inherit);
		}
	}
	
	private void recursiveRoleGroupChange(OrgUnit ou, RoleGroup roleGroup, boolean inherit) {
		for (User user : userService.findByOrgUnit(ou)) {
			if (!user.isDoNotInherit()) {
				manualRolesService.addUserToQueue(user, roleGroup);
			}
		}
		
		if (inherit) {
			for (OrgUnit child : ou.getChildren()) {
				recursiveRoleGroupChange(child, roleGroup, inherit);
			}
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole, boolean inherit) {
		if (!orgUnitService.getUserRoles(ou, true).contains(userRole)) {
			recursiveUserRoleChange(ou, userRole, inherit);
		}
	}

	private void recursiveUserRoleChange(OrgUnit ou, UserRole userRole, boolean inherit) {
		for (User user : userService.findByOrgUnit(ou)) {
			if (!user.isDoNotInherit()) {
				manualRolesService.addUserToQueue(user, userRole);
			}
		}
		
		if (inherit) {
			for (OrgUnit child : ou.getChildren()) {
				recursiveUserRoleChange(child, userRole, inherit);
			}
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole) {
		boolean assigned = false;
		boolean inherit = false;

		for (OrgUnitUserRoleAssignment roleMapping : ou.getUserRoleAssignments()) {
			if (roleMapping.getUserRole().getId() == userRole.getId()) {
				assigned = true;
				
				if (roleMapping.isInherit()) {
					inherit = true;
				}
			}
		}

		// there is a special case, where a parent OU has the same role assigned with the inherit flag,
		// and we now flag the user as dirty, even though the user is no such thing (not a big issue though)
		if (assigned) {
			recursiveUserRoleChange(ou, userRole, inherit);
		}

		if (orgUnitService.getUserRoles(ou, true).contains(userRole)) {
			for (User user : userService.findByOrgUnit(ou)) {
				if (!user.isDoNotInherit()) {
					manualRolesService.addUserToQueue(user, userRole);
				}
			}
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
      	if (!roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
			List<UserWithRole> mappings = userService.getUsersWithRoleGroup(roleGroup, true);
			List<User> users = mappings.stream().map(m -> m.getUser()).collect(Collectors.toList());

			for (User user : users) {
				manualRolesService.addUserToQueue(user, roleGroup);
			}
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
      	if (roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
			List<UserWithRole> mappings = userService.getUsersWithRoleGroup(roleGroup, true);
			List<User> users = mappings.stream().map(m -> m.getUser()).collect(Collectors.toList());

			for (User user : users) {
				manualRolesService.addUserToQueue(user, roleGroup);
			}
		}
	}

	@Override
	public void interceptAddSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		if (!userRole.getSystemRoleAssignments().contains(systemRoleAssignment)) {
			List<UserWithRole> mappings = userService.getUsersWithUserRole(userRole, true);
			List<User> users = mappings.stream().map(m -> m.getUser()).collect(Collectors.toList());

			for (User user : users) {
				manualRolesService.addUserToQueue(user, userRole);
			}
		}
	}

	@Override
	public void interceptRemoveSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		if (userRole.getSystemRoleAssignments().contains(systemRoleAssignment)) {
			List<UserWithRole> mappings = userService.getUsersWithUserRole(userRole, true);
			List<User> users = mappings.stream().map(m -> m.getUser()).collect(Collectors.toList());

			for (User user : users) {
				manualRolesService.addUserToQueue(user, userRole);
			}
		}
	}
}
