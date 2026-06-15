package dk.digitalidentity.rc.service.assignment.rule;

import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.assignment.rules.AssignmentRule.AssignmentAppliesResult;
import dk.digitalidentity.rc.service.assignment.rules.ExcludedOusRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createExcludedOusAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createPosition;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createTitle;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcludedOusRuleTest {

	@Mock
	private OrgUnitDao orgUnitDao;

	private ExcludedOusRule rule;

	private User testUser;
	private OrgUnit assignedOrgUnit;
	private Title testTitle;

	@BeforeEach
	void setUp() {
		rule = new ExcludedOusRule(orgUnitDao);

		testUser = createUser("user-uuid-123");
		assignedOrgUnit = createOrgUnit("assigned-ou-uuid", null);
		testTitle = createTitle("test-title-uuid");
	}

	@Nested
	@DisplayName("appliesToAssignment")
	class AppliesToAssignmentTests {

		@Test
		@DisplayName("should apply to OrgUnitUserRoleAssignment")
		void shouldApplyToOrgUnitUserRoleAssignment() {
			assertThat(rule.appliesToAssignment(OrgUnitUserRoleAssignment.class)).isTrue();
		}

		@Test
		@DisplayName("should apply to OrgUnitRoleGroupAssignment")
		void shouldApplyToOrgUnitRoleGroupAssignment() {
			assertThat(rule.appliesToAssignment(OrgUnitRoleGroupAssignment.class)).isTrue();
		}

		@Test
		@DisplayName("should not apply to non-OrgUnitAssignment classes")
		void shouldNotApplyToOtherClasses() {
			assertThat(rule.appliesToAssignment(String.class)).isFalse();
		}
	}

	@Nested
	@DisplayName("OU exclusion checks")
	class OuExclusionTests {

		@Test
		@DisplayName("should return NOT_APPLICABLE when flag is false")
		void shouldReturnNotApplicableWhenFlagFalse() {
			OrgUnit positionOu = createOrgUnit("position-ou-uuid", assignedOrgUnit);
			Position position = createPosition(positionOu, testTitle, testUser, false);
			OrgUnitUserRoleAssignment assignment = createExcludedOusAssignment(false, Collections.emptyList(), assignedOrgUnit);

			AssignmentAppliesResult result = rule.applies(assignment, testUser, position, assignedOrgUnit);

			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("should return NOT_APPLICABLE when excepted OUs list is empty")
		void shouldReturnNotApplicableWhenExceptedOusEmpty() {
			OrgUnit positionOu = createOrgUnit("position-ou-uuid", assignedOrgUnit);
			Position position = createPosition(positionOu, testTitle, testUser, false);
			OrgUnitUserRoleAssignment assignment = createExcludedOusAssignment(true, Collections.emptyList(), assignedOrgUnit);

			AssignmentAppliesResult result = rule.applies(assignment, testUser, position, assignedOrgUnit);

			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("should return NEGATIVE when position is in the directly excepted OU")
		void shouldReturnNegativeWhenPositionInDirectlyExceptedOu() {
			OrgUnit exceptedOu = createOrgUnit("excepted-ou-uuid", assignedOrgUnit);
			Position position = createPosition(exceptedOu, testTitle, testUser, false);
			OrgUnitUserRoleAssignment assignment = createExcludedOusAssignment(true, List.of(exceptedOu), assignedOrgUnit);
			when(orgUnitDao.findAllAncestorUuids("excepted-ou-uuid")).thenReturn(List.of("excepted-ou-uuid", "assigned-ou-uuid"));

			AssignmentAppliesResult result = rule.applies(assignment, testUser, position, assignedOrgUnit);

			assertThat(result).isEqualTo(AssignmentAppliesResult.NEGATIVE);
		}

		@Test
		@DisplayName("should return NEGATIVE when position is in a child of an excepted OU")
		void shouldReturnNegativeWhenPositionInChildOfExceptedOu() {
			OrgUnit exceptedOu = createOrgUnit("excepted-ou-uuid", assignedOrgUnit);
			OrgUnit childOfExcepted = createOrgUnit("child-of-excepted-uuid", exceptedOu);
			Position position = createPosition(childOfExcepted, testTitle, testUser, false);
			OrgUnitUserRoleAssignment assignment = createExcludedOusAssignment(true, List.of(exceptedOu), assignedOrgUnit);
			when(orgUnitDao.findAllAncestorUuids("child-of-excepted-uuid")).thenReturn(List.of("child-of-excepted-uuid", "excepted-ou-uuid", "assigned-ou-uuid"));

			AssignmentAppliesResult result = rule.applies(assignment, testUser, position, assignedOrgUnit);

			assertThat(result).isEqualTo(AssignmentAppliesResult.NEGATIVE);
		}

		@Test
		@DisplayName("should return NEGATIVE when position is deeply nested under an excepted OU")
		void shouldReturnNegativeWhenPositionDeeplyNestedUnderExceptedOu() {
			OrgUnit exceptedOu = createOrgUnit("excepted-ou-uuid", assignedOrgUnit);
			OrgUnit child = createOrgUnit("child-uuid", exceptedOu);
			OrgUnit grandchild = createOrgUnit("grandchild-uuid", child);
			Position position = createPosition(grandchild, testTitle, testUser, false);
			OrgUnitUserRoleAssignment assignment = createExcludedOusAssignment(true, List.of(exceptedOu), assignedOrgUnit);
			when(orgUnitDao.findAllAncestorUuids("grandchild-uuid")).thenReturn(List.of("grandchild-uuid", "child-uuid", "excepted-ou-uuid", "assigned-ou-uuid"));

			AssignmentAppliesResult result = rule.applies(assignment, testUser, position, assignedOrgUnit);

			assertThat(result).isEqualTo(AssignmentAppliesResult.NEGATIVE);
		}

		@Test
		@DisplayName("should return POSITIVE when position is in a non-excepted sibling OU")
		void shouldReturnPositiveWhenPositionInNonExceptedOu() {
			OrgUnit exceptedOu = createOrgUnit("excepted-ou-uuid", assignedOrgUnit);
			OrgUnit siblingOu = createOrgUnit("sibling-ou-uuid", assignedOrgUnit);
			Position position = createPosition(siblingOu, testTitle, testUser, false);
			OrgUnitUserRoleAssignment assignment = createExcludedOusAssignment(true, List.of(exceptedOu), assignedOrgUnit);
			when(orgUnitDao.findAllAncestorUuids("sibling-ou-uuid")).thenReturn(List.of("sibling-ou-uuid", "assigned-ou-uuid"));

			AssignmentAppliesResult result = rule.applies(assignment, testUser, position, assignedOrgUnit);

			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
		}

		@Test
		@DisplayName("should return NEGATIVE when position OU matches one of multiple excepted OUs")
		void shouldReturnNegativeWhenPositionMatchesOneOfMultipleExceptedOus() {
			OrgUnit exceptedOu1 = createOrgUnit("excepted-ou-1-uuid", assignedOrgUnit);
			OrgUnit exceptedOu2 = createOrgUnit("excepted-ou-2-uuid", assignedOrgUnit);
			Position position = createPosition(exceptedOu2, testTitle, testUser, false);
			OrgUnitUserRoleAssignment assignment = createExcludedOusAssignment(true, List.of(exceptedOu1, exceptedOu2), assignedOrgUnit);
			when(orgUnitDao.findAllAncestorUuids("excepted-ou-2-uuid")).thenReturn(List.of("excepted-ou-2-uuid", "assigned-ou-uuid"));

			AssignmentAppliesResult result = rule.applies(assignment, testUser, position, assignedOrgUnit);

			assertThat(result).isEqualTo(AssignmentAppliesResult.NEGATIVE);
		}
	}

	@Nested
	@DisplayName("doNotInherit position handling")
	class DoNotInheritTests {

		@Test
		@DisplayName("should return NEGATIVE when position has doNotInherit=true")
		void shouldReturnNegativeWhenDoNotInherit() {
			OrgUnit positionOu = createOrgUnit("position-ou-uuid", assignedOrgUnit);
			Position position = createPosition(positionOu, testTitle, testUser, true);
			OrgUnitUserRoleAssignment assignment = createExcludedOusAssignment(true, List.of(positionOu), assignedOrgUnit);

			// validateAssignmentEligibility short-circuits before the DAO is called
			AssignmentAppliesResult result = rule.applies(assignment, testUser, position, assignedOrgUnit);

			assertThat(result).isEqualTo(AssignmentAppliesResult.NEGATIVE);
		}
	}
}
