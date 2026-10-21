package dk.digitalidentity.rc.dao.projections;

import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;

/**
 * A constraint value on a system role assignment.
 * {@code type}, {@code entityId} and {@code value} are null when the assignment has no constraint values.
 */
public record SystemRoleConstraint(long userRoleId, ConstraintValueType type, String entityId, String value) {
}
