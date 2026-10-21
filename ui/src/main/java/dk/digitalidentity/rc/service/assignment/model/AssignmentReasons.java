package dk.digitalidentity.rc.service.assignment.model;

import java.util.Map;
import java.util.Optional;

import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;

public record AssignmentReasons(Map<Long, String> byUserRoleAssignmentId, Map<Long, String> byRoleGroupAssignmentId) {

	public static final AssignmentReasons NONE = new AssignmentReasons(Map.of(), Map.of());

	public Optional<String> reasonFor(CurrentAssignment currentAssignment) {
		return switch (AssignmentType.from(currentAssignment)) {
			case USER_USER_ROLE -> Optional.ofNullable(byUserRoleAssignmentId.get(currentAssignment.getAssignmentId()));
			case USER_ROLE_GROUP -> Optional.ofNullable(byRoleGroupAssignmentId.get(currentAssignment.getAssignmentId()));
			case OU_USER_ROLE, OU_ROLE_GROUP -> Optional.empty();
		};
	}
}
