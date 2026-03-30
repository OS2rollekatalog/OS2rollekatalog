package dk.digitalidentity.rc.service.assignment.rules;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles org-unit assignments with title conditions, both positive and negative.
 */
@Service
public class TitleAssignmentRule extends AssignmentRule {

	@Override
	public <C> boolean appliesToAssignment(Class<C> clazz) {
		return OrgUnitAssignment.class.isAssignableFrom(clazz);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> AssignmentAppliesResult applies(final T assignment, final User user, final Position position, final OrgUnit orgUnit) {
		return evaluateTitleAssignment((OrgUnitAssignment) assignment, position, orgUnit);
	}

	/**
	 * Evaluates whether a title-based assignment applies to a position.
	 *
	 * @return the result indicating if and how the assignment applies
	 */
	private AssignmentAppliesResult evaluateTitleAssignment(final OrgUnitAssignment assignment,
															final Position position, final OrgUnit orgUnit) {
		return validateAssignmentEligibility(position, orgUnit, assignment != null && assignment.isInherit(), false)
			.orElseGet(() -> evaluateTitleMatch(assignment, position));
	}

	/**
	 * Evaluates if the position's title matches the assignment's title conditions.
	 *
	 * @return the result based on whether the title matches positive/negative conditions
	 */
	private static AssignmentAppliesResult evaluateTitleMatch(OrgUnitAssignment assignment, Position position) {
		if (!hasTitleConditions(assignment)) {
			return AssignmentAppliesResult.NOT_APPLICABLE;
		}
		return checkTitleAgainstConditions(assignment, position.getTitle());
	}

	/**
	 * Checks if an assignment has any title conditions configured.
	 *
	 * @return true if the assignment has title conditions, false otherwise
	 */
	private static boolean hasTitleConditions(OrgUnitAssignment assignment) {
		return assignment != null && assignment.getContainsTitles() != null
			&& assignment.getContainsTitles() != ContainsTitles.NO;
	}

	/**
	 * Checks a title against the assignment's positive or negative title conditions.
	 *
	 * @return POSITIVE if title matches positive conditions, NEGATIVE if matches negative conditions,
	 *         for NEGATIVE assignments: POSITIVE if title is not in the exception list (user is not excepted),
	 *         NOT_APPLICABLE if no match or no valid conditions exist
	 */
	private static AssignmentAppliesResult checkTitleAgainstConditions(final OrgUnitAssignment assignment, final Title title) {
		boolean isNegative = assignment.getContainsTitles() == ContainsTitles.NEGATIVE;

		if (isNegative) {
			return handleNegativeTitleAssignment(assignment, title);
		} else {
			return handlePositiveTitleAssignment(assignment, title);
		}
	}

	/**
	 * Returns POSITIVE for everyone, except those with a title contained in the assignments excepted titles
	 */
	private static AssignmentAppliesResult handleNegativeTitleAssignment(final OrgUnitAssignment assignment, final Title title) {
		// Assignment is for everyone except some titles
		if (assignment.getTitles() == null || title == null) {
			// If there is no title or there is no excluded titles, everybody gets the role
			return AssignmentAppliesResult.POSITIVE;
		}
		boolean titleMatches = assignment.getTitles().stream()
			.anyMatch(t -> t.getUuid().equalsIgnoreCase(title.getUuid()));
		// If title matches any excepted title, assignment is excluded, otherwise it applies
		return titleMatches ? AssignmentAppliesResult.NEGATIVE : AssignmentAppliesResult.POSITIVE;
	}

	/**
	 * Returns POSITIVE if the title matches one of the assignments included titles, NOT_APPLICABLE otherwise
	 */
	private static AssignmentAppliesResult handlePositiveTitleAssignment(final OrgUnitAssignment assignment, final Title title) {
		// Assignment is for specific titles
		if (assignment.getTitles() == null || title == null) {
			// If there is no title or there is no included titles, this does not apply
			return AssignmentAppliesResult.NOT_APPLICABLE;
		}
		boolean titleMatches = assignment.getTitles().stream()
			.anyMatch(t -> t.getUuid().equalsIgnoreCase(title.getUuid()));
		// if title matches any of the included titles, it applies. Otherwise it does not.
		return titleMatches ? AssignmentAppliesResult.POSITIVE : AssignmentAppliesResult.NOT_APPLICABLE;
	}
}
