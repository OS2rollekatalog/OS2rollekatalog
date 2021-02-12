package dk.digitalidentity.rc.interceptor;

import java.time.LocalDate;
import java.util.List;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;

@Aspect
public class RoleChangeInterceptor {

	@Autowired
	private List<RoleChangeHook> hooks;

	// TitleService
	@Before("execution(* dk.digitalidentity.rc.service.TitleService.addRoleGroup(dk.digitalidentity.rc.dao.model.Title, dk.digitalidentity.rc.dao.model.RoleGroup, java.lang.String[], java.time.LocalDate, java.time.LocalDate)) && args(title, roleGroup, ouUuids, startDate, stopDate)")
	public void interceptAddRoleGroupAssignmentOnTitle(Title title, RoleGroup roleGroup, String[] ouUuids, LocalDate startDate, LocalDate stopDate) {

		// skip assignments that are active in the future (we will get a direct event-call later)
		if (startDate != null && LocalDate.now().isAfter(startDate)) {
			return;
		}

		for (RoleChangeHook hook : hooks) {
			hook.interceptAddRoleGroupAssignmentOnTitle(title, roleGroup, ouUuids);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.TitleService.removeRoleGroup(dk.digitalidentity.rc.dao.model.Title, dk.digitalidentity.rc.dao.model.RoleGroup)) && args(title, roleGroup)")
	public void interceptRemoveRoleGroupAssignmentOnTitle(Title title, RoleGroup roleGroup) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveRoleGroupAssignmentOnTitle(title, roleGroup);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.TitleService.addUserRole(dk.digitalidentity.rc.dao.model.Title, dk.digitalidentity.rc.dao.model.UserRole, java.lang.String[], java.time.LocalDate, java.time.LocalDate)) && args(title, userRole, ouUuids, startDate, stopDate)")
	public void interceptAddUserRoleAssignmentOnTitle(Title title, UserRole userRole, String[] ouUuids, LocalDate startDate, LocalDate stopDate) {

		// skip assignments that are active in the future (we will get a direct event-call later)
		if (startDate != null && LocalDate.now().isAfter(startDate)) {
			return;
		}

		for (RoleChangeHook hook : hooks) {
			hook.interceptAddUserRoleAssignmentOnTitle(title, userRole, ouUuids);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.TitleService.removeUserRole(dk.digitalidentity.rc.dao.model.Title, dk.digitalidentity.rc.dao.model.UserRole)) && args(title, userRole)")
	public void interceptRemoveUserRoleAssignmentOnUser(Title title, UserRole userRole) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveUserRoleAssignmentOnTitle(title, userRole);
		}
	}

	// UserService

	@Before("execution(* dk.digitalidentity.rc.service.UserService.addRoleGroup(dk.digitalidentity.rc.dao.model.User, dk.digitalidentity.rc.dao.model.RoleGroup, java.time.LocalDate, java.time.LocalDate)) && args(user, roleGroup, startDate, stopDate)")
	public void interceptAddRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup, LocalDate startDate, LocalDate stopDate) {
		
		// skip assignments that are active in the future (we will get a direct event-call later)
		if (startDate != null && LocalDate.now().isAfter(startDate)) {
			return;
		}

		for (RoleChangeHook hook : hooks) {
			hook.interceptAddRoleGroupAssignmentOnUser(user, roleGroup);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserService.removeRoleGroup(dk.digitalidentity.rc.dao.model.User, dk.digitalidentity.rc.dao.model.RoleGroup)) && args(user, roleGroup)")
	public void interceptRemoveRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveRoleGroupAssignmentOnUser(user, roleGroup);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserService.addUserRole(dk.digitalidentity.rc.dao.model.User, dk.digitalidentity.rc.dao.model.UserRole, java.time.LocalDate, java.time.LocalDate)) && args(user, userRole, startDate, stopDate)")
	public void interceptAddUserRoleAssignmentOnUser(User user, UserRole userRole, LocalDate startDate, LocalDate stopDate) {

		// skip assignments that are active in the future (we will get a direct event-call later)
		if (startDate != null && LocalDate.now().isAfter(startDate)) {
			return;
		}

		for (RoleChangeHook hook : hooks) {
			hook.interceptAddUserRoleAssignmentOnUser(user, userRole);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.UserService.removeUserRole(dk.digitalidentity.rc.dao.model.User, dk.digitalidentity.rc.dao.model.UserRole)) && args(user, userRole)")
	public void interceptRemoveUserRoleAssignmentOnUser(User user, UserRole userRole) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveUserRoleAssignmentOnUser(user, userRole);
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

	// PositionService
	
	@Before("execution(* dk.digitalidentity.rc.service.PositionService.addRoleGroup(dk.digitalidentity.rc.dao.model.Position, dk.digitalidentity.rc.dao.model.RoleGroup, java.time.LocalDate, java.time.LocalDate)) && args(position, roleGroup, startDate, stopDate)")
	public void interceptAddRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup, LocalDate startDate, LocalDate stopDate) {

		// skip assignments that are active in the future (we will get a direct event-call later)
		if (startDate != null && LocalDate.now().isAfter(startDate)) {
			return;
		}

		for (RoleChangeHook hook : hooks) {
			hook.interceptAddRoleGroupAssignmentOnPosition(position, roleGroup);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.PositionService.removeRoleGroup(dk.digitalidentity.rc.dao.model.Position, dk.digitalidentity.rc.dao.model.RoleGroup)) && args(position, roleGroup)")
	public void interceptRemoveRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveRoleGroupAssignmentOnPosition(position, roleGroup);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.PositionService.addUserRole(dk.digitalidentity.rc.dao.model.Position, dk.digitalidentity.rc.dao.model.UserRole, java.time.LocalDate, java.time.LocalDate)) && args(position, userRole, startDate, stopDate)")
	public void interceptAddUserRoleAssignmentOnPosition(Position position, UserRole userRole, LocalDate startDate, LocalDate stopDate) {

		// skip assignments that are active in the future (we will get a direct event-call later)
		if (startDate != null && LocalDate.now().isAfter(startDate)) {
			return;
		}

		for (RoleChangeHook hook : hooks) {
			hook.interceptAddUserRoleAssignmentOnPosition(position, userRole);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.PositionService.removeUserRole(dk.digitalidentity.rc.dao.model.Position, dk.digitalidentity.rc.dao.model.UserRole)) && args(position, userRole)")
	public void interceptRemoveUserRoleAssignmentOnPosition(Position position, UserRole userRole) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveUserRoleAssignmentOnPosition(position, userRole);
		}
	}

	// OrgUnitService

	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.addRoleGroup(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.RoleGroup, boolean, java.time.LocalDate, java.time.LocalDate)) && args(ou, roleGroup, inherit, startDate, stopDate)")
	public void interceptAddRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, boolean inherit, LocalDate startDate, LocalDate stopDate) {

		// skip assignments that are active in the future (we will get a direct event-call later)
		if (startDate != null && LocalDate.now().isAfter(startDate)) {
			return;
		}

		for (RoleChangeHook hook : hooks) {
			hook.interceptAddRoleGroupAssignmentOnOrgUnit(ou, roleGroup, inherit);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.removeRoleGroup(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.RoleGroup)) && args(ou, roleGroup)")
	public void interceptRemoveRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveRoleGroupAssignmentOnOrgUnit(ou, roleGroup);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.addUserRole(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.UserRole, boolean, java.time.LocalDate, java.time.LocalDate)) && args(ou, userRole, inherit, startDate, stopDate)")
	public void interceptAddUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole, boolean inherit, LocalDate startDate, LocalDate stopDate) {

		// skip assignments that are active in the future (we will get a direct event-call later)
		if (startDate != null && LocalDate.now().isAfter(startDate)) {
			return;
		}

		for (RoleChangeHook hook : hooks) {
			hook.interceptAddUserRoleAssignmentOnOrgUnit(ou, userRole, inherit);
		}
	}

	@Before("execution(* dk.digitalidentity.rc.service.OrgUnitService.removeUserRole(dk.digitalidentity.rc.dao.model.OrgUnit, dk.digitalidentity.rc.dao.model.UserRole)) && args(ou, userRole)")
	public void interceptRemoveUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole) {
		for (RoleChangeHook hook : hooks) {
			hook.interceptRemoveUserRoleAssignmentOnOrgUnit(ou, userRole);
		}
	}
	
	//// RoleGroupService Hooks

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
	
	//// UserRoleService Hooks

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
}
