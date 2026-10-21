package dk.digitalidentity.rc.rolerequest.service.scope;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.projections.PostponedConstraintValue;
import dk.digitalidentity.rc.dao.projections.SystemRoleConstraint;

/**
 * What one user's OU and it-system scope for a system role is resolved from: the constraints on their
 * roles containing that system role, plus the person facts those constraints refer to.
 */
public record SystemRoleScopeInputs(
	Map<Long, List<SystemRoleConstraint>> constraintsByRole,
	List<PostponedConstraintValue> postponedConstraints,
	Set<String> positionOrgUnitUuids,
	Set<String> managedOrgUnitUuids,
	Map<String, Set<String>> orgUnitUuidsByFunctionUuid
) {

	public static SystemRoleScopeInputs none() {
		return new SystemRoleScopeInputs(Map.of(), List.of(), Set.of(), Set.of(), Map.of());
	}

	public boolean isEmpty() {
		return constraintsByRole.isEmpty();
	}

	public Set<String> functionOrgUnitUuids(final Set<String> functionUuids) {
		return functionUuids.stream()
			.flatMap(functionUuid -> orgUnitUuidsByFunctionUuid.getOrDefault(functionUuid, Set.of()).stream())
			.collect(Collectors.toSet());
	}
}
