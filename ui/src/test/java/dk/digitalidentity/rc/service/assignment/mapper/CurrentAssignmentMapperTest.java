package dk.digitalidentity.rc.service.assignment.mapper;

import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createConstraintType;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createDirectRoleGroupAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createDirectUserRoleAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnitRoleGroupAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnitUserRoleAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createPostponedConstraint;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createRoleGroup;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createSystemRole;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createTitle;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUserRole;
import static org.assertj.core.api.Assertions.assertThat;

class CurrentAssignmentMapperTest {

	private User testUser;
	private UserRole testUserRole;
	private ItSystem testItSystem;
	private OrgUnit testOrgUnit;
	private Title testTitle;

	@BeforeEach
	void setUp() {
		testUser = createUser("test-user-uuid");
		testItSystem = createItSystem("test-it-system-uuid");
		testUserRole = createUserRole("test-user-role-uuid", testItSystem);
		testOrgUnit = createOrgUnit("test-org-unit-uuid", null);
		testTitle = createTitle("test-title-uuid");
	}

	@Nested
	@DisplayName("assignedWhen mapping")
	class AssignedWhenTests {

		private static final LocalDateTime ASSIGNED = LocalDateTime.of(2024, 9, 9, 14, 9, 14);

		@Test
		@DisplayName("Should take assignedWhen from the source assignment, not from the mapping time")
		void shouldTakeAssignedWhenFromSource() {
			UserUserRoleAssignment assignment = createDirectUserRoleAssignment(testUserRole, null, null);
			assignment.setAssignedTimestamp(Date.from(ASSIGNED.atZone(ZoneId.systemDefault()).toInstant()));

			CurrentAssignment result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testOrgUnit);

			assertThat(result.getAssignedWhen()).isEqualTo(ASSIGNED);
			assertThat(result.getAssignedWhen()).isNotEqualTo(result.getCreatedAt());
		}

		@Test
		@DisplayName("Should leave assignedWhen null when the source has none, so consumers fall back to validFrom")
		void shouldLeaveAssignedWhenNullWhenSourceHasNone() {
			UserUserRoleAssignment assignment = createDirectUserRoleAssignment(testUserRole, null, null);

			CurrentAssignment result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testOrgUnit);

			assertThat(result.getAssignedWhen()).isNull();
		}

		@Test
		@DisplayName("Should keep assignedWhen out of the record hash, so a corrected date does not version the row")
		void shouldKeepAssignedWhenOutOfRecordHash() {
			UserUserRoleAssignment without = createDirectUserRoleAssignment(testUserRole, null, null);
			UserUserRoleAssignment with = createDirectUserRoleAssignment(testUserRole, null, null);
			with.setAssignedTimestamp(Date.from(Instant.parse("2024-09-09T12:09:14Z")));

			String hashWithout = CurrentAssignmentMapper.toCurrentAssignment(without, testUser, testOrgUnit).getRecordHash();
			String hashWith = CurrentAssignmentMapper.toCurrentAssignment(with, testUser, testOrgUnit).getRecordHash();

			assertThat(hashWith).isEqualTo(hashWithout);
		}
	}

	@Nested
	@DisplayName("UserUserRoleAssignment mapping")
	class UserUserRoleAssignmentMappingTests {

		@Test
		@DisplayName("Should map basic fields from UserUserRoleAssignment")
		void shouldMapBasicFieldsFromUserUserRoleAssignment() {
			// Arrange
			LocalDate startDate = LocalDate.now();
			LocalDate stopDate = LocalDate.now().plusDays(30);
			UserUserRoleAssignment assignment = createDirectUserRoleAssignment(testUserRole, startDate, stopDate);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			CurrentAssignment result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testOrgUnit);

			// Assert
			assertThat(result.getUser()).isEqualTo(testUser);
			assertThat(result.getUserRole()).isEqualTo(testUserRole);
			assertThat(result.getItSystem()).isEqualTo(testItSystem);
			assertThat(result.getStartDate()).isEqualTo(startDate);
			assertThat(result.getStopDate()).isEqualTo(stopDate);
			assertThat(result.getAssignmentId()).isEqualTo(assignment.getId());
		}

		@Test
		@DisplayName("Should map postponed constraints when present")
		void shouldMapPostponedConstraintsWhenPresent() {
			// Arrange
			ConstraintType constraintType = createConstraintType("constraint-uuid", "Constraint Name", "entity-id");
			SystemRole systemRole = createSystemRole(1L);
			PostponedConstraint constraint = createPostponedConstraint("value1,value2", constraintType, systemRole);

			UserUserRoleAssignment assignment = createDirectUserRoleAssignment(testUserRole, null, null);
			assignment.setPostponedConstraints(List.of(constraint));
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			CurrentAssignment result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testOrgUnit);

			// Assert
			assertThat(result.getPostponedConstraints()).hasSize(1);
			assertThat(result.getPostponedConstraints().iterator().next().getConstraintTypeUuid()).isEqualTo(constraintType.getUuid());
		}

		@Test
		@DisplayName("Should handle null postponed constraints")
		void shouldHandleNullPostponedConstraints() {
			// Arrange
			UserUserRoleAssignment assignment = createDirectUserRoleAssignment(testUserRole, null, null);
			assignment.setPostponedConstraints(null);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			CurrentAssignment result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testOrgUnit);

			// Assert
			assertThat(result.getPostponedConstraints()).isEmpty();
		}

		@Test
		@DisplayName("Should generate record hash")
		void shouldGenerateRecordHash() {
			// Arrange
			UserUserRoleAssignment assignment = createDirectUserRoleAssignment(testUserRole, null, null);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			CurrentAssignment result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testOrgUnit);

			// Assert
			assertThat(result.getRecordHash()).isNotNull();
			assertThat(result.getRecordHash()).isNotEmpty();
		}
	}

	@Nested
	@DisplayName("UserRoleGroupAssignment mapping")
	class UserRoleGroupAssignmentMappingTests {

		@Test
		@DisplayName("Should expand RoleGroup to multiple CurrentAssignments")
		void shouldExpandRoleGroupToMultipleAssignments() {
			// Arrange
			UserRole role1 = createUserRole("role-1-uuid", testItSystem);
			role1.setId(1L);
			UserRole role2 = createUserRole("role-2-uuid", testItSystem);
			role2.setId(2L);
			UserRole role3 = createUserRole("role-3-uuid", testItSystem);
			role3.setId(3L);
			RoleGroup roleGroup = createRoleGroup(1L, List.of(role1, role2, role3));

			UserRoleGroupAssignment assignment = createDirectRoleGroupAssignment(roleGroup, null, null);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			Set<CurrentAssignment> result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testOrgUnit);

			// Assert
			assertThat(result).hasSize(3);
			assertThat(result).extracting(CurrentAssignment::getUserRole)
				.containsExactlyInAnyOrder(role1, role2, role3);
		}

		@Test
		@DisplayName("Should set roleGroup on all expanded assignments")
		void shouldSetRoleGroupOnAllExpandedAssignments() {
			// Arrange
			UserRole role1 = createUserRole("role-1-uuid", testItSystem);
			role1.setId(1L);
			UserRole role2 = createUserRole("role-2-uuid", testItSystem);
			role2.setId(2L);
			RoleGroup roleGroup = createRoleGroup(1L, List.of(role1, role2));

			UserRoleGroupAssignment assignment = createDirectRoleGroupAssignment(roleGroup, null, null);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			Set<CurrentAssignment> result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testOrgUnit);

			// Assert
			assertThat(result).hasSize(2)
				.allMatch(ca -> ca.getRoleGroup() == roleGroup);
		}

		@Test
		@DisplayName("Should return a single roleGroup-only row when RoleGroup has no UserRoles")
		void shouldReturnRoleGroupOnlyRowWhenRoleGroupHasNoUserRoles() {
			// Arrange
			RoleGroup emptyRoleGroup = createRoleGroup(1L, Collections.emptyList());

			UserRoleGroupAssignment assignment = createDirectRoleGroupAssignment(emptyRoleGroup, null, null);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			Set<CurrentAssignment> result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testOrgUnit);

			// Assert - the empty role group must still be represented so the assignment stays visible
			assertThat(result).hasSize(1);
			CurrentAssignment row = result.iterator().next();
			assertThat(row.isRoleGroupOnly()).isTrue();
			assertThat(row.getRoleGroup()).isEqualTo(emptyRoleGroup);
			assertThat(row.getUserRole()).isNull();
			assertThat(row.getItSystem()).isNull();
			assertThat(row.getRecordHash()).isNotBlank();
		}
	}

	@Nested
	@DisplayName("OrgUnitUserRoleAssignment mapping")
	class OrgUnitUserRoleAssignmentMappingTests {

		@Test
		@DisplayName("Should record how titles are filtered without moving the title or the record hash")
		void shouldRecordContainsTitlesOutsideTheRecordHash() {
			// Arrange - the same assignment, filtered positively and negatively on the same title
			OrgUnitUserRoleAssignment positive = createOrgUnitUserRoleAssignment(testUserRole, testOrgUnit);
			positive.setContainsTitles(ContainsTitles.POSITIVE);
			positive.setAssignedByName("Assigner");
			positive.setAssignedByUserId("assigner-id");

			OrgUnitUserRoleAssignment negative = createOrgUnitUserRoleAssignment(testUserRole, testOrgUnit);
			negative.setContainsTitles(ContainsTitles.NEGATIVE);
			negative.setAssignedByName("Assigner");
			negative.setAssignedByUserId("assigner-id");

			// Act
			CurrentAssignment positiveResult = CurrentAssignmentMapper.toCurrentAssignment(positive, testUser, testTitle, testOrgUnit);
			CurrentAssignment negativeResult = CurrentAssignmentMapper.toCurrentAssignment(negative, testUser, testTitle, testOrgUnit);

			// Assert - hashing the flag would close and reopen every title filtered assignment on upgrade
			assertThat(positiveResult.getContainsTitles()).isEqualTo(ContainsTitles.POSITIVE);
			assertThat(negativeResult.getContainsTitles()).isEqualTo(ContainsTitles.NEGATIVE);
			assertThat(positiveResult.getTitle()).isEqualTo(testTitle);
			assertThat(negativeResult.getTitle()).isEqualTo(testTitle);
			assertThat(negativeResult.getRecordHash()).isEqualTo(positiveResult.getRecordHash());
		}

		@Test
		@DisplayName("Should set both orgUnit and responsibleOrgUnit")
		void shouldSetOrgUnitAndResponsibleOrgUnit() {
			// Arrange
			OrgUnit positionOrgUnit = createOrgUnit("position-org-unit-uuid", null);
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(testUserRole, testOrgUnit);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			CurrentAssignment result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testTitle, positionOrgUnit);

			// Assert
			assertThat(result.getOrgUnit()).isEqualTo(testOrgUnit);
			assertThat(result.getResponsibleOrgUnit()).isEqualTo(positionOrgUnit);
		}
	}

	@Nested
	@DisplayName("OrgUnitRoleGroupAssignment mapping")
	class OrgUnitRoleGroupAssignmentMappingTests {

		@Test
		@DisplayName("Should expand OrgUnit RoleGroup to one assignment per user role, carrying orgUnit, roleGroup and title")
		void shouldExpandOrgUnitRoleGroupToMultipleAssignments() {
			// Arrange
			UserRole role1 = createUserRole("role-1-uuid", testItSystem);
			role1.setId(1L);
			UserRole role2 = createUserRole("role-2-uuid", testItSystem);
			role2.setId(2L);
			RoleGroup roleGroup = createRoleGroup(1L, List.of(role1, role2));

			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, testOrgUnit);
			assignment.setContainsTitles(ContainsTitles.POSITIVE);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			Set<CurrentAssignment> result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testTitle, testOrgUnit);

			// Assert
			assertThat(result).hasSize(2)
				.allMatch(ca -> ca.getOrgUnit() == testOrgUnit && ca.getRoleGroup() == roleGroup && ca.getTitle() == testTitle)
				.extracting(CurrentAssignment::getUserRole)
				.containsExactlyInAnyOrder(role1, role2);
		}

		@Test
		@DisplayName("Should stamp title on expanded assignments and record that titles are NEGATIVE")
		void shouldStampTitleAndRecordNegativeContainsTitles() {
			// Arrange
			UserRole role1 = createUserRole("role-1-uuid", testItSystem);
			role1.setId(1L);
			RoleGroup roleGroup = createRoleGroup(1L, List.of(role1));

			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, testOrgUnit);
			assignment.setContainsTitles(ContainsTitles.NEGATIVE);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			Set<CurrentAssignment> result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testTitle, testOrgUnit);

			// Assert
			// Assert - without the flag the stamped title would read as "assigned through title"
			assertThat(result).allMatch(ca -> ca.getTitle() == testTitle && ca.getContainsTitles() == ContainsTitles.NEGATIVE);
		}

		@Test
		@DisplayName("Should set title on the roleGroup-only row when the role group has no user roles")
		void shouldSetTitleOnRoleGroupOnlyRow() {
			// Arrange
			RoleGroup emptyRoleGroup = createRoleGroup(1L, Collections.emptyList());

			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(emptyRoleGroup, testOrgUnit);
			assignment.setContainsTitles(ContainsTitles.POSITIVE);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			Set<CurrentAssignment> result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testTitle, testOrgUnit);

			// Assert
			assertThat(result).hasSize(1);
			CurrentAssignment row = result.iterator().next();
			assertThat(row.isRoleGroupOnly()).isTrue();
			assertThat(row.getTitle()).isEqualTo(testTitle);
		}
	}
}
