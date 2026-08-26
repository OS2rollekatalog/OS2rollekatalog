package dk.digitalidentity.rc.service.assignment.rules;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles org unit assignments with excluded users.
 * <p>
 * This rule is a pure veto: it answers NEGATIVE for an excluded user and NOT_APPLICABLE otherwise.
 * Granting is left to the granting rules, so a title filter cannot be bypassed merely because a
 * user is absent from the exclusion list.
 * <p>
 * The veto covers the position-based evaluation. Manager/substitute and function assignments cannot
 * carry excluded users - the write paths make them mutually exclusive - so those overloads do not
 * participate.
 */
@Service
public class ExcludedUsersRule extends AssignmentRule {
	@Override
	public <C> boolean appliesToAssignment(Class<C> clazz) {
		return OrgUnitAssignment.class.isAssignableFrom(clazz);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> AssignmentAppliesResult applies(T assignment, User user, Position position, OrgUnit orgUnit) {
		return evaluateExcludedUserAssignment((OrgUnitAssignment) assignment, position, orgUnit);
	}

	/**
	 * Evaluates whether an excluded user assignment applies to a position.
	 * In practice excepted-user assignments cannot inherit - that combination is rejected on
	 * every write path - but the scope is read from the assignment rather than assumed.
	 *
	 * @return the result indicating if and how the assignment applies
	 */
	private AssignmentAppliesResult evaluateExcludedUserAssignment(final OrgUnitAssignment assignment, final Position position, final OrgUnit orgUnit) {
		return validateAssignmentEligibility(position, orgUnit, assignment != null && assignment.isInherit(), false)
			.orElseGet(() -> checkUserExclusion(assignment, position));
	}

	/**
	 * Checks if a user is excluded from an assignment.
	 * <p>
	 * The exclusion list is a pure veto: it can deselect a user, never grant. The recipients are
	 * decided by the granting rules, so a title filter cannot be bypassed merely because a user is
	 * absent from the exclusion list.
	 *
	 * @return NEGATIVE if the user is excluded, NOT_APPLICABLE otherwise
	 */
	private static AssignmentAppliesResult checkUserExclusion(OrgUnitAssignment assignment, Position position) {
		if (position == null) {
			return AssignmentAppliesResult.NOT_APPLICABLE;
		}
		if (assignment == null) {
			return AssignmentAppliesResult.NOT_APPLICABLE;
		}
		if (!assignment.isContainsExceptedUsers()) {
			return AssignmentAppliesResult.NOT_APPLICABLE;
		}
		boolean excluded = assignment.getExceptedUsers().stream()
			.anyMatch(u -> u.getUuid().equalsIgnoreCase(position.getUser().getUuid()));
		return excluded ? AssignmentAppliesResult.NEGATIVE : AssignmentAppliesResult.NOT_APPLICABLE;
	}

}
