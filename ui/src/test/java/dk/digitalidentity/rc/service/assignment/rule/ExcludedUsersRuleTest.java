package dk.digitalidentity.rc.service.assignment.rule;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.assignment.rules.AssignmentRule.AssignmentAppliesResult;
import dk.digitalidentity.rc.service.assignment.rules.ExcludedUsersRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createExcludedUsersAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createPosition;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createTitle;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ExcludedUsersRuleTest {

	private ExcludedUsersRule rule;

	private User testUser;
	private Position testPosition;
	private OrgUnit testOrgUnit;
	private Title testTitle;

	@BeforeEach
	void setUp() {
		rule = new ExcludedUsersRule();

		testUser = createUser("user-uuid-123");
		testOrgUnit = createOrgUnit("org-unit-uuid", null);
		testTitle = createTitle("test-title-uuid");
		testPosition = createPosition(testOrgUnit, testTitle, testUser, false);
	}

	@Nested
	@DisplayName("appliesToAssignment")
	class AppliesToAssignmentTests {

		@Test
		@DisplayName("should apply to OrgUnitUserRoleAssignment")
		void shouldApplyToOrgUnitUserRoleAssignment() {
			assertThat(rule.appliesToAssignment(OrgUnitUserRoleAssignment.class))
					.isTrue();
		}

		@Test
		@DisplayName("should apply to OrgUnitRoleGroupAssignment")
		void shouldApplyToOrgUnitRoleGroupAssignment() {
			assertThat(rule.appliesToAssignment(OrgUnitRoleGroupAssignment.class))
					.isTrue();
		}

		@Test
		@DisplayName("should not apply to non-OrgUnitAssignment classes")
		void shouldNotApplyToOtherClasses() {
			assertThat(rule.appliesToAssignment(String.class))
					.isFalse();
		}
	}

	@Nested
	@DisplayName("Null check handling")
	class NullCheckHandlingTests {
		@Test
		@DisplayName("Null Assignment returns NOT_APPLICABLE")
		void assignmentNullReturnsNOT_APPLICABLE () {
			// When
			AssignmentAppliesResult result = rule.applies(null, testUser, testPosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Null User returns NOT_APPLICABLE")
		void userNullReturnsNOT_APPLICABLE () {
			// Given
			OrgUnitUserRoleAssignment assignment = createExcludedUsersAssignment(false, Collections.emptyList(), testOrgUnit);

			// When
			AssignmentAppliesResult result = rule.applies(assignment, null, testPosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Null Position returns NOT_APPLICABLE")
		void positionNullReturnsNOT_APPLICABLE () {
			// Given
			OrgUnitUserRoleAssignment assignment = createExcludedUsersAssignment(false, Collections.emptyList(), testOrgUnit);
			Position position = null;

			// When
			AssignmentAppliesResult result = rule.applies(assignment, testUser, position, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Null orgunit returns NOT_APPLICABLE")
		void orgunitNullReturnsNOT_APPLICABLE () {
			// Given
			OrgUnitUserRoleAssignment assignment = createExcludedUsersAssignment(false, Collections.emptyList(), testOrgUnit);

			// When
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, null);

			// Then
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Assignment exceptedUsers null returns NOT_APPLICABLE")
		void exceptedUsersNullReturnsNOT_APPLICABLE () {
			// Given
			OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
			assignment.setOrgUnit(testOrgUnit);
			assignment.setExceptedUsers(null);

			// When
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}
	}

	@Nested
	@DisplayName("User exclusion checks")
	class UserExclusionTests {

		@Test
		@DisplayName("should return NOT_APPLICABLE when assignment has no excluded users")
		void shouldReturnNotApplicableWhenNoExcludedUsers() {
			// Given
			OrgUnitUserRoleAssignment assignment = createExcludedUsersAssignment(false, Collections.emptyList(), testOrgUnit);

			// When
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("should return NEGATIVE when user is in excluded list")
		void shouldReturnNegativeWhenUserIsExcluded() {
			// Given
			OrgUnitUserRoleAssignment assignment = createExcludedUsersAssignment(true, List.of(testUser), testOrgUnit);

			// When
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentAppliesResult.NEGATIVE);
		}

		@Test
		@DisplayName("should return POSITIVE when user is not in excluded list")
		void shouldReturnPositiveWhenUserIsNotExcluded() {
			// Given
			User otherUser = createUser("other-user-uuid");
			OrgUnitUserRoleAssignment assignment = createExcludedUsersAssignment(true, List.of(otherUser), testOrgUnit);

			// When
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentAppliesResult.POSITIVE);
		}

		@Test
		@DisplayName("should return NEGATIVE when user is one of multiple excluded users")
		void shouldReturnNegativeWhenUserIsOneOfMultipleExcluded() {
			// Given
			User otherUser1 = createUser("other-user-1");
			User otherUser2 = createUser("other-user-2");
			OrgUnitUserRoleAssignment assignment = createExcludedUsersAssignment(true, List.of(otherUser1, testUser, otherUser2), testOrgUnit);

			// When
			AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentAppliesResult.NEGATIVE);
		}
	}
}
