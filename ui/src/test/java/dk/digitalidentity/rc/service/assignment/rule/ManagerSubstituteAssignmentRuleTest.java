package dk.digitalidentity.rc.service.assignment.rule;

import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.assignment.rules.AssignmentRule.AssignmentAppliesResult;
import dk.digitalidentity.rc.service.assignment.rules.ManagerSubstituteAssignmentRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createManagerAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createManagerSubstitute;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnitWithManager;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createPosition;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createTitle;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ManagerSubstituteAssignmentRuleTest {

	private ManagerSubstituteAssignmentRule rule;

	private User testUser;
	private User managerUser;
	private OrgUnit testOrgUnit;
	private Title testTitle;
	private Position testPosition;

	@BeforeEach
	void setUp() {
		rule = new ManagerSubstituteAssignmentRule();

		testUser = createUser("test-user-uuid");
		testUser.setSubstituteFor(new ArrayList<>());
		managerUser = createUser("manager-user-uuid");
		testOrgUnit = createOrgUnitWithManager("org-unit-uuid", null, managerUser);
		testTitle = createTitle("test-title-uuid");
		testPosition = createPosition(testOrgUnit, testTitle, testUser, false);
	}

	@Nested
	@DisplayName("Position-based manager matching")
	class PositionBasedManagerMatchTests {

		@Test
		@DisplayName("Should return POSITIVE when user is manager of OrgUnit and assignment has isManager=true")
		void shouldReturnPositiveWhenUserIsManagerOfOrgUnit() {
			// Arrange
			OrgUnit orgUnitWithTestUserAsManager = createOrgUnitWithManager("org-unit-uuid", null, testUser);
			Position positionInManagedUnit = createPosition(orgUnitWithTestUserAsManager, testTitle, testUser, false);
			OrgUnitUserRoleAssignment assignment = createManagerAssignment(orgUnitWithTestUserAsManager, true, false, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, positionInManagedUnit, orgUnitWithTestUserAsManager);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
		}

		@Test
		@DisplayName("Should return NOT_APPLICABLE when assignment has isManager=false even if user is manager")
		void shouldReturnNotApplicableWhenManagerFlagIsFalse() {
			// Arrange
			OrgUnit orgUnitWithTestUserAsManager = createOrgUnitWithManager("org-unit-uuid", null, testUser);
			Position positionInManagedUnit = createPosition(orgUnitWithTestUserAsManager, testTitle, testUser, false);
			OrgUnitUserRoleAssignment assignment = createManagerAssignment(orgUnitWithTestUserAsManager, false, false, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, positionInManagedUnit, orgUnitWithTestUserAsManager);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Should return NOT_APPLICABLE when OrgUnit has no manager")
		void shouldReturnNotApplicableWhenOrgUnitHasNoManager() {
			// Arrange
			OrgUnit orgUnitWithoutManager = createOrgUnit("org-unit-uuid", null);
			Position positionInUnit = createPosition(orgUnitWithoutManager, testTitle, testUser, false);
			OrgUnitUserRoleAssignment assignment = createManagerAssignment(orgUnitWithoutManager, true, false, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, positionInUnit, orgUnitWithoutManager);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Should return NOT_APPLICABLE when user is not the manager of OrgUnit")
		void shouldReturnNotApplicableWhenUserIsNotTheManager() {
			// Arrange
			OrgUnitUserRoleAssignment assignment = createManagerAssignment(testOrgUnit, true, false, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}
	}

	@Nested
	@DisplayName("Position-based substitute match tests")
	class PositionBasedSubstituteMatchTests {

		@Test
		@DisplayName("Should return POSITIVE when user is substitute for the OrgUnit's manager")
		void shouldReturnPositiveWhenUserIsSubstituteForManager() {
			// Arrange
			ManagerSubstitute substituteRelation = createManagerSubstitute(managerUser, testUser, testOrgUnit);
			testUser.setSubstituteFor(List.of(substituteRelation));
			OrgUnitUserRoleAssignment assignment = createManagerAssignment(testOrgUnit, false, true, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
		}

		@Test
		@DisplayName("Should return NOT_APPLICABLE when assignment has isSubstitutes=false even if user is substitute")
		void shouldReturnNotApplicableWhenSubstituteFlagIsFalse() {
			// Arrange
			ManagerSubstitute substituteRelation = createManagerSubstitute(managerUser, testUser, testOrgUnit);
			testUser.setSubstituteFor(List.of(substituteRelation));
			OrgUnitUserRoleAssignment assignment = createManagerAssignment(testOrgUnit, false, false, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Should return NOT_APPLICABLE when user is substitute for a different OrgUnit")
		void shouldReturnNotApplicableWhenUserIsSubstituteForDifferentOrgUnit() {
			// Arrange
			OrgUnit differentOrgUnit = createOrgUnitWithManager("different-org-unit-uuid", null, managerUser);
			ManagerSubstitute substituteRelation = createManagerSubstitute(managerUser, testUser, differentOrgUnit);
			testUser.setSubstituteFor(List.of(substituteRelation));
			OrgUnitUserRoleAssignment assignment = createManagerAssignment(testOrgUnit, false, true, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Should return NOT_APPLICABLE when user is substitute for a different manager in the same OrgUnit")
		void shouldReturnNotApplicableWhenUserIsSubstituteForDifferentManager() {
			// Arrange
			User differentManager = createUser("different-manager-uuid");
			ManagerSubstitute substituteRelation = createManagerSubstitute(differentManager, testUser, testOrgUnit);
			testUser.setSubstituteFor(List.of(substituteRelation));
			OrgUnitUserRoleAssignment assignment = createManagerAssignment(testOrgUnit, false, true, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}
	}

	@Nested
	@DisplayName("Inheritance tests")
	class InheritanceTests {

		@Test
		@DisplayName("Should return NEGATIVE when position has doNotInherit flag set")
		void shouldReturnNegativeWhenPositionHasDoNotInheritFlag() {
			// Arrange
			OrgUnit orgUnitWithTestUserAsManager = createOrgUnitWithManager("org-unit-uuid", null, testUser);
			Position doNotInheritPosition = createPosition(orgUnitWithTestUserAsManager, testTitle, testUser, true);
			OrgUnitUserRoleAssignment assignment = createManagerAssignment(orgUnitWithTestUserAsManager, true, false, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, doNotInheritPosition, orgUnitWithTestUserAsManager);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NEGATIVE);
		}

		@Test
		@DisplayName("Should return NOT_APPLICABLE when inherit=false and position is in different OrgUnit")
		void shouldReturnNotApplicableWhenInheritFalseAndDifferentOrgUnit() {
			// Arrange
			OrgUnit parentOrgUnit = createOrgUnitWithManager("parent-org-unit-uuid", null, testUser);
			OrgUnit childOrgUnit = createOrgUnit("child-org-unit-uuid", parentOrgUnit);
			Position positionInChildUnit = createPosition(childOrgUnit, testTitle, testUser, false);
			OrgUnitUserRoleAssignment assignment = createManagerAssignment(parentOrgUnit, true, false, false);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, positionInChildUnit, parentOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Should evaluate manager logic when inherit=false but position is in same OrgUnit")
		void shouldEvaluateWhenInheritFalseButSameOrgUnit() {
			// Arrange
			OrgUnit orgUnitWithTestUserAsManager = createOrgUnitWithManager("org-unit-uuid", null, testUser);
			Position positionInSameUnit = createPosition(orgUnitWithTestUserAsManager, testTitle, testUser, false);
			OrgUnitUserRoleAssignment assignment = createManagerAssignment(orgUnitWithTestUserAsManager, true, false, false);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, positionInSameUnit, orgUnitWithTestUserAsManager);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
		}
	}

	@Nested
	@DisplayName("Manager-of-OU based evaluation tests (without position)")
	class ManagerOfOUBasedEvaluationTests {

		@Test
		@DisplayName("Should return POSITIVE when user is manager with inherit=true even from parent OU")
		void shouldReturnPositiveWhenUserIsManagerWithInherit() {
			// Arrange
			OrgUnit parentOrgUnit = createOrgUnitWithManager("parent-org-unit-uuid", null, testUser);
			OrgUnit childOrgUnit = createOrgUnit("child-org-unit-uuid", parentOrgUnit);
			OrgUnitUserRoleAssignment assignment = createManagerAssignment(childOrgUnit, true, false, true);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, parentOrgUnit, childOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
		}

		@Test
		@DisplayName("Should return NOT_APPLICABLE when inherit=false and managerOfOU differs from assignmentOU")
		void shouldReturnNotApplicableWhenNoInheritAndDifferentOU() {
			// Arrange
			OrgUnit managerOrgUnit = createOrgUnitWithManager("manager-org-unit-uuid", null, testUser);
			OrgUnit assignmentOrgUnit = createOrgUnit("assignment-org-unit-uuid", null);
			OrgUnitUserRoleAssignment assignment = createManagerAssignment(assignmentOrgUnit, true, false, false);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, managerOrgUnit, assignmentOrgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Should return POSITIVE when inherit=false but managerOfOU equals assignmentOU")
		void shouldReturnPositiveWhenNoInheritButSameOU() {
			// Arrange
			OrgUnit orgUnit = createOrgUnitWithManager("org-unit-uuid", null, testUser);
			OrgUnitUserRoleAssignment assignment = createManagerAssignment(orgUnit, true, false, false);

			// Act
			AssignmentAppliesResult result = rule.applies(assignment, testUser, orgUnit, orgUnit);

			// Assert
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
		}
	}
}
