package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.assignment.rules.AssignmentRule.AssignmentAppliesResult;
import dk.digitalidentity.rc.service.assignment.rules.AssignmentRuleEvaluator;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createDirectRoleGroupAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createDirectUserRoleAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnitRoleGroupAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnitUserRoleAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createPosition;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createRoleGroup;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createTitle;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUserRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CurrentAssignmentCalculatorTest {

	@Mock
	private AssignmentRuleEvaluator ruleEvaluator;

	@Mock
	private OrgUnitService orgUnitService;

	@InjectMocks
	private CurrentAssignmentCalculator calculator;

	private User testUser;
	private ItSystem testItSystem;

	@BeforeEach
	void setUp() {
		testUser = createUser("test-user-uuid");
		testItSystem = createItSystem("test-it-system-uuid");
	}

	@Nested
	@DisplayName("Direct User Role Assignments")
	class DirectUserRoleAssignmentTests {

		@Test
		@DisplayName("should include active direct user role assignments")
		void shouldIncludeActiveDirectAssignments() {
			// Given
			UserRole userRole = createUserRole("role-uuid", testItSystem);
			UserUserRoleAssignment assignment = createDirectUserRoleAssignment(userRole, null, null);
			testUser.setUserRoleAssignments(List.of(assignment));
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(Collections.emptyList());

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft())
				.hasSize(1)
				.extracting(CurrentAssignment::getUserRole)
				.containsExactly(userRole);
		}

		/**
		 * Future assignments are needed for the UI, and should be included
		 */
		@Test
		@DisplayName("should include assignments with future start date")
		void shouldIncludeAssignmentsWithFutureStartDate() {
			// Given
			UserRole userRole = createUserRole("role-uuid", testItSystem);
			UserUserRoleAssignment assignment = createDirectUserRoleAssignment(
				userRole,
				LocalDate.now().plusDays(1),  // Future start date
				null
			);
			testUser.setUserRoleAssignments(List.of(assignment));
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(Collections.emptyList());

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft())
				.hasSize(1)
				.extracting(CurrentAssignment::getUserRole)
				.containsExactly(userRole);
		}

		@Test
		@DisplayName("should exclude assignments with past stop date")
		void shouldExcludeAssignmentsWithPastStopDate() {
			// Given
			UserRole userRole = createUserRole("role-uuid", testItSystem);
			UserUserRoleAssignment assignment = createDirectUserRoleAssignment(
				userRole,
				null,
				LocalDate.now().minusDays(1)  // Past stop date
			);
			testUser.setUserRoleAssignments(List.of(assignment));
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(Collections.emptyList());

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft()).isEmpty();
		}

		@Test
		@DisplayName("should exclude assignments with stop date today")
		void shouldExcludeAssignmentsWithStopDateToday() {
			// Given
			UserRole userRole = createUserRole("role-uuid", testItSystem);
			UserUserRoleAssignment assignment = createDirectUserRoleAssignment(
				userRole,
				null,
				LocalDate.now()  // Stops today
			);
			testUser.setUserRoleAssignments(List.of(assignment));
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(Collections.emptyList());

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft()).isEmpty();
		}

		/**
		 * An active assignment:<br>
		 * - Has startdate null OR a startdate on or before today<br>
		 * - has no enddate OR an enddate that is later than today
		 */
		@Test
		@DisplayName("should include assignments with current date within range")
		void shouldIncludeAssignmentsWithCurrentDateInRange() {
			// Given
			UserRole userRole = createUserRole("role-uuid", testItSystem);
			UserUserRoleAssignment assignment = createDirectUserRoleAssignment(
				userRole,
				LocalDate.now().minusDays(5),  // Started 5 days ago
				LocalDate.now().plusDays(5)    // Ends in 5 days
			);
			testUser.setUserRoleAssignments(List.of(assignment));
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(Collections.emptyList());

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft()).hasSize(1);
		}
	}

	@Nested
	@DisplayName("Direct Role Group Assignments")
	class DirectRoleGroupAssignmentTests {

		@Test
		@DisplayName("should include roles from active direct role group assignments")
		void shouldIncludeRolesFromActiveRoleGroupAssignments() {
			// Given
			UserRole userRole = createUserRole("role-uuid", testItSystem);
			RoleGroup roleGroup = createRoleGroup(1L, List.of(userRole));
			UserRoleGroupAssignment assignment = createDirectRoleGroupAssignment(roleGroup, null, null);

			testUser.setUserRoleAssignments(Collections.emptyList());
			testUser.setRoleGroupAssignments(List.of(assignment));
			testUser.setPositions(Collections.emptyList());

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft())
				.extracting(CurrentAssignment::getUserRole)
				.containsExactly(userRole);
		}

		@Test
		@DisplayName("should include role groups with future start date")
		void shouldIncludeRoleGroupsWithFutureStartDate() {
			// Given
			UserRole userRole = createUserRole("role-uuid", testItSystem);
			RoleGroup roleGroup = createRoleGroup(1L, List.of(userRole));
			UserRoleGroupAssignment assignment = createDirectRoleGroupAssignment(
				roleGroup,
				LocalDate.now().plusDays(1),
				null
			);

			testUser.setUserRoleAssignments(Collections.emptyList());
			testUser.setRoleGroupAssignments(List.of(assignment));
			testUser.setPositions(Collections.emptyList());

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft())
				.extracting(CurrentAssignment::getUserRole)
				.containsExactly(userRole);
		}

		@Test
		@DisplayName("should produce a single roleGroup-only row for an empty direct role group")
		void shouldProduceRoleGroupOnlyRowForEmptyRoleGroup() {
			// Given a role group with no userroles assigned directly to the user
			RoleGroup emptyRoleGroup = createRoleGroup(1L, List.of());
			UserRoleGroupAssignment assignment = createDirectRoleGroupAssignment(emptyRoleGroup, null, null);

			testUser.setUserRoleAssignments(Collections.emptyList());
			testUser.setRoleGroupAssignments(List.of(assignment));
			testUser.setPositions(Collections.emptyList());

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then the assignment is still represented by one roleGroup-only row
			assertThat(result.getLeft()).hasSize(1);
			CurrentAssignment row = result.getLeft().iterator().next();
			assertThat(row.getRoleGroup()).isEqualTo(emptyRoleGroup);
			assertThat(row.getUserRole()).isNull();
			assertThat(row.getItSystem()).isNull();
			assertThat(row.getRecordHash()).isNotBlank();
		}
	}

	@Nested
	@DisplayName("OrgUnit Inherited Assignments")
	class OrgUnitInheritedAssignmentTests {

		@Test
		@DisplayName("should include assignments inherited from position's OrgUnit")
		void shouldIncludeAssignmentsFromOrgUnit() {
			// Given
			UserRole userRole = createUserRole("role-uuid", testItSystem);
			OrgUnit orgUnit = createOrgUnit("org-unit-uuid", null);
			OrgUnitUserRoleAssignment ouAssignment = createOrgUnitUserRoleAssignment(userRole, orgUnit);
			orgUnit.setUserRoleAssignments(List.of(ouAssignment));
			orgUnit.setRoleGroupAssignments(Collections.emptyList());

			Title title = createTitle("title-uuid");
			Position position = createPosition(orgUnit, title, testUser, false);

			testUser.setUserRoleAssignments(Collections.emptyList());
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(List.of(position));

			given(ruleEvaluator.applies(ouAssignment, testUser, position, orgUnit))
				.willReturn(AssignmentAppliesResult.POSITIVE);

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft())
				.extracting(CurrentAssignment::getUserRole)
				.containsExactly(userRole);
		}

		@Test
		@DisplayName("should produce a single roleGroup-only row for an empty role group inherited from OrgUnit")
		void shouldProduceRoleGroupOnlyRowForEmptyOrgUnitRoleGroup() {
			// Given an empty role group assigned to the position's OrgUnit
			RoleGroup emptyRoleGroup = createRoleGroup(1L, List.of());
			OrgUnit orgUnit = createOrgUnit("org-unit-uuid", null);
			OrgUnitRoleGroupAssignment ouAssignment = createOrgUnitRoleGroupAssignment(emptyRoleGroup, orgUnit);
			orgUnit.setUserRoleAssignments(Collections.emptyList());
			orgUnit.setRoleGroupAssignments(List.of(ouAssignment));

			Title title = createTitle("title-uuid");
			Position position = createPosition(orgUnit, title, testUser, false);

			testUser.setUserRoleAssignments(Collections.emptyList());
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(List.of(position));

			given(ruleEvaluator.applies(ouAssignment, testUser, position, orgUnit))
				.willReturn(AssignmentAppliesResult.POSITIVE);

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft()).hasSize(1);
			CurrentAssignment row = result.getLeft().iterator().next();
			assertThat(row.getRoleGroup()).isEqualTo(emptyRoleGroup);
			assertThat(row.getUserRole()).isNull();
			assertThat(row.getOrgUnit()).isEqualTo(orgUnit);
		}

		@Test
		@DisplayName("should not inherit assignments when position has doNotInherit flag")
		void shouldNotInheritWhenDoNotInheritIsSet() {
			// Given
			UserRole userRole = createUserRole("role-uuid", testItSystem);
			OrgUnit orgUnit = createOrgUnit("org-unit-uuid", null);
			OrgUnitUserRoleAssignment ouAssignment = createOrgUnitUserRoleAssignment(userRole, orgUnit);
			orgUnit.setUserRoleAssignments(List.of(ouAssignment));
			orgUnit.setRoleGroupAssignments(Collections.emptyList());

			Title title = createTitle("title-uuid");
			Position position = createPosition(orgUnit, title, testUser, true);  // doNotInherit = true

			testUser.setUserRoleAssignments(Collections.emptyList());
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(List.of(position));

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft()).isEmpty();
		}

		@Test
		@DisplayName("should inherit assignments from parent OrgUnits")
		void shouldInheritFromParentOrgUnits() {
			// Given
			UserRole parentRole = createUserRole("parent-role-uuid", testItSystem);
			OrgUnit parentOrgUnit = createOrgUnit("parent-org-unit-uuid", null);
			OrgUnitUserRoleAssignment parentAssignment = createOrgUnitUserRoleAssignment(parentRole, parentOrgUnit);
			parentOrgUnit.setUserRoleAssignments(List.of(parentAssignment));
			parentOrgUnit.setRoleGroupAssignments(Collections.emptyList());

			OrgUnit childOrgUnit = createOrgUnit("child-org-unit-uuid", parentOrgUnit);
			childOrgUnit.setUserRoleAssignments(Collections.emptyList());
			childOrgUnit.setRoleGroupAssignments(Collections.emptyList());

			Title title = createTitle("title-uuid");
			Position position = createPosition(childOrgUnit, title, testUser, false);

			testUser.setUserRoleAssignments(Collections.emptyList());
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(List.of(position));

			given(ruleEvaluator.applies(parentAssignment, testUser, position, parentOrgUnit))
				.willReturn(AssignmentAppliesResult.POSITIVE);

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft())
				.extracting(CurrentAssignment::getUserRole)
				.containsExactly(parentRole);
		}

		@Test
		@DisplayName("should combine assignments from child and parent OrgUnits")
		void shouldCombineChildAndParentAssignments() {
			// Given
			UserRole parentRole = createUserRole("parent-role-uuid", testItSystem);
			UserRole childRole = createUserRole("child-role-uuid", testItSystem);

			OrgUnit parentOrgUnit = createOrgUnit("parent-org-unit-uuid", null);
			OrgUnitUserRoleAssignment parentAssignment = createOrgUnitUserRoleAssignment(parentRole, parentOrgUnit);
			parentOrgUnit.setUserRoleAssignments(List.of(parentAssignment));
			parentOrgUnit.setRoleGroupAssignments(Collections.emptyList());

			OrgUnit childOrgUnit = createOrgUnit("child-org-unit-uuid", parentOrgUnit);
			OrgUnitUserRoleAssignment childAssignment = createOrgUnitUserRoleAssignment(childRole, childOrgUnit);
			childOrgUnit.setUserRoleAssignments(List.of(childAssignment));
			childOrgUnit.setRoleGroupAssignments(Collections.emptyList());

			Title title = createTitle("title-uuid");
			Position position = createPosition(childOrgUnit, title, testUser, false);

			testUser.setUserRoleAssignments(Collections.emptyList());
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(List.of(position));

			given(ruleEvaluator.applies(childAssignment, testUser, position, childOrgUnit))
				.willReturn(AssignmentAppliesResult.POSITIVE);
			given(ruleEvaluator.applies(parentAssignment, testUser, position, parentOrgUnit))
				.willReturn(AssignmentAppliesResult.POSITIVE);

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft())
				.hasSize(2)
				.extracting(CurrentAssignment::getUserRole)
				.containsExactlyInAnyOrder(parentRole, childRole);
		}
	}

	@Nested
	@DisplayName("Assignment Exceptions (Negated Assignments)")
	class AssignmentExceptionTests {

		@Test
		@DisplayName("should track negated assignments as exceptions")
		void shouldTrackNegatedAssignmentsAsExceptions() {
			// Given
			UserRole userRole = createUserRole("role-uuid", testItSystem);
			OrgUnit orgUnit = createOrgUnit("org-unit-uuid", null);
			OrgUnitUserRoleAssignment ouAssignment = createOrgUnitUserRoleAssignment(userRole, orgUnit);
			orgUnit.setUserRoleAssignments(List.of(ouAssignment));
			orgUnit.setRoleGroupAssignments(Collections.emptyList());

			Title title = createTitle("title-uuid");
			Position position = createPosition(orgUnit, title, testUser, false);

			testUser.setUserRoleAssignments(Collections.emptyList());
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(List.of(position));

			given(ruleEvaluator.applies(ouAssignment, testUser, position, orgUnit))
				.willReturn(AssignmentAppliesResult.NEGATIVE);

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft()).isEmpty();
			assertThat(result.getRight()).hasSize(1);
		}

		@Test
		@DisplayName("should not include assignments when rule evaluates to NEGATIVE")
		void shouldNotIncludeNegativeAssignments() {
			// Given
			UserRole allowedRole = createUserRole("allowed-role-uuid", testItSystem);
			UserRole deniedRole = createUserRole("denied-role-uuid", testItSystem);

			OrgUnit orgUnit = createOrgUnit("org-unit-uuid", null);
			OrgUnitUserRoleAssignment allowedAssignment = createOrgUnitUserRoleAssignment(allowedRole, orgUnit);
			OrgUnitUserRoleAssignment deniedAssignment = createOrgUnitUserRoleAssignment(deniedRole, orgUnit);
			orgUnit.setUserRoleAssignments(List.of(allowedAssignment, deniedAssignment));
			orgUnit.setRoleGroupAssignments(Collections.emptyList());

			Title title = createTitle("title-uuid");
			Position position = createPosition(orgUnit, title, testUser, false);

			testUser.setUserRoleAssignments(Collections.emptyList());
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(List.of(position));

			given(ruleEvaluator.applies(allowedAssignment, testUser, position, orgUnit))
				.willReturn(AssignmentAppliesResult.POSITIVE);
			given(ruleEvaluator.applies(deniedAssignment, testUser, position, orgUnit))
				.willReturn(AssignmentAppliesResult.NEGATIVE);

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft())
				.hasSize(1)
				.extracting(CurrentAssignment::getUserRole)
				.containsExactly(allowedRole);
			assertThat(result.getRight()).hasSize(1);
		}
	}

	@Nested
	@DisplayName("OrgUnit Role Group Assignments")
	class OrgUnitRoleGroupAssignmentTests {

		@Test
		@DisplayName("should include roles from OrgUnit role group assignments")
		void shouldIncludeRolesFromOrgUnitRoleGroups() {
			// Given
			UserRole userRole = createUserRole("role-uuid", testItSystem);
			RoleGroup roleGroup = createRoleGroup(1L, List.of(userRole));

			OrgUnit orgUnit = createOrgUnit("org-unit-uuid", null);
			OrgUnitRoleGroupAssignment rgAssignment = createOrgUnitRoleGroupAssignment(roleGroup, orgUnit);
			orgUnit.setUserRoleAssignments(Collections.emptyList());
			orgUnit.setRoleGroupAssignments(List.of(rgAssignment));

			Title title = createTitle("title-uuid");
			Position position = createPosition(orgUnit, title, testUser, false);

			testUser.setUserRoleAssignments(Collections.emptyList());
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(List.of(position));

			given(ruleEvaluator.applies(rgAssignment, testUser, position, orgUnit))
				.willReturn(AssignmentAppliesResult.POSITIVE);

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft())
				.extracting(CurrentAssignment::getUserRole)
				.containsExactly(userRole);
		}
	}

	@Nested
	@DisplayName("Edge Cases")
	class EdgeCaseTests {

		@Test
		@DisplayName("should return empty sets when user has no assignments")
		void shouldReturnEmptyWhenNoAssignments() {
			// Given
			testUser.setUserRoleAssignments(Collections.emptyList());
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(Collections.emptyList());

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft()).isEmpty();
			assertThat(result.getRight()).isEmpty();
		}

		@Test
		@DisplayName("should handle user with multiple positions")
		void shouldHandleMultiplePositions() {
			// Given
			UserRole role1 = createUserRole("role-1-uuid", testItSystem);
			UserRole role2 = createUserRole("role-2-uuid", testItSystem);

			OrgUnit orgUnit1 = createOrgUnit("org-unit-1-uuid", null);
			OrgUnitUserRoleAssignment assignment1 = createOrgUnitUserRoleAssignment(role1, orgUnit1);
			orgUnit1.setUserRoleAssignments(List.of(assignment1));
			orgUnit1.setRoleGroupAssignments(Collections.emptyList());

			OrgUnit orgUnit2 = createOrgUnit("org-unit-2-uuid", null);
			OrgUnitUserRoleAssignment assignment2 = createOrgUnitUserRoleAssignment(role2, orgUnit2);
			orgUnit2.setUserRoleAssignments(List.of(assignment2));
			orgUnit2.setRoleGroupAssignments(Collections.emptyList());

			Title title = createTitle("title-uuid");
			Position position1 = createPosition(orgUnit1, title, testUser, false);
			Position position2 = createPosition(orgUnit2, title, testUser, false);

			testUser.setUserRoleAssignments(Collections.emptyList());
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(List.of(position1, position2));

			given(ruleEvaluator.applies(assignment1, testUser, position1, orgUnit1))
				.willReturn(AssignmentAppliesResult.POSITIVE);
			given(ruleEvaluator.applies(assignment2, testUser, position2, orgUnit2))
				.willReturn(AssignmentAppliesResult.POSITIVE);

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then
			assertThat(result.getLeft())
				.hasSize(2)
				.extracting(CurrentAssignment::getUserRole)
				.containsExactlyInAnyOrder(role1, role2);
		}

		@Test
		@DisplayName("function-based role inherited via multiple positions emits one row (not one per position)")
		void shouldDedupFunctionBasedRoleAcrossPositions() {
			// Given — fælles ancestor med funktions-baseret rolle, to stillinger der hver
			// walker op til den. Før fixet producerede hver stilling sin egen række med
			// position-specifik responsibleOu → duplikerede current_assignment-rækker.
			UserRole functionRole = createUserRole("function-role-uuid", testItSystem);
			OrgUnit commonAncestor = createOrgUnit("common-ancestor-uuid", null);
			OrgUnitUserRoleAssignment functionAssignment = createOrgUnitUserRoleAssignment(functionRole, commonAncestor);
			functionAssignment.setContainsFunctions(true);
			functionAssignment.setInherit(true);
			commonAncestor.setUserRoleAssignments(List.of(functionAssignment));
			commonAncestor.setRoleGroupAssignments(Collections.emptyList());

			OrgUnit child1 = createOrgUnit("child-1-uuid", commonAncestor);
			child1.setUserRoleAssignments(Collections.emptyList());
			child1.setRoleGroupAssignments(Collections.emptyList());
			OrgUnit child2 = createOrgUnit("child-2-uuid", commonAncestor);
			child2.setUserRoleAssignments(Collections.emptyList());
			child2.setRoleGroupAssignments(Collections.emptyList());

			Title title = createTitle("title-uuid");
			Position position1 = createPosition(child1, title, testUser, false);
			Position position2 = createPosition(child2, title, testUser, false);

			testUser.setUserRoleAssignments(Collections.emptyList());
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(List.of(position1, position2));

			given(ruleEvaluator.applies(functionAssignment, testUser, position1, commonAncestor))
				.willReturn(AssignmentAppliesResult.POSITIVE);
			given(ruleEvaluator.applies(functionAssignment, testUser, position2, commonAncestor))
				.willReturn(AssignmentAppliesResult.POSITIVE);

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then — én række pr. (rolle, assignment-OU), uafhængigt af antal stillinger.
			// responsibleOrgUnit låst til assignment-OU'en (commonAncestor) for funktions-rækker,
			// så recordHash kollapser de to walks.
			assertThat(result.getLeft())
				.hasSize(1)
				.allSatisfy(ca -> {
					assertThat(ca.getUserRole()).isEqualTo(functionRole);
					assertThat(ca.getOrgUnit()).isEqualTo(commonAncestor);
					assertThat(ca.getResponsibleOrgUnit()).isEqualTo(commonAncestor);
				});
		}

		@Test
		@DisplayName("non-function role inherited via multiple positions keeps per-position rows (legitimate multiplicity)")
		void shouldKeepPositionMultiplicityForNonFunctionRoles() {
			// Given — almindelig OU-arvet rolle (uden containsFunctions). Hver stilling repræsenterer
			// sin egen claim path og attesteres af sin egen ansvarlige OU, så N rækker er korrekt.
			UserRole ouRole = createUserRole("ou-role-uuid", testItSystem);
			OrgUnit commonAncestor = createOrgUnit("common-ancestor-uuid", null);
			OrgUnitUserRoleAssignment ouAssignment = createOrgUnitUserRoleAssignment(ouRole, commonAncestor);
			ouAssignment.setContainsFunctions(false);
			ouAssignment.setInherit(true);
			commonAncestor.setUserRoleAssignments(List.of(ouAssignment));
			commonAncestor.setRoleGroupAssignments(Collections.emptyList());

			OrgUnit child1 = createOrgUnit("child-1-uuid", commonAncestor);
			child1.setUserRoleAssignments(Collections.emptyList());
			child1.setRoleGroupAssignments(Collections.emptyList());
			OrgUnit child2 = createOrgUnit("child-2-uuid", commonAncestor);
			child2.setUserRoleAssignments(Collections.emptyList());
			child2.setRoleGroupAssignments(Collections.emptyList());

			Title title = createTitle("title-uuid");
			Position position1 = createPosition(child1, title, testUser, false);
			Position position2 = createPosition(child2, title, testUser, false);

			testUser.setUserRoleAssignments(Collections.emptyList());
			testUser.setRoleGroupAssignments(Collections.emptyList());
			testUser.setPositions(List.of(position1, position2));

			given(ruleEvaluator.applies(ouAssignment, testUser, position1, commonAncestor))
				.willReturn(AssignmentAppliesResult.POSITIVE);
			given(ruleEvaluator.applies(ouAssignment, testUser, position2, commonAncestor))
				.willReturn(AssignmentAppliesResult.POSITIVE);

			// When
			ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> result =
				calculator.calculateAllAssignmentsForUser(testUser);

			// Then — to rækker med forskellig responsibleOrgUnit (én pr. stilling).
			assertThat(result.getLeft())
				.hasSize(2)
				.extracting(CurrentAssignment::getResponsibleOrgUnit)
				.containsExactlyInAnyOrder(child1, child2);
		}
	}

}
