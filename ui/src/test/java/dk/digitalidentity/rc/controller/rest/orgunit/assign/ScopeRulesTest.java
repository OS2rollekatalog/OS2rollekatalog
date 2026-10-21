package dk.digitalidentity.rc.controller.rest.orgunit.assign;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules.AssignmentException;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules.AssignmentTarget;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules.InheritConstraint;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules.ScopeChoice;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules.ScopeRule;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScopeRules")
class ScopeRulesTest {

	/**
	 * The authoritative truth table from OU_ASSIGNMENT_WIZARD_NOTES.md - all 32
	 * (target, exception, inherit) triples, of which exactly 13 are valid.
	 */
	@Nested
	@DisplayName("validated")
	class Validated {

		@ParameterizedTest(name = "{0} + {1} + inherit={2} -> valid={3}")
		@CsvSource({
			"EVERYONE, NONE,   true,  true",
			"EVERYONE, NONE,   false, true",
			"EVERYONE, TITLES, true,  true",
			"EVERYONE, TITLES, false, true",
			"EVERYONE, OUS,    true,  true",
			"EVERYONE, OUS,    false, false",
			"EVERYONE, USERS,  true,  false",
			"EVERYONE, USERS,  false, true",
			"TITLES,   NONE,   true,  true",
			"TITLES,   NONE,   false, true",
			"TITLES,   TITLES, true,  false",
			"TITLES,   TITLES, false, false",
			"TITLES,   OUS,    true,  false",
			"TITLES,   OUS,    false, false",
			"TITLES,   USERS,  true,  false",
			"TITLES,   USERS,  false, true",
			"FUNCTION, NONE,   true,  true",
			"FUNCTION, NONE,   false, true",
			"FUNCTION, TITLES, true,  false",
			"FUNCTION, TITLES, false, false",
			"FUNCTION, OUS,    true,  false",
			"FUNCTION, OUS,    false, false",
			"FUNCTION, USERS,  true,  false",
			"FUNCTION, USERS,  false, false",
			"MANAGER,  NONE,   true,  true",
			"MANAGER,  NONE,   false, true",
			"MANAGER,  TITLES, true,  false",
			"MANAGER,  TITLES, false, false",
			"MANAGER,  OUS,    true,  false",
			"MANAGER,  OUS,    false, false",
			"MANAGER,  USERS,  true,  false",
			"MANAGER,  USERS,  false, false",
		})
		@DisplayName("accepts exactly the combinations the business rules allow")
		void matchesTruthTable(AssignmentTarget target, AssignmentException exception, boolean inherit, boolean expectedValid) {
			// ---- Given ---- //
			ScopeChoice choice = new ScopeChoice(target, exception, inherit);

			// ---- When ---- //
			Optional<ScopeChoice> result = ScopeRules.validated(choice);

			// ---- Then ---- //
			if (expectedValid) {
				assertThat(result).contains(choice);
			} else {
				assertThat(result).isEmpty();
			}
		}
	}

	/** The client enables its radios from this map, so its shape is part of the contract. */
	@Nested
	@DisplayName("constraintsByTarget")
	class ConstraintsByTarget {

		@Test
		@DisplayName("every target the rules allow is listed")
		void listsEveryTarget() {
			// ---- When ---- //
			Map<AssignmentTarget, Map<AssignmentException, InheritConstraint>> constraints = ScopeRules.constraintsByTarget();

			// ---- Then ---- //
			assertThat(constraints).containsOnlyKeys(AssignmentTarget.EVERYONE, AssignmentTarget.TITLES,
				AssignmentTarget.MANAGER, AssignmentTarget.FUNCTION);
		}

		@Test
		@DisplayName("a target lists exactly the exceptions it allows, with their inherit constraints")
		void listsExceptionsWithConstraints() {
			// ---- When ---- //
			Map<AssignmentTarget, Map<AssignmentException, InheritConstraint>> constraints = ScopeRules.constraintsByTarget();

			// ---- Then ---- //
			assertThat(constraints.get(AssignmentTarget.EVERYONE)).containsExactlyInAnyOrderEntriesOf(Map.of(
				AssignmentException.NONE, InheritConstraint.FREE,
				AssignmentException.TITLES, InheritConstraint.FREE,
				AssignmentException.OUS, InheritConstraint.ALWAYS,
				AssignmentException.USERS, InheritConstraint.NEVER));
		}

		@Test
		@DisplayName("a target allowing one exception lists only that one")
		void listsOnlyAllowedExceptions() {
			// ---- When ---- //
			Map<AssignmentTarget, Map<AssignmentException, InheritConstraint>> constraints = ScopeRules.constraintsByTarget();

			// ---- Then ---- //
			assertThat(constraints.get(AssignmentTarget.MANAGER))
				.containsExactlyInAnyOrderEntriesOf(Map.of(AssignmentException.NONE, InheritConstraint.FREE));
		}

		@Test
		@DisplayName("every listed constraint agrees with the rule the choice resolves to")
		void agreesWithRuleFor() {
			// ---- Given ---- //
			Map<AssignmentTarget, Map<AssignmentException, InheritConstraint>> constraints = ScopeRules.constraintsByTarget();

			// ---- When / Then ---- //
			constraints.forEach((target, byException) -> byException.forEach((exception, constraint) ->
				assertThat(ScopeRules.ruleFor(new ScopeChoice(target, exception, constraint.appliedTo(true))))
					.map(ScopeRule::inherit)
					.contains(constraint)));
		}
	}
}
