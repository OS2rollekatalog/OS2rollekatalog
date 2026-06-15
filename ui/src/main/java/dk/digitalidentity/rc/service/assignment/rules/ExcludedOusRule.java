package dk.digitalidentity.rc.service.assignment.rules;

import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles org unit assignments with excluded child OUs.
 * A user is excluded if their position's OU is, or is under, one of the excepted OUs.
 */
@Service
@RequiredArgsConstructor
public class ExcludedOusRule extends AssignmentRule {

	private final OrgUnitDao orgUnitDao;

	@Override
	public <C> boolean appliesToAssignment(Class<C> clazz) {
		return OrgUnitAssignment.class.isAssignableFrom(clazz);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public <T> AssignmentAppliesResult applies(T assignment, User user, Position position, OrgUnit orgUnit) {
		return evaluateExcludedOuAssignment((OrgUnitAssignment) assignment, position, orgUnit);
	}

	private AssignmentAppliesResult evaluateExcludedOuAssignment(final OrgUnitAssignment assignment, final Position position, final OrgUnit orgUnit) {
		return validateAssignmentEligibility(position, orgUnit, true, false)
			.orElseGet(() -> checkOuExclusion(assignment, position));
	}

	private AssignmentAppliesResult checkOuExclusion(final OrgUnitAssignment assignment, final Position position) {
		if (!assignment.isContainsExceptedOus()) {
			return AssignmentAppliesResult.NOT_APPLICABLE;
		}

		List<OrgUnit> exceptedOus = assignment.getExceptedOus();
		if (exceptedOus == null || exceptedOus.isEmpty()) {
			return AssignmentAppliesResult.NOT_APPLICABLE;
		}

		Set<String> exceptedUuids = exceptedOus.stream()
			.map(OrgUnit::getUuid)
			.collect(Collectors.toSet());

		List<String> ancestorUuids = orgUnitDao.findAllAncestorUuids(position.getOrgUnit().getUuid());
		boolean excluded = ancestorUuids.stream().anyMatch(exceptedUuids::contains);

		return excluded ? AssignmentAppliesResult.NEGATIVE : AssignmentAppliesResult.POSITIVE;
	}
}
