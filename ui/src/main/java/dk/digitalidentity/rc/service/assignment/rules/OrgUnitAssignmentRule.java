package dk.digitalidentity.rc.service.assignment.rules;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles plain org unit assignments, without any conditions
 */
@Service
public class OrgUnitAssignmentRule extends AssignmentRule {
	@Override
	public <C> boolean appliesToAssignment(Class<C> clazz) {
		return OrgUnitAssignment.class.isAssignableFrom(clazz);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> AssignmentAppliesResult applies(T assignment, User user, Position position, OrgUnit orgUnit) {
		return evaluateOrgUnitAssignment((OrgUnitAssignment) assignment, position, orgUnit);
	}

	/**
	 * Evaluates whether a basic organizational unit assignment applies to a position.
	 * This rule handles assignments without specific conditions (no title, function, manager,
	 * substitute, or user exclusion requirements).
	 *
	 * @return the result indicating if and how the assignment applies
	 */
	private AssignmentAppliesResult evaluateOrgUnitAssignment(final OrgUnitAssignment assignment,
															  final Position position, final OrgUnit orgUnit) {
		return validateAssignmentEligibility(position, orgUnit, assignment.isInherit(), false)
			.orElseGet(() -> checkUnconditionalAssignment(assignment));
	}

	/**
	 * Checks if the assignment is an unconditional organizational unit assignment.
	 * An unconditional assignment carries no granting condition of its own (titles, functions,
	 * manager or substitutes) and therefore applies to all eligible positions in the org unit.
	 * Excluded users and excluded org units do not make an assignment conditional - they are
	 * vetoes applied on top by {@link ExcludedUsersRule} and {@link ExcludedOusRule}.
	 *
	 * @return POSITIVE if the assignment carries no granting condition, NOT_APPLICABLE if it does
	 */
	private static AssignmentAppliesResult checkUnconditionalAssignment(final OrgUnitAssignment assignment) {
		return assignment.hasGrantingCondition()
			? AssignmentAppliesResult.NOT_APPLICABLE
			: AssignmentAppliesResult.POSITIVE;
	}

}
