package dk.digitalidentity.rc.service.assignment.rules;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManagerSubstituteAssignmentRule extends AssignmentRule {

	@Override
	public <C> boolean appliesToAssignment(Class<C> clazz) {
		return OrgUnitAssignment.class.isAssignableFrom(clazz);
	}

	// Position-based evaluation
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> AssignmentAppliesResult applies(T assignment, User user, Position position, OrgUnit orgUnit) {
		return evaluateManagerOrSubstituteAssignment((OrgUnitAssignment) assignment, user, position, orgUnit);
	}

	// Manager/substitute-based evaluation (without position)
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> AssignmentAppliesResult applies(T assignment, User user, OrgUnit managerOfOU, OrgUnit assignmentOU) {
		return evaluateManagerOrSubstituteWithoutPosition((OrgUnitAssignment) assignment, user, managerOfOU, assignmentOU);
	}

	private AssignmentAppliesResult evaluateManagerOrSubstituteAssignment(final OrgUnitAssignment assignment,
		final User user, final Position position, final OrgUnit orgUnit) {
		return validateAssignmentEligibility(position, orgUnit, assignment.isInherit(), true)
			.orElseGet(() -> checkManagerOrSubstituteMatch(assignment, user, position.getOrgUnit()));
	}

	private AssignmentAppliesResult evaluateManagerOrSubstituteWithoutPosition(final OrgUnitAssignment assignment,
		final User user,
		final OrgUnit managerOfOU,
		final OrgUnit assignmentOU) {
		// Check if assignment allows inheritance or is on current OU
		if (!assignment.isInherit() && !managerOfOU.getUuid().equals(assignmentOU.getUuid())) {
			return AssignmentAppliesResult.NOT_APPLICABLE;
		}

		// Check if user is manager/substitute of managerOfOU
		return checkManagerOrSubstituteMatch(assignment, user, managerOfOU);
	}

	private static AssignmentAppliesResult checkManagerOrSubstituteMatch(OrgUnitAssignment assignment, User user, OrgUnit orgUnit) {
		if (isManagerMatch(assignment, user, orgUnit)) {
			return AssignmentAppliesResult.POSITIVE;
		}

		if (isSubstituteMatch(assignment, user, orgUnit)) {
			return AssignmentAppliesResult.POSITIVE;
		}
		return AssignmentAppliesResult.NOT_APPLICABLE;
	}

	private static boolean isManagerMatch(OrgUnitAssignment assignment, User user, OrgUnit orgUnit) {
		return assignment.isManager()
			&& orgUnit.getManager() != null
			&& orgUnit.getManager().getUuid().equalsIgnoreCase(user.getUuid());
	}

	private static boolean isSubstituteMatch(OrgUnitAssignment assignment, User user, OrgUnit orgUnit) {
		if (!assignment.isSubstitutes() || orgUnit.getManager() == null) {
			return false;
		}

		final String managerUuid = orgUnit.getManager().getUuid();
		return user.getSubstituteFor().stream()
			.anyMatch(sub -> sub.getOrgUnit().getUuid().equalsIgnoreCase(orgUnit.getUuid())
				&& sub.getManager().getUuid().equalsIgnoreCase(managerUuid));
	}
}
