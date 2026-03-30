package dk.digitalidentity.rc.service.assignment.rule;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserOUFunction;
import dk.digitalidentity.rc.service.assignment.rules.AssignmentRule;
import dk.digitalidentity.rc.service.assignment.rules.AssignmentRule.AssignmentAppliesResult;
import dk.digitalidentity.rc.service.assignment.rules.AssignmentRuleEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createFunction;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createPosition;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createTitle;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUserOUFunction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssignmentRuleEvaluatorTest {

	@Mock
	private AssignmentRule rule1;

	@Mock
	private AssignmentRule rule2;

	@Mock
	private AssignmentRule rule3;

	private AssignmentRuleEvaluator evaluator;

	private User testUser;
	private OrgUnit testOrgUnit;
	private Title testTitle;
	private Position testPosition;
	private OrgUnitUserRoleAssignment testAssignment;

	@BeforeEach
	void setUp() {
		testUser = createUser("test-user-uuid");
		testOrgUnit = createOrgUnit("org-unit-uuid", null);
		testTitle = createTitle("test-title-uuid");
		testPosition = createPosition(testOrgUnit, testTitle, testUser, false);
		testAssignment = new OrgUnitUserRoleAssignment();
		testAssignment.setOrgUnit(testOrgUnit);
	}

	@Nested
	@DisplayName("Position-based evaluation")
	class PositionBasedEvaluationTests {

		@Test
		@DisplayName("Should return NOT_APPLICABLE when no rules apply to assignment type")
		void shouldReturnNotApplicableWhenNoRulesApply() {
			// Arrange
			given(rule1.appliesToAssignment(OrgUnitUserRoleAssignment.class)).willReturn(false);
			given(rule2.appliesToAssignment(OrgUnitUserRoleAssignment.class)).willReturn(false);
			evaluator = new AssignmentRuleEvaluator(List.of(rule1, rule2));

			// Act
			AssignmentAppliesResult result = evaluator.applies(testAssignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Should return POSITIVE when at least one rule returns POSITIVE and none return NEGATIVE")
		void shouldReturnPositiveWhenAtLeastOneRuleReturnsPositive() {
			// Arrange
			given(rule1.appliesToAssignment(OrgUnitUserRoleAssignment.class)).willReturn(true);
			given(rule1.applies(testAssignment, testUser, testPosition, testOrgUnit)).willReturn(AssignmentAppliesResult.NOT_APPLICABLE);
			given(rule2.appliesToAssignment(OrgUnitUserRoleAssignment.class)).willReturn(true);
			given(rule2.applies(testAssignment, testUser, testPosition, testOrgUnit)).willReturn(AssignmentAppliesResult.POSITIVE);
			evaluator = new AssignmentRuleEvaluator(List.of(rule1, rule2));

			// Act
			AssignmentAppliesResult result = evaluator.applies(testAssignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
		}

		@Test
		@DisplayName("Should return NEGATIVE immediately when any rule returns NEGATIVE")
		void shouldReturnNegativeImmediatelyWhenAnyRuleReturnsNegative() {
			// Arrange
			given(rule1.appliesToAssignment(OrgUnitUserRoleAssignment.class)).willReturn(true);
			given(rule1.applies(testAssignment, testUser, testPosition, testOrgUnit)).willReturn(AssignmentAppliesResult.NEGATIVE);
			evaluator = new AssignmentRuleEvaluator(List.of(rule1, rule2, rule3));

			// Act
			AssignmentAppliesResult result = evaluator.applies(testAssignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NEGATIVE);
		}

		@Test
		@DisplayName("Should short-circuit on NEGATIVE even if later rule would return POSITIVE")
		void shouldShortCircuitOnNegativeEvenIfLaterRuleWouldBePositive() {
			// Arrange
			given(rule1.appliesToAssignment(OrgUnitUserRoleAssignment.class)).willReturn(true);
			given(rule1.applies(testAssignment, testUser, testPosition, testOrgUnit)).willReturn(AssignmentAppliesResult.NEGATIVE);
			evaluator = new AssignmentRuleEvaluator(List.of(rule1, rule2));

			// Act
			AssignmentAppliesResult result = evaluator.applies(testAssignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NEGATIVE);
			verify(rule2, never()).applies(any(), any(), any(Position.class), any(OrgUnit.class));
		}

		@Test
		@DisplayName("Should skip rules that do not apply to assignment type")
		void shouldSkipRulesThatDoNotApplyToAssignmentType() {
			// Arrange
			given(rule1.appliesToAssignment(OrgUnitUserRoleAssignment.class)).willReturn(false);
			given(rule2.appliesToAssignment(OrgUnitUserRoleAssignment.class)).willReturn(true);
			given(rule2.applies(testAssignment, testUser, testPosition, testOrgUnit)).willReturn(AssignmentAppliesResult.POSITIVE);
			evaluator = new AssignmentRuleEvaluator(List.of(rule1, rule2));

			// Act
			AssignmentAppliesResult result = evaluator.applies(testAssignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
			verify(rule1, never()).applies(any(), any(), any(Position.class), any(OrgUnit.class));
		}

		@Test
		@DisplayName("Should return POSITIVE if all applicable rules return POSITIVE")
		void shouldReturnPositiveIfAllApplicableRulesReturnPositive() {
			// Arrange
			given(rule1.appliesToAssignment(OrgUnitUserRoleAssignment.class)).willReturn(true);
			given(rule1.applies(testAssignment, testUser, testPosition, testOrgUnit)).willReturn(AssignmentAppliesResult.POSITIVE);
			given(rule2.appliesToAssignment(OrgUnitUserRoleAssignment.class)).willReturn(true);
			given(rule2.applies(testAssignment, testUser, testPosition, testOrgUnit)).willReturn(AssignmentAppliesResult.POSITIVE);
			evaluator = new AssignmentRuleEvaluator(List.of(rule1, rule2));

			// Act
			AssignmentAppliesResult result = evaluator.applies(testAssignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
		}
	}

	@Nested
	@DisplayName("Function-based evaluation")
	class FunctionBasedEvaluationTests {

		private UserOUFunction testFunctionAssignment;

		@BeforeEach
		void setUpFunction() {
			testFunctionAssignment = createUserOUFunction(testUser, testOrgUnit, createFunction("test-function-uuid"));
		}

		@Test
		@DisplayName("Should evaluate rules with UserOUFunction context")
		void shouldEvaluateRulesWithFunctionAssignmentContext() {
			// Arrange
			given(rule1.appliesToAssignment(OrgUnitUserRoleAssignment.class)).willReturn(true);
			given(rule1.applies(eq(testAssignment), eq(testUser), eq(testFunctionAssignment), eq(testOrgUnit)))
				.willReturn(AssignmentAppliesResult.POSITIVE);
			evaluator = new AssignmentRuleEvaluator(List.of(rule1));

			// Act
			AssignmentAppliesResult result = evaluator.applies(testAssignment, testUser, testFunctionAssignment, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
			verify(rule1).applies(testAssignment, testUser, testFunctionAssignment, testOrgUnit);
		}

		@Test
		@DisplayName("Should return NEGATIVE immediately on first NEGATIVE with function context")
		void shouldReturnNegativeImmediatelyOnFirstNegative() {
			// Arrange
			given(rule1.appliesToAssignment(OrgUnitUserRoleAssignment.class)).willReturn(true);
			given(rule1.applies(testAssignment, testUser, testFunctionAssignment, testOrgUnit))
				.willReturn(AssignmentAppliesResult.NEGATIVE);
			evaluator = new AssignmentRuleEvaluator(List.of(rule1, rule2));

			// Act
			AssignmentAppliesResult result = evaluator.applies(testAssignment, testUser, testFunctionAssignment, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NEGATIVE);
			verify(rule2, never()).applies(any(), any(), any(UserOUFunction.class), any(OrgUnit.class));
		}
	}

	@Nested
	@DisplayName("Manager-based evaluation")
	class ManagerBasedEvaluationTests {

		private OrgUnit managerOrgUnit;
		private OrgUnit assignmentOrgUnit;

		@BeforeEach
		void setUpManagerContext() {
			managerOrgUnit = createOrgUnit("manager-org-unit-uuid", null);
			assignmentOrgUnit = createOrgUnit("assignment-org-unit-uuid", null);
		}

		@Test
		@DisplayName("Should evaluate rules with manager OrgUnit context")
		void shouldEvaluateRulesWithManagerOrgUnitContext() {
			// Arrange
			given(rule1.appliesToAssignment(OrgUnitUserRoleAssignment.class)).willReturn(true);
			given(rule1.applies(eq(testAssignment), eq(testUser), eq(managerOrgUnit), eq(assignmentOrgUnit)))
				.willReturn(AssignmentAppliesResult.POSITIVE);
			evaluator = new AssignmentRuleEvaluator(List.of(rule1));

			// Act
			AssignmentAppliesResult result = evaluator.applies(testAssignment, testUser, managerOrgUnit, assignmentOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
			verify(rule1).applies(testAssignment, testUser, managerOrgUnit, assignmentOrgUnit);
		}

		@Test
		@DisplayName("Should return NEGATIVE immediately on first NEGATIVE with manager context")
		void shouldReturnNegativeImmediatelyOnFirstNegative() {
			// Arrange
			given(rule1.appliesToAssignment(OrgUnitUserRoleAssignment.class)).willReturn(true);
			given(rule1.applies(testAssignment, testUser, managerOrgUnit, assignmentOrgUnit))
				.willReturn(AssignmentAppliesResult.NEGATIVE);
			evaluator = new AssignmentRuleEvaluator(List.of(rule1, rule2));

			// Act
			AssignmentAppliesResult result = evaluator.applies(testAssignment, testUser, managerOrgUnit, assignmentOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NEGATIVE);
			verify(rule2, never()).applies(any(), any(), eq(managerOrgUnit), eq(assignmentOrgUnit));
		}
	}
}
