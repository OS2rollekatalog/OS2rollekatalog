package dk.digitalidentity.rc.mockfactory.rolerequest;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;

import java.util.ArrayList;
import java.util.List;

public class MockFactory {
	public static ItSystem createItSystem(String uuid, List<ApprovableBy> approvableBy) {
		return createItSystem(uuid, approvableBy, new ArrayList<>());
	}

	public static ItSystem createItSystem(String uuid, List<ApprovableBy> approvableBy, List<RequestableBy> requesterPermission) {
		ItSystem itSystem = new ItSystem();
		itSystem.setUuid(uuid);
		itSystem.setApproverPermission(approvableBy);
		itSystem.setRequesterPermission(requesterPermission);
		itSystem.setOrgUnitFilterOrgUnits(new ArrayList<>());
		return itSystem;
	}

	public static UserRole createUserRole(String uuid, ItSystem itSystem, List<ApprovableBy> approvableBy) {
		return createUserRole(uuid, itSystem, approvableBy, new ArrayList<>());
	}

	public static UserRole createUserRole(String uuid, ItSystem itSystem, List<ApprovableBy> approvableBy, List<RequestableBy> requesterPermission) {
		UserRole userRole = new UserRole();
		userRole.setUuid(uuid);
		userRole.setItSystem(itSystem);
		userRole.setApproverPermission(approvableBy);
		userRole.setRequesterPermission(requesterPermission);
		userRole.setReadOnly(false);
		userRole.setOrgUnitFilterOrgUnits(new ArrayList<>());
		return userRole;
	}

	public static RoleGroup createRoleGroup(Long id, List<UserRole> userRoles, List<ApprovableBy> approvableBy) {
		return createRoleGroup(id, userRoles, approvableBy, new ArrayList<>());
	}

	public static RoleGroup createRoleGroup(Long id, List<UserRole> userRoles, List<ApprovableBy> approvableBy, List<RequestableBy> requesterPermission) {
		RoleGroup roleGroup = new RoleGroup();
		roleGroup.setId(id);
		roleGroup.setUserRoleAssignments(
			userRoles.stream()
				.map(ur -> {
					var ura = new RoleGroupUserRoleAssignment();
					ura.setUserRole(ur);
					return ura;
				})
				.toList()
		);
		roleGroup.setApproverPermission(approvableBy);
		roleGroup.setRequesterPermission(requesterPermission);
		return roleGroup;
	}

	public static User createUser(String uuid) {
		User user = new User();
		user.setUuid(uuid);
		user.setUserRoleAssignments(new ArrayList<>());
		user.setRoleGroupAssignments(new ArrayList<>());
		user.setPositions(new ArrayList<>());
		return user;
	}

	public static OrgUnit createOrgUnit(String uuid, OrgUnit parent) {
		OrgUnit orgUnit = new OrgUnit();
		orgUnit.setUuid(uuid);
		orgUnit.setParent(parent);
		orgUnit.setUserRoleAssignments(new ArrayList<>());
		orgUnit.setRoleGroupAssignments(new ArrayList<>());
		return orgUnit;
	}

	public static RoleRequest createRoleRequest(User receiver, OrgUnit orgUnit, UserRole userRole) {
		return RoleRequest.builder()
			.receiver(receiver)
			.orgUnit(orgUnit)
			.userRole(userRole)
			.build();
	}

	public static RoleRequest createRoleRequest(User receiver, OrgUnit orgUnit, RoleGroup roleGroup) {
		return RoleRequest.builder()
			.receiver(receiver)
			.orgUnit(orgUnit)
			.roleGroup(roleGroup)
			.build();
	}
}
