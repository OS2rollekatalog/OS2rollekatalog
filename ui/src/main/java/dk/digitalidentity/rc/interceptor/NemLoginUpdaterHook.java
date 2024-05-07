package dk.digitalidentity.rc.interceptor;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.nemlogin.NemLoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NemLoginUpdaterHook implements RoleChangeHook {
	private boolean enabled;
	
	@Autowired
	private NemLoginService nemLoginService;
	
	public NemLoginUpdaterHook(@Autowired RoleCatalogueConfiguration config) {
		enabled = config.getIntegrations().getNemLogin().isEnabled();
	}
		
	@Override
	public void interceptAddRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		if (enabled && StringUtils.hasLength(user.getNemloginUuid())) {
			nemLoginService.addUserToQueue(user);
		}
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		if (enabled && StringUtils.hasLength(user.getNemloginUuid())) {
			nemLoginService.addUserToQueue(user);
		}
	}
	
	@Override
	public void interceptActivateUser(User user) {
		if (enabled && StringUtils.hasLength(user.getNemloginUuid())) {
			nemLoginService.addUserToQueue(user);
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnUser(User user, UserRole userRole) {
		if (enabled && StringUtils.hasLength(user.getNemloginUuid())) {
			nemLoginService.addUserToQueue(user);
		}
	}

	@Override
	public void interceptEditUserRoleAssignmentOnUser(User user, UserUserRoleAssignment assignment) {
		if (enabled && StringUtils.hasLength(user.getNemloginUuid())) {
			nemLoginService.addUserToQueue(user);
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnUser(User user, UserRole userRole) {
		if (enabled && StringUtils.hasLength(user.getNemloginUuid())) {
			nemLoginService.addUserToQueue(user);
		}
	}

	@Override
	public void interceptAddPositionOnUser(User user, Position position) {
		if (enabled && StringUtils.hasLength(user.getNemloginUuid())) {
			System.out.println("uuid = " + user.getUuid());
			nemLoginService.addUserToQueue(user);
		}
	}

	@Override
	public void interceptRemovePositionOnUser(User user, Position position) {
		if (enabled && StringUtils.hasLength(user.getNemloginUuid())) {
			nemLoginService.addUserToQueue(user);
		}
	}

	@Override
	public void interceptAddRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup) {
		if (enabled && StringUtils.hasLength(position.getUser().getNemloginUuid())) {
			nemLoginService.addUserToQueue(position.getUser());
		}
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup) {
		if (enabled && StringUtils.hasLength(position.getUser().getNemloginUuid())) {
			nemLoginService.addUserToQueue(position.getUser());
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnPosition(Position position, UserRole userRole) {
		if (enabled && StringUtils.hasLength(position.getUser().getNemloginUuid())) {
      		nemLoginService.addUserToQueue(position.getUser());
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnPosition(Position position, UserRole userRole) {
		if (enabled && StringUtils.hasLength(position.getUser().getNemloginUuid())) {
			nemLoginService.addUserToQueue(position.getUser());
		}
	}

	@Override
	public void interceptAddRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, boolean inherit) {
		if (enabled && roleGroup.getUserRoleAssignments().stream().anyMatch(u -> u.getUserRole().getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN))) {
			nemLoginService.addOrgUnitToQueue(ou, inherit);
		}
	}
	
	@Override
	public void interceptRemoveRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup) {
		if (enabled && roleGroup.getUserRoleAssignments().stream().anyMatch(u -> u.getUserRole().getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN))) {
			nemLoginService.addOrgUnitToQueue(ou, false);
		}
	}
	
	@Override
	public void interceptAddUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole, boolean inherit) {
		if (enabled && userRole.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN)) {
			nemLoginService.addOrgUnitToQueue(ou, inherit);
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole) {
		if (enabled && userRole.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN)) {
			nemLoginService.addOrgUnitToQueue(ou, false);
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		if (enabled && userRole.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN)) {
			nemLoginService.addRoleGroupToQueue(roleGroup);
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		if (enabled && userRole.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN)) {
			nemLoginService.addRoleGroupToQueue(roleGroup);
		}
	}

	@Override
	public void interceptAddSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		if (enabled && userRole.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN)) {
			nemLoginService.addUserRoleToQueue(userRole);
		}
	}

	@Override
	public void interceptRemoveSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		if (enabled && userRole.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN)) {
			nemLoginService.addUserRoleToQueue(userRole);
		}
	}
	
	@Override
	public void interceptEditUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole) {
		if (enabled && userRole.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN)) {
			nemLoginService.addOrgUnitToQueue(ou, false);
		}
	}

	@Override
	public void interceptEditRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup) {
		if (enabled && roleGroup.getUserRoleAssignments().stream().anyMatch(u -> u.getUserRole().getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN))) {
			nemLoginService.addOrgUnitToQueue(ou, false);
		}
	}
}
