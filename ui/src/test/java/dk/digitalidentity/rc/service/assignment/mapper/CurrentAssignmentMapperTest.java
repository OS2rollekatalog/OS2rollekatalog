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

import java.time.LocalDate;
import java.util.Collections;
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
		@DisplayName("Should return empty set when RoleGroup has no UserRoles")
		void shouldReturnEmptySetWhenRoleGroupHasNoUserRoles() {
			// Arrange
			RoleGroup emptyRoleGroup = createRoleGroup(1L, Collections.emptyList());

			UserRoleGroupAssignment assignment = createDirectRoleGroupAssignment(emptyRoleGroup, null, null);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			Set<CurrentAssignment> result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testOrgUnit);

			// Assert
			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("OrgUnitUserRoleAssignment mapping")
	class OrgUnitUserRoleAssignmentMappingTests {

		@Test
		@DisplayName("Should set title when containsTitles is POSITIVE")
		void shouldMapOrgUnitAssignmentWithTitle() {
			// Arrange
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(testUserRole, testOrgUnit);
			assignment.setContainsTitles(ContainsTitles.POSITIVE);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			CurrentAssignment result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testTitle, testOrgUnit);

			// Assert
			assertThat(result.getTitle()).isEqualTo(testTitle);
		}

		@Test
		@DisplayName("Should not set title when containsTitles is NO")
		void shouldNotSetTitleWhenContainsTitlesIsNo() {
			// Arrange
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(testUserRole, testOrgUnit);
			assignment.setContainsTitles(ContainsTitles.NO);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			CurrentAssignment result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testTitle, testOrgUnit);

			// Assert
			assertThat(result.getTitle()).isNull();
		}

		@Test
		@DisplayName("Should not set title when containsTitles is null")
		void shouldNotSetTitleWhenContainsTitlesIsNull() {
			// Arrange
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(testUserRole, testOrgUnit);
			assignment.setContainsTitles(null);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			CurrentAssignment result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testTitle, testOrgUnit);

			// Assert
			assertThat(result.getTitle()).isNull();
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
		@DisplayName("Should expand OrgUnit RoleGroup to multiple CurrentAssignments")
		void shouldExpandOrgUnitRoleGroupToMultipleAssignments() {
			// Arrange
			UserRole role1 = createUserRole("role-1-uuid", testItSystem);
			role1.setId(1L);
			UserRole role2 = createUserRole("role-2-uuid", testItSystem);
			role2.setId(2L);
			RoleGroup roleGroup = createRoleGroup(1L, List.of(role1, role2));

			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, testOrgUnit);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			Set<CurrentAssignment> result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testOrgUnit);

			// Assert
			assertThat(result).hasSize(2)
				.extracting(CurrentAssignment::getUserRole)
				.containsExactlyInAnyOrder(role1, role2);
		}

		@Test
		@DisplayName("Should set orgUnit and roleGroup on all expanded assignments")
		void shouldSetOrgUnitAndRoleGroupOnAllExpandedAssignments() {
			// Arrange
			UserRole role1 = createUserRole("role-1-uuid", testItSystem);
			role1.setId(1L);
			UserRole role2 = createUserRole("role-2-uuid", testItSystem);
			role2.setId(2L);
			RoleGroup roleGroup = createRoleGroup(1L, List.of(role1, role2));

			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, testOrgUnit);
			assignment.setAssignedByName("Assigner");
			assignment.setAssignedByUserId("assigner-id");

			// Act
			Set<CurrentAssignment> result = CurrentAssignmentMapper.toCurrentAssignment(assignment, testUser, testOrgUnit);

			// Assert
			assertThat(result).hasSize(2)
				.allMatch(ca -> ca.getOrgUnit() == testOrgUnit && ca.getRoleGroup() == roleGroup);
		}
	}
}
