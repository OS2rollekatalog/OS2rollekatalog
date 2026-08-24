package dk.digitalidentity.rc.service.assignment.rules;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles org unit assignments with excluded users
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
	 * Excluded user assignments cannot be inherited.
	 *
	 * @return the result indicating if and how the assignment applies
	 */
	private AssignmentAppliesResult evaluateExcludedUserAssignment(final OrgUnitAssignment assignment, final Position position, final OrgUnit orgUnit) {
		return validateAssignmentEligibility(position, orgUnit, false, false) // Excluded users assignments cannot inherit
			.orElseGet(() -> checkUserExclusion(assignment, position));
	}

	/**
	 * Checks if a user is excluded from an assignment.
	 *
	 * @return NEGATIVE if the user is excluded, POSITIVE if not excluded and the exclusion list is
	 *         the only condition on the assignment, NOT_APPLICABLE if the assignment has no user
	 *         exclusions or leaves the actual selection to another condition (titel/funktion/leder)
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
		if (excluded) {
			return AssignmentAppliesResult.NEGATIVE;
		}
		// Kombineret med fx et titelfilter er undtagelseslisten kun et fravalg - den må ikke i sig
		// selv tildele rollen til alle andre i enheden
		return hasGrantingCondition(assignment)
			? AssignmentAppliesResult.NOT_APPLICABLE
			: AssignmentAppliesResult.POSITIVE;
	}

}
