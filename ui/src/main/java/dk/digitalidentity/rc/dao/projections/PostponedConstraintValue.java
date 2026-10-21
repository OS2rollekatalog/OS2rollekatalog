package dk.digitalidentity.rc.dao.projections;

public record PostponedConstraintValue(long assignmentId, long userRoleId, String entityId, String value) {
}
