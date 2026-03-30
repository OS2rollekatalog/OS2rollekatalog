package dk.digitalidentity.rc.interceptor;

import java.util.HashSet;
import java.util.Set;

import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PendingADUpdateService;

@Component
public class LdapUpdaterHook implements RoleChangeHook {

	@Autowired
	private PendingADUpdateService pendingADUpdateService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private AssignmentService assignmentService;

	@Override
	public void interceptAddRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		if (!user.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).anyMatch(g -> g.getId() == roleGroup.getId())) {
			pendingADUpdateService.addRoleGroupToQueue(roleGroup);
		}
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		if (user.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).anyMatch(g -> g.getId() == roleGroup.getId())) {
			pendingADUpdateService.addRoleGroupToQueue(roleGroup);
		}
	}

	@Override
	public void interceptActivateUser(User user) {
		Set<CurrentAssignment> assignments = assignmentService.getByUser(user);

		// Deduplicate by userRole to avoid adding same role multiple times
		Set<Long> processedUserRoleIds = new HashSet<>();

		for (CurrentAssignment assignment : assignments) {
			if (processedUserRoleIds.add(assignment.getUserRole().getId())) {
				pendingADUpdateService.addUserRoleToQueue(assignment.getUserRole());
			}
		}
	}

	@Override
	public void interceptFlagUserDeleted(User user) {
		Set<CurrentAssignment> assignments = assignmentService.getByUser(user);

		// Deduplicate by userRole to avoid adding same role multiple times
		Set<Long> processedUserRoleIds = new HashSet<>();

		for (CurrentAssignment assignment : assignments) {
			if (processedUserRoleIds.add(assignment.getUserRole().getId())) {
				pendingADUpdateService.addUserRoleToQueue(assignment.getUserRole());
			}
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnUser(User user, UserRole userRole) {
		if (!user.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).anyMatch(u -> u.getId() == userRole.getId())) {
			pendingADUpdateService.addUserRoleToQueue(userRole);
		}
	}

	@Override
	public void interceptEditUserRoleAssignmentOnUser(User user, UserUserRoleAssignment userRole) {
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnUser(User user, UserRole userRole) {
		if (user.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).anyMatch(u -> u.getId() == userRole.getId())) {
			pendingADUpdateService.addUserRoleToQueue(userRole);
		}
	}

	@Override
	public void interceptAddPositionOnUser(User user, Position position) {
		if (!user.getPositions().contains(position)) {
			if (!position.isDoNotInherit()) {
				pendingADUpdateService.addUserToQueue(user, position);
			}
		}
	}

	@Override
	public void interceptRemovePositionOnUser(User user, Position position) {
		if (user.getPositions().contains(position)) {
			if (!position.isDoNotInherit()) {
				pendingADUpdateService.addUserToQueue(user, position);
			}
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
		pendingADUpdateService.addUserRoleToQueue(userRole);
	}

	@Override
	public void interceptEditUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole) {
		pendingADUpdateService.addUserRoleToQueue(userRole);
	}

	@Override
	public void interceptEditRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup) {
		pendingADUpdateService.addRoleGroupToQueue(roleGroup);
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
}
