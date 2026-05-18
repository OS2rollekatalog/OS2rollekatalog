package dk.digitalidentity.rc.service.assignment.rules;

import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserOUFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
		if (!isOrgUnitInAssignmentScope(functionAssignment.getOrgUnit(), orgUnit, assignment.isInherit())) {
			return AssignmentAppliesResult.NOT_APPLICABLE;
		}
		if (assignment.getFunctions() != null && assignment.getFunctions().stream()
			.anyMatch(function -> function.getUuid().equalsIgnoreCase(functionAssignment.getFunction().getUuid()))) {
			return AssignmentAppliesResult.POSITIVE;
		}
		return AssignmentAppliesResult.NOT_APPLICABLE;
	}

	/**
	 * Evaluates whether the assignment's required functions match any of the user's
	 * function-tildelinger. {@link UserOUFunction} is scoped directly to (User, OrgUnit),
	 * so this iterates the user's function assignments and accepts a match when the
	 * tildeling's OU is in scope of the role assignment: the assignment's own OU for
	 * non-inherit, or the assignment's OU or any descendant for inherit.
	 */
	private AssignmentAppliesResult evaluateFunctionMatch(OrgUnitAssignment assignment, User user, OrgUnit assignmentOu) {
		if (!assignment.isContainsFunctions()) {
			return AssignmentAppliesResult.NOT_APPLICABLE;
		}
		if (user.getFunctionAssignments() == null || assignment.getFunctions() == null) {
			return AssignmentAppliesResult.NOT_APPLICABLE;
		}
		final Set<String> requiredFunctionUuids = assignment.getFunctions().stream()
			.map(Function::getUuid)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());

		for (UserOUFunction fa : user.getFunctionAssignments()) {
			if (fa.getOrgUnit() == null || fa.getFunction() == null) {
				continue;
			}
			if (!requiredFunctionUuids.contains(fa.getFunction().getUuid())) {
				continue;
			}
			if (isOrgUnitInAssignmentScope(fa.getOrgUnit(), assignmentOu, assignment.isInherit())) {
				return AssignmentAppliesResult.POSITIVE;
			}
		}
		return AssignmentAppliesResult.NOT_APPLICABLE;
	}

	/**
	 * Returns true when {@code functionOu} is the same as the assignment's OU, or — when
	 * inheritance is enabled — a descendant of it. Walking up from the function's OU
	 * keeps the scope strictly bounded by the assignment's subtree.
	 */
	private static boolean isOrgUnitInAssignmentScope(OrgUnit functionOu, OrgUnit assignmentOu, boolean inherit) {
		if (assignmentOu == null || functionOu == null) {
			return false;
		}
		if (functionOu.getUuid().equalsIgnoreCase(assignmentOu.getUuid())) {
			return true;
		}
		if (!inherit) {
			return false;
		}
		OrgUnit current = functionOu.getParent();
		while (current != null) {
			if (current.getUuid().equalsIgnoreCase(assignmentOu.getUuid())) {
				return true;
			}
			current = current.getParent();
		}
		return false;
	}

}
