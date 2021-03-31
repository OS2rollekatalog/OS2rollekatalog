package dk.digitalidentity.rc.interceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.service.OrgUnitService;

@Component
public class ConstrainedAssignerHook implements RoleChangeHook {

	@Autowired
	private AccessConstraintService assignerRoleConstraint;
	
	@Autowired
	private OrgUnitService orgUnitService;

	@Override
	public void interceptAddRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		if (!assignerRoleConstraint.isAssignmentAllowed(user, roleGroup)) {
			throw new SecurityException("user is prohibited from modifying user: " + user.getEntityId());
		}
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		if (!assignerRoleConstraint.isAssignmentAllowed(user, roleGroup)) {
			throw new SecurityException("user is prohibited from modifying user: " + user.getEntityId());
		}		
	}

	@Override
	public void interceptAddUserRoleAssignmentOnUser(User user, UserRole userRole) {
		if (!assignerRoleConstraint.isAssignmentAllowed(user, userRole)) {
			throw new SecurityException("user is prohibited from modifying user: " + user.getEntityId());
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnUser(User user, UserRole userRole) {
		if (!assignerRoleConstraint.isAssignmentAllowed(user, userRole)) {
			throw new SecurityException("user is prohibited from modifying user: " + user.getEntityId());
		}
	}

	@Override
	public void interceptAddRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup) {
		User user = position.getUser();

		if (!assignerRoleConstraint.isAssignmentAllowed(user, roleGroup)) {
			throw new SecurityException("user is prohibited from modifying user: " + user.getEntityId());
		}
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnPosition(Position position, RoleGroup roleGroup) {
		User user = position.getUser();

		if (!assignerRoleConstraint.isAssignmentAllowed(user, roleGroup)) {
			throw new SecurityException("user is prohibited from modifying user: " + user.getEntityId());
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnPosition(Position position, UserRole userRole) {
		User user = position.getUser();

		if (!assignerRoleConstraint.isAssignmentAllowed(user, userRole)) {
			throw new SecurityException("user is prohibited from modifying user: " + user.getEntityId());
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnPosition(Position position, UserRole userRole) {
		User user = position.getUser();

		if (!assignerRoleConstraint.isAssignmentAllowed(user, userRole)) {
			throw new SecurityException("user is prohibited from modifying user: " + user.getEntityId());
		}
	}

	@Override
	public void interceptAddRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, boolean inherit) {
		// if the user is allowed to assign roles on the OU, then we also allow
		// them to assign inherited roles, even though they do not have access to sub-OU's
		if (!assignerRoleConstraint.isAssignmentAllowed(ou, roleGroup)) {
			throw new SecurityException("user is prohibited from modifying ou: " + ou.getEntityId());
		}
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup) {
		if (!assignerRoleConstraint.isAssignmentAllowed(ou, roleGroup)) {
			throw new SecurityException("user is prohibited from modifying ou: " + ou.getEntityId());
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole, boolean inherit) {
		// if the user is allowed to assign roles on the OU, then we also allow
		// them to assign inherited roles, even though they do not have access to sub-OU's
		if (!assignerRoleConstraint.isAssignmentAllowed(ou, userRole)) {
			throw new SecurityException("user is prohibited from modifying ou: " + ou.getEntityId());
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole) {
		if (!assignerRoleConstraint.isAssignmentAllowed(ou, userRole)) {
			throw new SecurityException("user is prohibited from modifying ou: " + ou.getEntityId());
		}
	}

	@Override
	public void interceptAddUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		; // not relevant
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		; // not relevant
	}

	@Override
	public void interceptAddSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		; // not relevant
	}

	@Override
	public void interceptRemoveSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		; // not relevant
	}

	@Override
	public void interceptAddPositionOnUser(User user, Position position) {
		; // not relevant
	}

	@Override
	public void interceptRemovePositionOnUser(User user, Position position) {
		; // not relevant
	}

	@Override
	public void interceptAddRoleGroupAssignmentOnTitle(Title title, RoleGroup roleGroup, String[] ouUuids) {
		for (String uuid : ouUuids) {
			OrgUnit ou = orgUnitService.getByUuid(uuid);
			
			if (!assignerRoleConstraint.isAssignmentAllowed(ou, roleGroup)) {
				throw new SecurityException("user is prohibited from modifying ou: " + ou.getEntityId());
			}
		}
	}

	@Override
	public void interceptRemoveRoleGroupAssignmentOnTitle(Title title, RoleGroup roleGroup, OrgUnit ou) {
		; // we accept removal
	}

	@Override
	public void interceptAddUserRoleAssignmentOnTitle(Title title, UserRole userRole, String[] ouUuids) {
		for (String uuid : ouUuids) {
			OrgUnit ou = orgUnitService.getByUuid(uuid);
			
			if (!assignerRoleConstraint.isAssignmentAllowed(ou, userRole)) {
				throw new SecurityException("user is prohibited from modifying ou: " + ou.getEntityId());
			}
		}
	}

	@Override
	public void interceptRemoveUserRoleAssignmentOnTitle(Title title, UserRole userRole, OrgUnit ou) {
		; // we accept removal		
	}
}
