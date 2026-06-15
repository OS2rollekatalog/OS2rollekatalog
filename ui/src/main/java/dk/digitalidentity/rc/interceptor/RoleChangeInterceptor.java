package dk.digitalidentity.rc.interceptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;

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

@Aspect
public class RoleChangeInterceptor {

	@Autowired
	private List<RoleChangeHook> hooks;

	// UserService

	@Before("execution(* dk.digitalidentity.rc.service.UserService.editUserRoleAssignment(dk.digitalidentity.rc.dao.model.User, dk.digitalidentity.rc.dao.model.UserUserRoleAssignment, ..)) && args(user, assignment, ..)")
	public void interceptEditAssignment(final User user, dk.digitalidentity.rc.dao.model.UserUserRoleAssignment assignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptEditUserRoleAssignmentOnUser(user, assignment);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserService.editRoleGroupAssignment(dk.digitalidentity.rc.dao.model.User, dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment, ..)) && args(user, assignment, ..)")
	public void interceptEditRoleGroupAssignment(final User user, UserRoleGroupAssignment assignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptEditRoleGroupAssignmentOnUser(user, assignment);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserService.activateUser(dk.digitalidentity.rc.dao.model.User)) && args(user)")
	public void interceptActivateUser(User user) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptActivateUser(user);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserService.flagUserDeleted(dk.digitalidentity.rc.dao.model.User)) && args(user)")
	public void interceptFlagUserDeleted(User user) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptFlagUserDeleted(user);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserService.addRoleGroup(dk.digitalidentity.rc.dao.model.User, dk.digitalidentity.rc.dao.model.RoleGroup, java.time.LocalDate, java.time.LocalDate, ..)) && args(user, roleGroup, startDate, stopDate, ..)")
	public void interceptAddRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup, LocalDate startDate, LocalDate stopDate) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptAddImmediateRoleGroupAssignmentOnUser(user, roleGroup);
			if (startDate != null && startDate.isAfter(LocalDate.now())) {
				// skip assignments that are active in the future (we will get a direct event-call later)
				continue;
			}
			hook.interceptAddRoleGroupAssignmentOnUser(user, roleGroup);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserService.removeRoleGroup(dk.digitalidentity.rc.dao.model.User, dk.digitalidentity.rc.dao.model.RoleGroup)) && args(user, roleGroup)")
	public void interceptRemoveRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveRoleGroupAssignmentOnUser(user, roleGroup);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserService.removeRoleGroupAssignment(dk.digitalidentity.rc.dao.model.User, dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment)) && args(user, assignment)")
	public void interceptRemoveRoleGroupAssignmentOnUser2(User user, UserRoleGroupAssignment assignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveRoleGroupAssignmentOnUser(user, assignment.getRoleGroup());
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserService.addUserRole(dk.digitalidentity.rc.dao.model.User, dk.digitalidentity.rc.dao.model.UserRole, java.time.LocalDate, java.time.LocalDate, ..)) && args(user, userRole, startDate, stopDate, ..)")
	public void interceptAddUserRoleAssignmentOnUser(User user, UserRole userRole, LocalDate startDate, LocalDate stopDate) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptAddImmediateUserRoleAssignmentOnUser(user, userRole);
			if (startDate != null && startDate.isAfter(LocalDate.now())) {
				// skip assignments that are active in the future (we will get a direct event-call later)
				continue;
			}
			hook.interceptAddUserRoleAssignmentOnUser(user, userRole);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserService.removeUserRole(dk.digitalidentity.rc.dao.model.User, dk.digitalidentity.rc.dao.model.UserRole)) && args(user, userRole)")
	public void interceptRemoveUserRoleAssignmentOnUser(User user, UserRole userRole) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveUserRoleAssignmentOnUser(user, userRole);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserService.removeUserRoleAssignment(dk.digitalidentity.rc.dao.model.User, dk.digitalidentity.rc.dao.model.UserUserRoleAssignment)) && args(user, assignment)")
	public void interceptRemoveUserRoleAssignmentOnUser2(User user, UserUserRoleAssignment assignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveUserRoleAssignmentOnUser(user, assignment.getUserRole());
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserService.addPosition(dk.digitalidentity.rc.dao.model.User, dk.digitalidentity.rc.dao.model.Position)) && args(user, position)")
	public void interceptAddPositionOnUser(User user, Position position) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptAddPositionOnUser(user, position);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserService.removePosition(dk.digitalidentity.rc.dao.model.User, dk.digitalidentity.rc.dao.model.Position)) && args(user, position)")
	public void interceptRemovePositionOnUser(User user, Position position) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemovePositionOnUser(user, position);
		}
	}

	// OrgUnitService

	@AfterReturning(
		pointcut = "execution(* dk.digitalidentity.rc.service.OrgUnitService.addUserRole(..))",
		returning = "result")
	public void interceptAddedUserRoleAssignmentOnOrgUnit(OrgUnitUserRoleAssignment result) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptAddedUserRoleAssignmentOnOrgUnit(result.getOrgUnit(), result);
		}
	}

	@AfterReturning(
		pointcut = "execution(* dk.digitalidentity.rc.service.OrgUnitService.addRoleGroup(..))",
		returning = "result")
	public void interceptAddedRoleGroupAssignmentOnOrgUnit(OrgUnitRoleGroupAssignment result) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptAddedRoleGroupAssignmentOnOrgUnit(result.getOrgUnit(), result);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.addRoleGroup(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.RoleGroup, boolean, java.time.LocalDate, java.time.LocalDate, java.util.Set, java.util.Set, boolean, boolean, boolean, java.util.Set, String)) && args(ou, roleGroup, inherit, startDate, stopDate, exceptedUsers, titles, *, *, *, *, caseNumber)")
	public void interceptAddRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, boolean inherit, LocalDate startDate, LocalDate stopDate, Set<String> exceptedUsers, Set<String> titles, String caseNumber) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptAddImmediateRoleGroupAssignmentOnOrgUnit(ou, roleGroup, inherit);
			if (startDate != null && startDate.isAfter(LocalDate.now())) {
				// skip assignments that are active in the future (we will get a direct event-call later)
				continue;
			}
			hook.interceptAddRoleGroupAssignmentOnOrgUnit(ou, roleGroup, inherit);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.removeRoleGroup(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.RoleGroup)) && args(ou, roleGroup)")
	public void interceptRemoveRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveRoleGroupAssignmentOnOrgUnit(ou, roleGroup);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.removeRoleGroupAssignment(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment)) && args(ou, assignment)")
	public void interceptRemoveRoleGroupAssignmentOnOrgUnit2(OrgUnit ou, OrgUnitRoleGroupAssignment assignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveRoleGroupAssignmentOnOrgUnit(ou, assignment.getRoleGroup());
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.addUserRole(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.UserRole, boolean, java.time.LocalDate, java.time.LocalDate, java.util.Set, java.util.Set, boolean, boolean, boolean, java.util.Set, String)) && args(ou, userRole, inherit, startDate, stopDate, exceptedUsers, titles, *, *, *, *, caseNumber)")
	public void interceptAddUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole, boolean inherit, LocalDate startDate, LocalDate stopDate, Set<String> exceptedUsers, Set<String> titles, String caseNumber) {

		for (RoleChangeHook hook : hooks) {
			hook.interceptAddImmediateUserRoleAssignmentOnOrgUnit(ou, userRole, inherit);
			if (startDate != null && startDate.isAfter(LocalDate.now())) {
				// skip assignments that are active in the future (we will get a direct event-call later)
				continue;
			}
			hook.interceptAddUserRoleAssignmentOnOrgUnit(ou, userRole, inherit);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.removeUserRole(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.UserRole)) && args(ou, userRole)")
	public void interceptRemoveUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveUserRoleAssignmentOnOrgUnit(ou, userRole);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.removeUserRoleAssignment(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment)) && args(ou, assignment)")
	public void interceptRemoveUserRoleAssignmentOnOrgUnit2(OrgUnit ou, OrgUnitUserRoleAssignment assignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveUserRoleAssignmentOnOrgUnit(ou, assignment.getUserRole());
		}
	}

	// public boolean updateUserRoleAssignment(OrgUnit ou, OrgUnitUserRoleAssignment assignment, boolean inherit, LocalDate startDate, LocalDate stopDate, Set<String> exceptedUsers, Set<String> titleUuids) {
	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.updateUserRoleAssignment(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment, ..)) && args(ou, assignment, ..)")
	public void interceptEditUserRoleAssignmentOnOrgUnit(OrgUnit ou, OrgUnitUserRoleAssignment assignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptEditUserRoleAssignmentOnOrgUnit(ou, assignment.getUserRole());
		}
	}

	// public boolean updateRoleGroupAssignment(OrgUnit ou, OrgUnitRoleGroupAssignment assignment, boolean inherit, LocalDate startDate, LocalDate stopDate, Set<String> exceptedUsers, Set<String> titleUuids) {
	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.updateRoleGroupAssignment(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment, ..)) && args(ou, assignment, ..)")
	public void interceptEditRoleGroupAssignmentOnOrgUnit(OrgUnit ou, OrgUnitRoleGroupAssignment assignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptEditRoleGroupAssignmentOnOrgUnit(ou, assignment.getRoleGroup());
		}
	}

	@AfterReturning("execution(* dk.digitalidentity.rc.service.OrgUnitService.updateUserRoleAssignment(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment, ..)) && args(ou, assignment, ..)")
	public void interceptEditUserRoleAssignmentOnOrgUnitAfter(OrgUnit ou, OrgUnitUserRoleAssignment assignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptEditUserRoleAssignmentOnOrgUnitAfter(ou, assignment.getUserRole());
		}
	}

	@AfterReturning("execution(* dk.digitalidentity.rc.service.OrgUnitService.updateRoleGroupAssignment(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment, ..)) && args(ou, assignment, ..)")
	public void interceptEditRoleGroupAssignmentOnOrgUnitAfter(OrgUnit ou, OrgUnitRoleGroupAssignment assignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptEditRoleGroupAssignmentOnOrgUnitAfter(ou, assignment.getRoleGroup());
		}
	}

	/// / RoleGroupService Hooks

	@Before("execution(* dk.digitalidentity.rc.service.RoleGroupService.addUserRole(dk.digitalidentity.rc.dao.model.RoleGroup, dk.digitalidentity.rc.dao.model.UserRole)) && args(roleGroup, userRole)")
	public void interceptAddUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptAddUserRoleAssignmentOnRoleGroup(roleGroup, userRole);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.RoleGroupService.removeUserRole(dk.digitalidentity.rc.dao.model.RoleGroup, dk.digitalidentity.rc.dao.model.UserRole)) && args(roleGroup, userRole)")
	public void interceptRemoveUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveUserRoleAssignmentOnRoleGroup(roleGroup, userRole);
		}
	}

	/// / UserRoleService Hooks

	@Before("execution(* dk.digitalidentity.rc.service.UserRoleService.addSystemRoleAssignment(dk.digitalidentity.rc.dao.model.UserRole, dk.digitalidentity.rc.dao.model.SystemRoleAssignment)) && args(userRole, systemRoleAssignment)")
	public void interceptAddSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptAddSystemRoleAssignmentOnUserRole(userRole, systemRoleAssignment);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserRoleService.removeSystemRoleAssignment(dk.digitalidentity.rc.dao.model.UserRole, dk.digitalidentity.rc.dao.model.SystemRoleAssignment)) && args(userRole, systemRoleAssignment)")
	public void interceptRemoveSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveSystemRoleAssignmentOnUserRole(userRole, systemRoleAssignment);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserRoleService.delete(dk.digitalidentity.rc.dao.model.UserRole)) && args(userRole)")
	public void interceptDeleteUserRole(UserRole userRole) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptDeleteUserRole(userRole);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.RoleGroupService.delete(dk.digitalidentity.rc.dao.model.RoleGroup)) && args(roleGroup)")
	public void interceptDeleteRoleGroup(RoleGroup roleGroup) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptDeleteRoleGroup(roleGroup);
		}
	}
}
