package dk.digitalidentity.rc.service.assignment.rules;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserOUFunction;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;

import java.util.Optional;

public abstract class AssignmentRule {

	public enum AssignmentAppliesResult {
		POSITIVE,
		NEGATIVE, // Was actively not selected for this assignment
		NOT_APPLICABLE
	}

	public abstract <C> boolean appliesToAssignment(Class<C> clazz);

	// Position-based evaluation
	public abstract <T> AssignmentAppliesResult applies(T assignment, User user, Position position, OrgUnit orgUnit);

	// Function-based evaluation
	public <T> AssignmentAppliesResult applies(T assignment, User user, UserOUFunction functionAssignment, OrgUnit orgUnit) {
		return AssignmentAppliesResult.NOT_APPLICABLE;
	}

	// Manager/substitute-based evaluation
	// managerOfOU: The OU where the user is manager/substitute
	// assignmentOU: The OU where the assignment is located
	public <T> AssignmentAppliesResult applies(T assignment, User user, OrgUnit managerOfOU, OrgUnit assignmentOU) {
		return AssignmentAppliesResult.NOT_APPLICABLE;
	}

	/**
	 * Validates basic eligibility criteria for assignment application.
	 * Checks if the position allows inheritance and, when not inheriting,
	 * verifies that the position belongs to the current organizational unit.
	 *
	 * @return an Optional containing the negative or not-applicable result if validation fails,
	 *         or empty if the position passes all eligibility checks
	 */
	Optional<AssignmentAppliesResult> validateAssignmentEligibility(final Position position, final OrgUnit orgUnit, final boolean inherit, final boolean isManagerOrFunctionAssignment) {
		if (isManagerOrFunctionAssignment) {
			if (position != null && position.isDoNotInherit()) {
				return Optional.of(AssignmentAppliesResult.NEGATIVE);
			}
			if (!inherit && !isCurrentOu(position, orgUnit)) {
				return Optional.of(AssignmentAppliesResult.NOT_APPLICABLE);
			}
			return Optional.empty();
		}
		// The code above actually handles what happens when position is null.
		// This check is just for null safety and shouldn't realistically happen in production
		if (position == null) {
			return Optional.of(AssignmentAppliesResult.NOT_APPLICABLE);
		}
		if (position.isDoNotInherit()) {
			return Optional.of(AssignmentAppliesResult.NEGATIVE);
		}
		if (!inherit && !isCurrentOu(position, orgUnit)) {
			return Optional.of(AssignmentAppliesResult.NOT_APPLICABLE);
		}
		return Optional.empty();
	}

	/**
	 * True when the assignment carries a condition that in itself decides who gets the role
	 * (titler, funktioner, leder/stedfortræder).
	 * <p>
	 * Undtagelses-reglerne (undtagne brugere og undtagne enheder) må kun fravælge, aldrig tildele,
	 * når en sådan betingelse er sat. Ellers ville fx "tildelt på titel med undtagne brugere" give
	 * rollen til alle i enheden, der ikke står på undtagelseslisten - uanset titel - fordi
	 * {@link AssignmentRuleEvaluator} tager POSITIVE fra en vilkårlig regel som et ja.
	 *
	 * @return true hvis tildelingen selv udpeger modtagerne, false hvis den gælder alle i enheden
	 */
	static boolean hasGrantingCondition(final OrgUnitAssignment assignment) {
		if (assignment == null) {
			return false;
		}
		return (assignment.getContainsTitles() != null && assignment.getContainsTitles() != ContainsTitles.NO)
			|| assignment.isContainsFunctions()
			|| assignment.isManager()
			|| assignment.isSubstitutes();
	}

	static boolean isCurrentOu(final Position position, final OrgUnit orgUnit) {
		if (orgUnit == null) {
			return false;
		}
		return position.getOrgUnit().getUuid().equalsIgnoreCase(orgUnit.getUuid());
	}
}
