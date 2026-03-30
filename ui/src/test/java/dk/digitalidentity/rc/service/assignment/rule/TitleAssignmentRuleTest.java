package dk.digitalidentity.rc.service.assignment.rule;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import dk.digitalidentity.rc.service.assignment.rules.AssignmentRule;
import dk.digitalidentity.rc.service.assignment.rules.TitleAssignmentRule;
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
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgunitAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createPosition;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createTitle;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUserRole;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TitleAssignmentRuleTest {

	private TitleAssignmentRule rule;

	private User testUser;
	private Position testPosition;
	private OrgUnit testOrgUnit;
	private Title testTitle;
	private UserRole testUserRole;

	@BeforeEach
	void setUp() {
		rule = new TitleAssignmentRule();

		testUser = createUser("user-uuid-123");
		testOrgUnit = createOrgUnit("org-unit-uuid", null);
		testTitle = createTitle("test-title-uuid");
		testPosition = createPosition(testOrgUnit, testTitle, testUser, false);
		testUserRole = createUserRole();
	}

	@Nested
	@DisplayName("Null check handling")
	class NullCheckHandlingTests {
		@Test
		@DisplayName("Null Assignment returns NOT_APPLICABLE")
		void assignmentNullReturnsNOT_APPLICABLE () {
			// When
			AssignmentRule.AssignmentAppliesResult result = rule.applies(null, testUser, testPosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentRule.AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Null User returns NOT_APPLICABLE")
		void userNullReturnsNOT_APPLICABLE () {
			// Given
			OrgUnitUserRoleAssignment assignment = createExcludedUsersAssignment(false, Collections.emptyList(), testOrgUnit);

			// When
			AssignmentRule.AssignmentAppliesResult result = rule.applies(assignment, null, testPosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentRule.AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Null Position returns NOT_APPLICABLE")
		void positionNullReturnsNOT_APPLICABLE () {
			// Given
			OrgUnitUserRoleAssignment assignment = createExcludedUsersAssignment(false, Collections.emptyList(), testOrgUnit);
			Position position = null;

			// When
			AssignmentRule.AssignmentAppliesResult result = rule.applies(assignment, testUser, position, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentRule.AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Null orgunit returns NOT_APPLICABLE")
		void orgunitNullReturnsNOT_APPLICABLE () {
			// Given
			OrgUnitUserRoleAssignment assignment = createExcludedUsersAssignment(false, Collections.emptyList(), testOrgUnit);

			// When
			AssignmentRule.AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, null);

			// Then
			assertThat(result).isEqualTo(AssignmentRule.AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("Assignment containsTitles null returns NOT_APPLICABLE")
		void containsTitlesNullReturnsNOT_APPLICABLE () {
			// Given
			OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
			assignment.setOrgUnit(testOrgUnit);
			assignment.setUserRole(testUserRole);
			assignment.setTitles(List.of(testTitle));
			assignment.setContainsTitles(null);

			// When
			AssignmentRule.AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentRule.AssignmentAppliesResult.NOT_APPLICABLE);
		}
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
	@DisplayName("hasTitleConditions tests")
	class hasTitleConditionsTests {
		@Test
		@DisplayName("should return NOT_APPLICABLE when title containsTitles are not set")
		void containsTitlesNullReturnsNOT_APPLICABLE() {
			// Given
			OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
			assignment.setOrgUnit(testOrgUnit);
			assignment.setUserRole(testUserRole);
			assignment.setTitles(List.of(testTitle));
			assignment.setContainsTitles(null);

			// When
			AssignmentRule.AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentRule.AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("should return NOT_APPLICABLE when title containsTitles are NO")
		void containsTitlesNOReturnsNOT_APPLICABLE() {
			// Given
			OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
			assignment.setOrgUnit(testOrgUnit);
			assignment.setUserRole(testUserRole);
			assignment.setTitles(List.of(testTitle));
			assignment.setContainsTitles(ContainsTitles.NO);

			// When
			AssignmentRule.AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentRule.AssignmentAppliesResult.NOT_APPLICABLE);
		}
	}

	@Nested
	@DisplayName("checkTitleAgainstConditions tests")
	class checkTitleAgainstConditionsTests {

		@Test
		@DisplayName("should return NOT_APPLICABLE when title is null")
		void noTitleReturnsNOT_APPLICABLE() {
			// Given
			OrgUnitAssignment assignment = createOrgunitAssignment(testOrgUnit, testUserRole, List.of());
			Position nullTitlePosition = createPosition(testOrgUnit, null, testUser, false);

			// When
			AssignmentRule.AssignmentAppliesResult result = rule.applies(assignment, testUser, nullTitlePosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentRule.AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("should return NOT_APPLICABLE when assignment title includes position title")
		void matchingTitleReturnsNOT_APPLICABLE() {
			// Given
			OrgUnitAssignment assignment = createOrgunitAssignment(testOrgUnit, testUserRole, List.of(testTitle));

			// When
			AssignmentRule.AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentRule.AssignmentAppliesResult.NOT_APPLICABLE);
		}

		@Test
		@DisplayName("should return POSITIVE when assignment contains titles is POSITIVE ")
		void anyContainedTitleReturnsPOSITIVE() {
			// Given
			OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
			assignment.setOrgUnit(testOrgUnit);
			assignment.setUserRole(testUserRole);
			assignment.setTitles(List.of(testTitle));
			assignment.setContainsTitles(ContainsTitles.POSITIVE);

			// When
			AssignmentRule.AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentRule.AssignmentAppliesResult.POSITIVE);
		}

		@Test
		@DisplayName("should return NEGATIVE when assignment contains titles is NEGATIVE ")
		void excludedContainedTitlesReturnsNEGATIVE() {
			// Given
			OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
			assignment.setOrgUnit(testOrgUnit);
			assignment.setUserRole(testUserRole);
			assignment.setTitles(List.of(testTitle));
			assignment.setContainsTitles(ContainsTitles.NEGATIVE);

			// When
			AssignmentRule.AssignmentAppliesResult result = rule.applies(assignment, testUser, testPosition, testOrgUnit);

			// Then
			assertThat(result).isEqualTo(AssignmentRule.AssignmentAppliesResult.NEGATIVE);
		}
	}


}
