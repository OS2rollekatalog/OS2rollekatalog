package dk.digitalidentity.rc.interceptor;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.dmp.DMPService;

@Component
public class DMPUpdaterHook implements RoleChangeHook {
	private boolean enabled;
	
	@Autowired
	private DMPService dmpService;
	
	public DMPUpdaterHook(@Autowired RoleCatalogueConfiguration config) {
		enabled = config.getIntegrations().getDmp().isEnabled();
	}

	@Override
	public void interceptActivateUser(User user) {
		if (enabled) {
			dmpService.queueUser(user);
		}
	}

	@Override
	public void interceptAddRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		if (enabled && roleGroup.getUserRoleAssignments().stream().anyMatch(ura -> Objects.equals(ura.getUserRole().getItSystem().getIdentifier(), DMPService.DMP_IT_SYSTEM_IDENTIFIER))) {
			dmpService.queueUser(user);
		}
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		if (enabled && roleGroup.getUserRoleAssignments().stream().anyMatch(ura -> Objects.equals(ura.getUserRole().getItSystem().getIdentifier(), DMPService.DMP_IT_SYSTEM_IDENTIFIER))) {
			dmpService.queueUser(user);
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnUser(User user, UserRole userRole) {
		if (enabled && Objects.equals(userRole.getItSystem().getIdentifier(), DMPService.DMP_IT_SYSTEM_IDENTIFIER)) {
			dmpService.queueUser(user);
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnUser(User user, UserRole userRole) {
		if (enabled && Objects.equals(userRole.getItSystem().getIdentifier(), DMPService.DMP_IT_SYSTEM_IDENTIFIER)) {
			dmpService.queueUser(user);
		}
	}

	@Override
	public void interceptAddPositionOnUser(User user, Position position) {
		if (enabled) {
			dmpService.queueUser(user);
		}
	}

	@Override
	public void interceptRemovePositionOnUser(User user, Position position) {
		if (enabled) {
			dmpService.queueUser(user);
		}
	}

	@Override
	public void interceptAddRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, boolean inherit) {
		if (enabled && roleGroup.getUserRoleAssignments().stream().anyMatch(ura -> Objects.equals(ura.getUserRole().getItSystem().getIdentifier(), DMPService.DMP_IT_SYSTEM_IDENTIFIER))) {
			dmpService.queueOrgUnit(ou, inherit);
		}
	}

	// TODO: vi mangler oplysninger om hvorvidt denne er med nedarvning... dvs der først ryddes op ved fuld sync om natten...
	//       men mere generelt er det et issue fx i AD integrationen, som ikke har et fuldt-sync system
	@Override
	public void interceptRemoveRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup) {
		if (enabled && roleGroup.getUserRoleAssignments().stream().anyMatch(ura -> Objects.equals(ura.getUserRole().getItSystem().getIdentifier(), DMPService.DMP_IT_SYSTEM_IDENTIFIER))) {
			dmpService.queueOrgUnit(ou, false);
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole, boolean inherit) {
		if (enabled && Objects.equals(userRole.getItSystem().getIdentifier(), DMPService.DMP_IT_SYSTEM_IDENTIFIER)) {
			dmpService.queueOrgUnit(ou, inherit);
		}
	}

	// TODO: vi mangler oplysninger om hvorvidt denne er med nedarvning... dvs der først ryddes op ved fuld sync om natten...
	//       men mere generelt er det et issue fx i AD integrationen, som ikke har et fuldt-sync system
	@Override
	public void interceptRemoveUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole) {
		if (enabled && Objects.equals(userRole.getItSystem().getIdentifier(), DMPService.DMP_IT_SYSTEM_IDENTIFIER)) {
			dmpService.queueOrgUnit(ou, false);
		}
	}

	// TODO: vi mangler oplysninger om hvorvidt denne er med nedarvning... dvs der først ryddes op ved fuld sync om natten...
	//       men mere generelt er det et issue fx i AD integrationen, som ikke har et fuldt-sync system
	@Override
	public void interceptEditUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole) {
		if (enabled && Objects.equals(userRole.getItSystem().getIdentifier(), DMPService.DMP_IT_SYSTEM_IDENTIFIER)) {
			dmpService.queueOrgUnit(ou, false);
		}
	}

	// TODO: vi mangler oplysninger om hvorvidt denne er med nedarvning... dvs der først ryddes op ved fuld sync om natten...
	//       men mere generelt er det et issue fx i AD integrationen, som ikke har et fuldt-sync system
	@Override
	public void interceptEditRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup) {
		if (enabled && roleGroup.getUserRoleAssignments().stream().anyMatch(ura -> Objects.equals(ura.getUserRole().getItSystem().getIdentifier(), DMPService.DMP_IT_SYSTEM_IDENTIFIER))) {
			dmpService.queueOrgUnit(ou, false);
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		if (enabled && Objects.equals(userRole.getItSystem().getIdentifier(), DMPService.DMP_IT_SYSTEM_IDENTIFIER)) {
			dmpService.queueUserRole(userRole);
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		if (enabled && Objects.equals(userRole.getItSystem().getIdentifier(), DMPService.DMP_IT_SYSTEM_IDENTIFIER)) {
			dmpService.queueUserRole(userRole);
		}
	}

	@Override
	public void interceptAddSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		if (enabled && Objects.equals(userRole.getItSystem().getIdentifier(), DMPService.DMP_IT_SYSTEM_IDENTIFIER)) {
			dmpService.queueUserRole(userRole);
		}
	}

	@Override
	public void interceptRemoveSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		if (enabled && Objects.equals(userRole.getItSystem().getIdentifier(), DMPService.DMP_IT_SYSTEM_IDENTIFIER)) {
			dmpService.queueUserRole(userRole);
		}
	}

	@Override
	public void interceptAddRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup) {
		; // dead functionality, we will not support triggers on position assignments for this hook
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup) {
		; // dead functionality, we will not support triggers on position assignments for this hook
	}

	@Override
	public void interceptAddUserRoleAssignmentOnPosition(Position position, UserRole userRole) {
		; // dead functionality, we will not support triggers on position assignments for this hook
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnPosition(Position position, UserRole userRole) {
		; // dead functionality, we will not support triggers on position assignments for this hook
	}
}
