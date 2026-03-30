package dk.digitalidentity.rc.service.assignment.rules;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserOUFunction;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssignmentRuleEvaluator {
	private final List<AssignmentRule> allRules;

	@Transactional(propagation = Propagation.MANDATORY)
	public <T> AssignmentRule.AssignmentAppliesResult applies(T assignment, User user, Position position, OrgUnit orgUnit) {
		AssignmentRule.AssignmentAppliesResult result = AssignmentRule.AssignmentAppliesResult.NOT_APPLICABLE;
		for (AssignmentRule rule : allRules) {
			if (rule.appliesToAssignment(assignment.getClass())) {
				final var currentRuleResult = rule.applies(assignment, user, position, orgUnit);
				if (currentRuleResult == AssignmentRule.AssignmentAppliesResult.NEGATIVE) {
					// Any negative always results in a negative
					return currentRuleResult;
				}
				if (currentRuleResult == AssignmentRule.AssignmentAppliesResult.POSITIVE) {
					result = AssignmentRule.AssignmentAppliesResult.POSITIVE;
				}
			}
		}
		return result;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public <T> AssignmentRule.AssignmentAppliesResult applies(T assignment, User user, UserOUFunction functionAssignment, OrgUnit orgUnit) {
		AssignmentRule.AssignmentAppliesResult result = AssignmentRule.AssignmentAppliesResult.NOT_APPLICABLE;
		for (AssignmentRule rule : allRules) {
			if (rule.appliesToAssignment(assignment.getClass())) {
				final var currentRuleResult = rule.applies(assignment, user, functionAssignment, orgUnit);
				if (currentRuleResult == AssignmentRule.AssignmentAppliesResult.NEGATIVE) {
					// Any negative always results in a negative
					return currentRuleResult;
				}
				if (currentRuleResult == AssignmentRule.AssignmentAppliesResult.POSITIVE) {
					result = AssignmentRule.AssignmentAppliesResult.POSITIVE;
				}
			}
		}
		return result;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public <T> AssignmentRule.AssignmentAppliesResult applies(T assignment, User user, OrgUnit managerOfOU, OrgUnit assignmentOU) {
		AssignmentRule.AssignmentAppliesResult result = AssignmentRule.AssignmentAppliesResult.NOT_APPLICABLE;
		for (AssignmentRule rule : allRules) {
			if (rule.appliesToAssignment(assignment.getClass())) {
				final var currentRuleResult = rule.applies(assignment, user, managerOfOU, assignmentOU);
				if (currentRuleResult == AssignmentRule.AssignmentAppliesResult.NEGATIVE) {
					// Any negative always results in a negative
					return currentRuleResult;
				}
				if (currentRuleResult == AssignmentRule.AssignmentAppliesResult.POSITIVE) {
					result = AssignmentRule.AssignmentAppliesResult.POSITIVE;
				}
			}
		}
		return result;
	}
}
