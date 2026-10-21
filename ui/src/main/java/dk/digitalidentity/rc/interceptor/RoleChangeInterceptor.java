package dk.digitalidentity.rc.interceptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

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
import dk.digitalidentity.rc.dao.model.assignment.UserRoleModifiedEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
public class RoleChangeInterceptor {

	@Autowired(required = false)
	private List<RoleChangeHook> hooks = List.of();

	@Autowired
    private ApplicationEventPublisher eventPublisher;

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

	@Around("execution(* dk.digitalidentity.rc.service.UserService.save(..))")
	public Object interceptCreateUser(ProceedingJoinPoint joinPoint) throws Throwable {
		boolean create = false;

		if (joinPoint.getArgs().length > 0) {
			Object target = joinPoint.getArgs()[0];

			if (target != null && target instanceof User user) {
				if (user.getLastUpdated() == null) {
					create = true;
				}
			}
		}
		
		Object o = joinPoint.proceed();
		if (create && o instanceof User user) {
			for (RoleChangeHook hook : hooks) {
				hook.interceptCreateUser(user);
			}
		}
		
		return o;
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
		pointcut = "execution(* dk.digitalidentity.rc.service.OrgUnitService.addUserRole(..)) || execution(* dk.digitalidentity.rc.service.OrgUnitService.addUserRoleWithInheritAndExceptedOus(..))",
		returning = "result")
	public void interceptAddedUserRoleAssignmentOnOrgUnit(OrgUnitUserRoleAssignment result) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptAddedUserRoleAssignmentOnOrgUnit(result.getOrgUnit(), result);
		}
	}

	@AfterReturning(
		pointcut = "execution(* dk.digitalidentity.rc.service.OrgUnitService.addRoleGroup(..)) || execution(* dk.digitalidentity.rc.service.OrgUnitService.addRoleGroupWithInheritAndExceptedOus(..))",
		returning = "result")
	public void interceptAddedRoleGroupAssignmentOnOrgUnit(OrgUnitRoleGroupAssignment result) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptAddedRoleGroupAssignmentOnOrgUnit(result.getOrgUnit(), result);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.addRoleGroup(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.RoleGroup, boolean, java.time.LocalDate, java.time.LocalDate, java.util.Set, java.util.Set, boolean, boolean, boolean, java.util.Set, String)) && args(ou, roleGroup, inherit, startDate, stopDate, exceptedUsers, titles, *, *, *, *, caseNumber)")
	public void interceptAddRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, boolean inherit, LocalDate startDate, LocalDate stopDate, Set<String> exceptedUsers, Set<String> titles, String caseNumber) {
		notifyRoleGroupAddedOnOrgUnit(ou, roleGroup, inherit, startDate);
	}

	// The excepted-ous assignments have their own method signature, and always inherit
	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.addRoleGroupWithInheritAndExceptedOus(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.RoleGroup, ..)) && args(ou, roleGroup, startDate, stopDate, ..)")
	public void interceptAddRoleGroupAssignmentWithExceptedOusOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, LocalDate startDate, LocalDate stopDate) {
		notifyRoleGroupAddedOnOrgUnit(ou, roleGroup, true, startDate);
	}

	private void notifyRoleGroupAddedOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, boolean inherit, LocalDate startDate) {
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
			hook.interceptRemoveRoleGroupAssignmentOnOrgUnit(ou, assignment);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.addUserRole(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.UserRole, boolean, java.time.LocalDate, java.time.LocalDate, java.util.Set, java.util.Set, boolean, boolean, boolean, java.util.Set, String)) && args(ou, userRole, inherit, startDate, stopDate, exceptedUsers, titles, *, *, *, *, caseNumber)")
	public void interceptAddUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole, boolean inherit, LocalDate startDate, LocalDate stopDate, Set<String> exceptedUsers, Set<String> titles, String caseNumber) {
		notifyUserRoleAddedOnOrgUnit(ou, userRole, inherit, startDate);
	}

	// The excepted-ous assignments have their own method signature, and always inherit
	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.addUserRoleWithInheritAndExceptedOus(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.UserRole, ..)) && args(ou, userRole, startDate, stopDate, ..)")
	public void interceptAddUserRoleAssignmentWithExceptedOusOnOrgUnit(OrgUnit ou, UserRole userRole, LocalDate startDate, LocalDate stopDate) {
		notifyUserRoleAddedOnOrgUnit(ou, userRole, true, startDate);
	}

	private void notifyUserRoleAddedOnOrgUnit(OrgUnit ou, UserRole userRole, boolean inherit, LocalDate startDate) {
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
			hook.interceptRemoveUserRoleAssignmentOnOrgUnit(ou, assignment);
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

	// The excepted-ous updates take the assignment without the org unit, so the org unit is read off the assignment
	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.updateUserRoleWithInheritAndExceptedOus(dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment, ..)) && args(assignment, ..)")
	public void interceptEditUserRoleAssignmentWithExceptedOusOnOrgUnit(OrgUnitUserRoleAssignment assignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptEditUserRoleAssignmentOnOrgUnit(assignment.getOrgUnit(), assignment.getUserRole());
		}
	}

	@AfterReturning("execution(* dk.digitalidentity.rc.service.OrgUnitService.updateUserRoleWithInheritAndExceptedOus(dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment, ..)) && args(assignment, ..)")
	public void interceptEditUserRoleAssignmentWithExceptedOusOnOrgUnitAfter(OrgUnitUserRoleAssignment assignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptEditUserRoleAssignmentOnOrgUnitAfter(assignment.getOrgUnit(), assignment.getUserRole());
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.updateRoleGroupWithInheritAndExceptedOus(dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment, ..)) && args(assignment, ..)")
	public void interceptEditRoleGroupAssignmentWithExceptedOusOnOrgUnit(OrgUnitRoleGroupAssignment assignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptEditRoleGroupAssignmentOnOrgUnit(assignment.getOrgUnit(), assignment.getRoleGroup());
		}
	}

	@AfterReturning("execution(* dk.digitalidentity.rc.service.OrgUnitService.updateRoleGroupWithInheritAndExceptedOus(dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment, ..)) && args(assignment, ..)")
	public void interceptEditRoleGroupAssignmentWithExceptedOusOnOrgUnitAfter(OrgUnitRoleGroupAssignment assignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptEditRoleGroupAssignmentOnOrgUnitAfter(assignment.getOrgUnit(), assignment.getRoleGroup());
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
		
		switch (userRole.getItSystem().getSystemType()) {
			case SAML:
			case KOMBIT:
				break;
			case AD:
			case KSPCICS:
			case MANUAL:
			case NEMLOGIN:
				eventPublisher.publishEvent(new UserRoleModifiedEvent(userRole.getId()));
				break;
		}
	}

	// Notifying costs a recalculation of every user holding the role, so a call that leaves the values
	// untouched - a removal of a constraint that is not there, a bulk replace with what is already stored - must not fire
	@Around("(execution(* dk.digitalidentity.rc.service.UserRoleService.addSystemRoleConstraint(dk.digitalidentity.rc.dao.model.SystemRoleAssignment, ..))"
		+ " || execution(* dk.digitalidentity.rc.service.UserRoleService.updateSystemRoleConstraint(dk.digitalidentity.rc.dao.model.SystemRoleAssignment, ..))"
		+ " || execution(* dk.digitalidentity.rc.service.UserRoleService.removeSystemRoleConstraint(dk.digitalidentity.rc.dao.model.SystemRoleAssignment, ..))"
		+ " || execution(* dk.digitalidentity.rc.service.UserRoleService.replaceSystemRoleConstraints(dk.digitalidentity.rc.dao.model.SystemRoleAssignment, ..)))"
		+ " && args(systemRoleAssignment, ..)")
	public Object interceptEditSystemRoleConstraintsOnUserRole(ProceedingJoinPoint joinPoint, SystemRoleAssignment systemRoleAssignment) throws Throwable {
		String before = constraintSignature(systemRoleAssignment);
		Object result = joinPoint.proceed();

		if (!before.equals(constraintSignature(systemRoleAssignment))) {
			for (RoleChangeHook hook : hooks) {
				hook.interceptEditSystemRoleConstraintsOnUserRole(systemRoleAssignment.getUserRole(), systemRoleAssignment);
			}
		}

		return result;
	}

	// Order-independent: the values arrive in whatever order the join table hands them out
	private static String constraintSignature(SystemRoleAssignment assignment) {
		if (assignment.getConstraintValues() == null) {
			return "";
		}

		return assignment.getConstraintValues().stream()
			.map(value -> value.getConstraintType().getUuid() + "|" + value.getConstraintValueType()
				+ "|" + value.getConstraintValue() + "|" + value.getConstraintIdentifier() + "|" + value.isPostponed())
			.sorted()
			.collect(Collectors.joining(","));
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserRoleService.removeSystemRoleAssignment(dk.digitalidentity.rc.dao.model.UserRole, dk.digitalidentity.rc.dao.model.SystemRoleAssignment)) && args(userRole, systemRoleAssignment)")
	public void interceptRemoveSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveSystemRoleAssignmentOnUserRole(userRole, systemRoleAssignment);
		}
		
		switch (userRole.getItSystem().getSystemType()) {
			case SAML:
			case KOMBIT:
				break;
			case AD:
			case KSPCICS:
			case MANUAL:
			case NEMLOGIN:
				eventPublisher.publishEvent(new UserRoleModifiedEvent(userRole.getId()));
				break;
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

	public void mangerRoleChange(User user) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptManagerRoleChange(user);
		}
	}

	public void substituteRoleChange(User user) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptSubstituteRoleChange(user);
		}
	}
}
