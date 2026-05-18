package dk.digitalidentity.rc.service.assignment.rule;

import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserOUFunction;
import dk.digitalidentity.rc.service.assignment.rules.AssignmentRule.AssignmentAppliesResult;
import dk.digitalidentity.rc.service.assignment.rules.FunctionAssignmentRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createFunction;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createFunctionAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createPosition;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createTitle;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUserOUFunction;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FunctionAssignmentRuleTest {

	private FunctionAssignmentRule rule;

	private User testUser;
	private OrgUnit testOrgUnit;
	private Title testTitle;
	private Position testPosition;
	private Function testFunction;

	@BeforeEach
	void setUp() {
		rule = new FunctionAssignmentRule();

		testUser = createUser("test-user-uuid");
		testUser.setFunctionAssignments(new ArrayList<>());
		testOrgUnit = createOrgUnit("org-unit-uuid", null);
		testTitle = createTitle("test-title-uuid");
		testPosition = createPosition(testOrgUnit, testTitle, testUser, false);
		testFunction = createFunction("test-function-uuid");
	}

	@Nested
	@DisplayName("Position-based function matching")
	class PositionBasedFunctionMatchTests {

		@Test
		@DisplayName("Should return NOT_APPLICABLE when containsFunctions is false")
		void shouldReturnNotApplicableWhenContainsFunctionsIsFalse() {
			// Arrange
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(testOrgUnit, List.of(testFunction), false, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Should return POSITIVE when user has matching function in same OrgUnit")
		void shouldReturnPositiveWhenUserHasMatchingFunctionInOrgUnit() {
			// Arrange
			UserOUFunction userFunction = createUserOUFunction(testUser, testOrgUnit, testFunction);
			testUser.setFunctionAssignments(List.of(userFunction));
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(testOrgUnit, List.of(testFunction), true, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
		}

		@Test
		@DisplayName("Should return NOT_APPLICABLE when user has matching function but in different OrgUnit")
		void shouldReturnNotApplicableWhenUserHasFunctionInDifferentOrgUnit() {
			// Arrange
			OrgUnit differentOrgUnit = createOrgUnit("different-org-unit-uuid", null);
			UserOUFunction userFunction = createUserOUFunction(testUser, differentOrgUnit, testFunction);
			testUser.setFunctionAssignments(List.of(userFunction));
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(testOrgUnit, List.of(testFunction), true, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Should return NOT_APPLICABLE when user has no function assignments")
		void shouldReturnNotApplicableWhenUserHasNoFunctions() {
			// Arrange
			testUser.setFunctionAssignments(null);
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(testOrgUnit, List.of(testFunction), true, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Should return NOT_APPLICABLE when user has functions but none match required functions")
		void shouldReturnNotApplicableWhenNoMatchingFunction() {
			// Arrange
			Function differentFunction = createFunction("different-function-uuid");
			UserOUFunction userFunction = createUserOUFunction(testUser, testOrgUnit, differentFunction);
			testUser.setFunctionAssignments(List.of(userFunction));
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(testOrgUnit, List.of(testFunction), true, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Should return POSITIVE when one of multiple required functions matches user's function")
		void shouldReturnPositiveWhenOneOfMultipleFunctionsMatches() {
			// Arrange
			Function function1 = createFunction("function-1-uuid");
			Function function2 = createFunction("function-2-uuid");
			Function function3 = createFunction("function-3-uuid");
			UserOUFunction userFunction = createUserOUFunction(testUser, testOrgUnit, function2);
			testUser.setFunctionAssignments(List.of(userFunction));
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(testOrgUnit, List.of(function1, function2, function3), true, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
		}
	}

	@Nested
	@DisplayName("Inheritance tests")
	class InheritanceTests {

		@Test
		@DisplayName("Should return NOT_APPLICABLE when inherit=false and position is in different OrgUnit")
		void shouldReturnNotApplicableWhenInheritFalseAndDifferentOrgUnit() {
			// Arrange
			OrgUnit parentOrgUnit = createOrgUnit("parent-org-unit-uuid", null);
			OrgUnit childOrgUnit = createOrgUnit("child-org-unit-uuid", parentOrgUnit);
			Position positionInChildUnit = createPosition(childOrgUnit, testTitle, testUser, false);
			UserOUFunction userFunction = createUserOUFunction(testUser, parentOrgUnit, testFunction);
			testUser.setFunctionAssignments(List.of(userFunction));
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(parentOrgUnit, List.of(testFunction), true, false);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, positionInChildUnit, parentOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Should return POSITIVE when inherit=true and user's function is on position's child OU")
		void shouldReturnPositiveWhenInheritTrueAndFunctionOnChildOU() {
			// Arrange — Linette-scenariet: rolle tildelt på parent med inherit, brugerens position
			// og funktion ligger sammen på en sub-OU.
			OrgUnit parentOrgUnit = createOrgUnit("parent-org-unit-uuid", null);
			OrgUnit childOrgUnit = createOrgUnit("child-org-unit-uuid", parentOrgUnit);
			Position positionInChildUnit = createPosition(childOrgUnit, testTitle, testUser, false);
			UserOUFunction userFunction = createUserOUFunction(testUser, childOrgUnit, testFunction);
			testUser.setFunctionAssignments(List.of(userFunction));
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(parentOrgUnit, List.of(testFunction), true, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, positionInChildUnit, parentOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
		}

		@Test
		@DisplayName("Should return POSITIVE when inherit=true and user's function is on intermediate OU between position and assignment")
		void shouldReturnPositiveWhenInheritTrueAndFunctionOnIntermediateOU() {
			// Arrange
			OrgUnit grandparentOrgUnit = createOrgUnit("grandparent-uuid", null);
			OrgUnit parentOrgUnit = createOrgUnit("parent-uuid", grandparentOrgUnit);
			OrgUnit childOrgUnit = createOrgUnit("child-uuid", parentOrgUnit);
			Position positionInChildUnit = createPosition(childOrgUnit, testTitle, testUser, false);
			UserOUFunction userFunction = createUserOUFunction(testUser, parentOrgUnit, testFunction);
			testUser.setFunctionAssignments(List.of(userFunction));
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(grandparentOrgUnit, List.of(testFunction), true, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, positionInChildUnit, grandparentOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
		}

		@Test
		@DisplayName("Should return NOT_APPLICABLE when inherit=true and function is on OU above assignment's OU")
		void shouldReturnNotApplicableWhenFunctionOnOuAboveAssignment() {
			// Arrange — funktion på en OU som er forfader til assignment-OU'en, dvs. uden for inheritance-scope.
			OrgUnit grandparentOrgUnit = createOrgUnit("grandparent-uuid", null);
			OrgUnit parentOrgUnit = createOrgUnit("parent-uuid", grandparentOrgUnit);
			OrgUnit childOrgUnit = createOrgUnit("child-uuid", parentOrgUnit);
			Position positionInChildUnit = createPosition(childOrgUnit, testTitle, testUser, false);
			UserOUFunction userFunction = createUserOUFunction(testUser, grandparentOrgUnit, testFunction);
			testUser.setFunctionAssignments(List.of(userFunction));
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(parentOrgUnit, List.of(testFunction), true, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, positionInChildUnit, parentOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}
	}

	@Nested
	@DisplayName("UserOUFunction-based evaluation tests")
	class FunctionAssignmentBasedEvaluationTests {

		@Test
		@DisplayName("Should return NOT_APPLICABLE when containsFunctions is false")
		void shouldReturnNotApplicableWhenContainsFunctionsIsFalse() {
			// Arrange
			UserOUFunction functionAssignment = createUserOUFunction(testUser, testOrgUnit, testFunction);
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(testOrgUnit, List.of(testFunction), false, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, functionAssignment, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Should return POSITIVE when UserOUFunction's function matches required function")
		void shouldReturnPositiveWhenFunctionMatches() {
			// Arrange
			UserOUFunction functionAssignment = createUserOUFunction(testUser, testOrgUnit, testFunction);
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(testOrgUnit, List.of(testFunction), true, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, functionAssignment, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
		}

		@Test
		@DisplayName("Should return NOT_APPLICABLE when UserOUFunction's function does not match any required function")
		void shouldReturnNotApplicableWhenFunctionDoesNotMatch() {
			// Arrange
			Function differentFunction = createFunction("different-function-uuid");
			UserOUFunction functionAssignment = createUserOUFunction(testUser, testOrgUnit, differentFunction);
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(testOrgUnit, List.of(testFunction), true, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, functionAssignment, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Should return NOT_APPLICABLE when inherit=false and function's OrgUnit differs from assignment's OrgUnit")
		void shouldReturnNotApplicableWhenNoInheritAndDifferentOU() {
			// Arrange
			OrgUnit functionOrgUnit = createOrgUnit("function-org-unit-uuid", null);
			OrgUnit assignmentOrgUnit = createOrgUnit("assignment-org-unit-uuid", null);
			UserOUFunction functionAssignment = createUserOUFunction(testUser, functionOrgUnit, testFunction);
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(assignmentOrgUnit, List.of(testFunction), true, false);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, functionAssignment, assignmentOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Should return POSITIVE when inherit=true and function's OrgUnit is descendant of assignment's OrgUnit")
		void shouldReturnPositiveWhenInheritTrueAndFunctionInDescendantOU() {
			// Arrange — funktionens OU ligger i assignment-OU'ens subtree
			OrgUnit assignmentOrgUnit = createOrgUnit("assignment-org-unit-uuid", null);
			OrgUnit functionOrgUnit = createOrgUnit("function-org-unit-uuid", assignmentOrgUnit);
			UserOUFunction functionAssignment = createUserOUFunction(testUser, functionOrgUnit, testFunction);
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(assignmentOrgUnit, List.of(testFunction), true, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, functionAssignment, assignmentOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
		}

		@Test
		@DisplayName("Should return NOT_APPLICABLE when inherit=true but function's OrgUnit is not in assignment's subtree")
		void shouldReturnNotApplicableWhenInheritTrueButFunctionOutsideSubtree() {
			// Arrange — funktionens OU ligger udenfor assignment-OU'ens subtree (ingen parent-relation)
			OrgUnit functionOrgUnit = createOrgUnit("function-org-unit-uuid", null);
			OrgUnit assignmentOrgUnit = createOrgUnit("assignment-org-unit-uuid", null);
			UserOUFunction functionAssignment = createUserOUFunction(testUser, functionOrgUnit, testFunction);
			OrgUnitUserRoleAssignment assignment = createFunctionAssignment(assignmentOrgUnit, List.of(testFunction), true, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, functionAssignment, assignmentOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}
	}
}
