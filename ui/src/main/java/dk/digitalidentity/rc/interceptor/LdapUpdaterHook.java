package dk.digitalidentity.rc.interceptor;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PendingADUpdateService;

@Component
public class LdapUpdaterHook implements RoleChangeHook {

	@Autowired
	private PendingADUpdateService pendingADUpdateService;
	
	@Autowired
	private OrgUnitService orgUnitService;
		
	@Override
	public void interceptAddRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		if (!user.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(roleGroup)) {
			pendingADUpdateService.addRoleGroupToQueue(roleGroup);
		}
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		if (user.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(roleGroup)) {
			pendingADUpdateService.addRoleGroupToQueue(roleGroup);
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnUser(User user, UserRole userRole) {
		if (!user.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
			pendingADUpdateService.addUserRoleToQueue(userRole);
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnUser(User user, UserRole userRole) {
		if (user.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
			pendingADUpdateService.addUserRoleToQueue(userRole);
		}
	}

	@Override
	public void interceptAddPositionOnUser(User user, Position position) {
		if (!user.getPositions().contains(position)) {
			if (!user.isDoNotInherit()) {
				pendingADUpdateService.addUserToQueue(user, position);
			}
		}
	}

	@Override
	public void interceptRemovePositionOnUser(User user, Position position) {
		if (user.getPositions().contains(position)) {
			if (!user.isDoNotInherit()) {
				pendingADUpdateService.addUserToQueue(user, position);
			}
		}
	}

	@Override
	public void interceptAddRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup) {
      	if (!position.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(roleGroup)) {
			pendingADUpdateService.addRoleGroupToQueue(roleGroup);
		}
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup) {
      	if (position.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(roleGroup)) {
			pendingADUpdateService.addRoleGroupToQueue(roleGroup);
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnPosition(Position position, UserRole userRole) {
      	if (!position.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
			pendingADUpdateService.addUserRoleToQueue(userRole);
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnPosition(Position position, UserRole userRole) {
		if (position.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
			pendingADUpdateService.addUserRoleToQueue(userRole);
		}
	}

	@Override
	public void interceptAddRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, boolean inherit) {
		if (!orgUnitService.getRoleGroups(ou, true).contains(roleGroup)) {
			pendingADUpdateService.addRoleGroupToQueue(roleGroup);
		}
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup) {
		boolean assigned = false;

		for (OrgUnitRoleGroupAssignment roleGroupMapping : ou.getRoleGroupAssignments()) {
			if (roleGroupMapping.getRoleGroup().getId() == roleGroup.getId()) {
				assigned = true;
			}
		}

		// there is a special case, where a parent OU has the same role assigned with the inherit flag,
		// and we now flag the user as dirty, even though the user is no such thing (not a big issue though)
		if (assigned) {
			pendingADUpdateService.addRoleGroupToQueue(roleGroup);
		}
	}
	
	@Override
	public void interceptAddUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole, boolean inherit) {
		if (!orgUnitService.getUserRoles(ou, true).contains(userRole)) {
			pendingADUpdateService.addUserRoleToQueue(userRole);
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole) {
		boolean assigned = false;

		for (OrgUnitUserRoleAssignment roleMapping : ou.getUserRoleAssignments()) {
			if (roleMapping.getUserRole().getId() == userRole.getId()) {
				assigned = true;
			}
		}

		if (assigned) {
			pendingADUpdateService.addUserRoleToQueue(userRole);
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		// role-modelling does not happen often enough that we should bother safety checking - just flag as dirty
		pendingADUpdateService.addUserRoleToQueue(userRole);
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		// role-modelling does not happen often enough that we should bother safety checking - just flag as dirty
		pendingADUpdateService.addUserRoleToQueue(userRole);
	}

	@Override
	public void interceptAddSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		// role-modelling does not happen often enough that we should bother safety checking - just flag as dirty
		pendingADUpdateService.addUserRoleToQueue(userRole);
	}

	@Override
	public void interceptRemoveSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		// role-modelling does not happen often enough that we should bother safety checking - just flag as dirty
		pendingADUpdateService.addUserRoleToQueue(userRole);
	}

	@Override
	public void interceptAddRoleGroupAssignmentOnTitle(Title title, RoleGroup roleGroup, String[] ouUuids) {
		pendingADUpdateService.addRoleGroupToQueue(roleGroup);
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnTitle(Title title, RoleGroup roleGroup) {
		pendingADUpdateService.addRoleGroupToQueue(roleGroup);
	}

	@Override
	public void interceptAddUserRoleAssignmentOnTitle(Title title, UserRole userRole, String[] ouUuids) {
		pendingADUpdateService.addUserRoleToQueue(userRole);
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnTitle(Title title, UserRole userRole) {
		pendingADUpdateService.addUserRoleToQueue(userRole);
	}
}
