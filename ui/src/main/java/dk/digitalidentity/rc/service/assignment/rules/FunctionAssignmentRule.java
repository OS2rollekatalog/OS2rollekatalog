package dk.digitalidentity.rc.service.assignment.rules;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserOUFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FunctionAssignmentRule extends AssignmentRule {
	@Override
	public <C> boolean appliesToAssignment(Class<C> clazz) {
		return OrgUnitAssignment.class.isAssignableFrom(clazz);
	}

	/**
	 * Position-based evaluation: Determines whether an assignment applies to a given position within an organizational unit.
	 */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> AssignmentAppliesResult applies(T assignment, User user, Position position, OrgUnit orgUnit) {
		return appliesToAssignment((OrgUnitAssignment) assignment, user, position, orgUnit);
	}

	/**
	 * Function-based evaluation: Determines whether an assignment applies based on a user's function assignment.
	 */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> AssignmentAppliesResult applies(T assignment, User user, UserOUFunction functionAssignment, OrgUnit orgUnit) {
		return appliesToFunctionAssignment((OrgUnitAssignment) assignment, functionAssignment, orgUnit);
	}

	private AssignmentAppliesResult appliesToAssignment(final OrgUnitAssignment assignment,
		final User user, final Position position, final OrgUnit orgUnit) {
		return validateAssignmentEligibility(position, orgUnit, assignment.isInherit(), true)
			.orElseGet(() -> evaluateFunctionMatch(assignment, user, orgUnit));
	}

	private AssignmentAppliesResult appliesToFunctionAssignment(final OrgUnitAssignment assignment,
		final UserOUFunction functionAssignment, final OrgUnit orgUnit) {
		if (!assignment.isContainsFunctions()) {
			return AssignmentAppliesResult.NOT_APPLICABLE;
		}

		// Check if assignment allows inheritance or is on current OU
		if (!assignment.isInherit() && !functionAssignment.getOrgUnit().getUuid().equals(orgUnit.getUuid())) {
			return AssignmentAppliesResult.NOT_APPLICABLE;
		}

		// Check if the function matches
		if (assignment.getFunctions() != null && assignment.getFunctions().stream()
			.anyMatch(function -> function.getUuid().equals(functionAssignment.getFunction().getUuid()))) {
			return AssignmentAppliesResult.POSITIVE;
		}

		return AssignmentAppliesResult.NOT_APPLICABLE;
	}

	/**
	 * Evaluates whether the assignment's required functions match the user's assigned functions
	 * for the specific organizational unit.
	 */
	private AssignmentAppliesResult evaluateFunctionMatch(OrgUnitAssignment assignment, User user, OrgUnit orgUnit) {
		if (!assignment.isContainsFunctions()) {
			return AssignmentAppliesResult.NOT_APPLICABLE;
		}
		final Set<String> functionUuidsFromUser = getFunctionUuidsForUserInOrgUnit(user, orgUnit);
		if (assignment.getFunctions() != null && assignment.getFunctions().stream()
			.anyMatch(function -> functionUuidsFromUser.contains(function.getUuid()))) {
			return AssignmentAppliesResult.POSITIVE;
		}
		return AssignmentAppliesResult.NOT_APPLICABLE;
	}

	/**
	 * Extracts all function UUIDs assigned to a user for a specific organizational unit.
	 */
	private Set<String> getFunctionUuidsForUserInOrgUnit(User user, OrgUnit orgUnit) {
		if (user.getFunctionAssignments() == null) {
			return Collections.emptySet();
		}
		return user.getFunctionAssignments().stream()
			.filter(fa -> fa.getOrgUnit() != null && fa.getOrgUnit().getUuid().equals(orgUnit.getUuid()))
			.map(fa -> fa.getFunction().getUuid())
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
	}
}
