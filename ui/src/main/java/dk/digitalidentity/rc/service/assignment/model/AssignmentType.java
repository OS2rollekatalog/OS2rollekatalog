package dk.digitalidentity.rc.service.assignment.model;

import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;

public enum AssignmentType {
	USER_USER_ROLE,
	USER_ROLE_GROUP,
	OU_USER_ROLE,
	OU_ROLE_GROUP;

	public static AssignmentType from(CurrentAssignment currentAssignment) {
		boolean fromRoleGroup = currentAssignment.getRoleGroup() != null;
		if (currentAssignment.getOrgUnit() == null) {
			return fromRoleGroup ? USER_ROLE_GROUP : USER_USER_ROLE;
		}
		return fromRoleGroup ? OU_ROLE_GROUP : OU_USER_ROLE;
	}
}
