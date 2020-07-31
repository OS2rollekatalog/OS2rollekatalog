package dk.digitalidentity.rc.interceptor;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;

public interface RoleChangeHook {

	// UserServiceHooks
	public void interceptAddRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup);
	public void interceptRemoveRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup);
	public void interceptAddUserRoleAssignmentOnUser(User user, UserRole userRole);
	public void interceptRemoveUserRoleAssignmentOnUser(User user, UserRole userRole);
	public void interceptAddPositionOnUser(User user, Position position);
	public void interceptRemovePositionOnUser(User user, Position position);

	// PositionService Hooks
	public void interceptAddRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup);
	public void interceptRemoveRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup);
	public void interceptAddUserRoleAssignmentOnPosition(Position position, UserRole userRole);
	public void interceptRemoveUserRoleAssignmentOnPosition(Position position, UserRole userRole);

	// OrgUnitService Hooks
	public void interceptAddRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, boolean inherit);
	public void interceptRemoveRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup);
	public void interceptAddUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole, boolean inherit);
	public void interceptRemoveUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole);
	
	// RoleGroupService Hooks
	public void interceptAddUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole);
	public void interceptRemoveUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole);

	//// UserRoleService Hooks
	public void interceptAddSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment);
	public void interceptRemoveSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment);
}
