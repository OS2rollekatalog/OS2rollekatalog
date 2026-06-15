package dk.digitalidentity.rc.interceptor;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;

public interface RoleChangeHook {

	// UserServiceHooks
	public void interceptActivateUser(User user);
	public void interceptFlagUserDeleted(User user);
	public void interceptAddRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup);
	public default void interceptAddImmediateRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {};
	public void interceptRemoveRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup);
	public void interceptAddUserRoleAssignmentOnUser(User user, UserRole userRole);
	public default void interceptAddImmediateUserRoleAssignmentOnUser(User user, UserRole userRole) {}; //
	public void interceptEditUserRoleAssignmentOnUser(User user, UserUserRoleAssignment assignment);
	public default void interceptEditRoleGroupAssignmentOnUser(User user, UserRoleGroupAssignment assignment) {};
	public void interceptRemoveUserRoleAssignmentOnUser(User user, UserRole userRole);
	public void interceptAddPositionOnUser(User user, Position position);
	public void interceptRemovePositionOnUser(User user, Position position);

	// OrgUnitService Hooks

	/**fired @After the save, full assignment object is available*/
	public default void interceptAddedUserRoleAssignmentOnOrgUnit(OrgUnit ou, OrgUnitUserRoleAssignment assignment) {}
	/**fired @After the save, full assignment object is available*/
	public default void interceptAddedRoleGroupAssignmentOnOrgUnit(OrgUnit ou, OrgUnitRoleGroupAssignment assignment) {}

	public void interceptAddRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, boolean inherit);
	public default void interceptAddImmediateRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, boolean inherit) {};
	public void interceptRemoveRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup);
	public void interceptAddUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole, boolean inherit);
	public default void interceptAddImmediateUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole, boolean inherit) {};
	public void interceptRemoveUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole);
	public void interceptEditUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole);
	public default void interceptEditUserRoleAssignmentOnOrgUnitAfter(OrgUnit ou, UserRole userRole) {}
	public void interceptEditRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup);
	public default void interceptEditRoleGroupAssignmentOnOrgUnitAfter(OrgUnit ou, RoleGroup roleGroup) {}

	// RoleGroupService Hooks
	public void interceptAddUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole);
	public void interceptRemoveUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole);

	// UserRoleService Hooks
	public void interceptAddSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment);
	public void interceptRemoveSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment);
	public default void interceptDeleteUserRole(UserRole userRole) {}
	public default void interceptDeleteRoleGroup(RoleGroup roleGroup) {}
}
