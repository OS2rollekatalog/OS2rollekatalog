package dk.digitalidentity.rc.service.assignment.mapper;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentPostponedConstraint;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignmentConstraint;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createFullCurrentAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createNamedOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createPostponedConstraint;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createRoleGroup;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createTitle;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUserRole;
import static org.assertj.core.api.Assertions.assertThat;

class HistoricAssignmentMapperTest {

	private User testUser;
	private UserRole testUserRole;
	private ItSystem testItSystem;
	private OrgUnit testOrgUnit;
	private Title testTitle;
	private RoleGroup testRoleGroup;

	@BeforeEach
	void setUp() {
		testUser = createUser("test-user-uuid");
		testUser.setUserId("test-user-id");
		testUser.setName("Test User");

		testItSystem = createItSystem("test-it-system-uuid");
		testItSystem.setId(1L);
		testItSystem.setName("Test IT System");

		testUserRole = createUserRole("test-user-role-uuid", testItSystem);
		testUserRole.setId(1L);
		testUserRole.setName("Test User Role");
		testUserRole.setDescription("Test User Role Description");
		testUserRole.setSensitiveRole(false);
		testUserRole.setExtraSensitiveRole(false);

		testOrgUnit = createOrgUnit("test-org-unit-uuid", null);
		testOrgUnit.setName("Test OrgUnit");

		testTitle = createTitle("test-title-uuid");
		testTitle.setName("Test Title");

		testRoleGroup = createRoleGroup(1L, List.of(testUserRole));
		testRoleGroup.setName("Test Role Group");
		testRoleGroup.setDescription("Test Role Group Description");
	}

	@Nested
	@DisplayName("AssignedThrough type determination")
	class AssignedThroughTypeDeterminationTests {

		@Test
		@DisplayName("Should set assignedThroughType to TITLE when both title and orgUnit are present")
		void shouldSetAssignedThroughTitleWhenBothTitleAndOrgUnitPresent() {
			// Arrange
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, testOrgUnit, testTitle, null);

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThrough.TITLE);
			assertThat(result.getAssignedThroughTitleUuid()).isEqualTo(testTitle.getUuid());
			assertThat(result.getAssignedThroughTitleName()).isEqualTo(testTitle.getName());
			assertThat(result.getAssignedThroughOUUuid()).isEqualTo(testOrgUnit.getUuid());
			assertThat(result.getAssignedThroughOUName()).isEqualTo(testOrgUnit.getName());
		}

		@Test
		@DisplayName("Should set assignedThroughType to ORGUNIT when only orgUnit is present (no title)")
		void shouldSetAssignedThroughOrgUnitWhenOnlyOrgUnitPresent() {
			// Arrange
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, testOrgUnit, null, null);

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThrough.ORGUNIT);
			assertThat(result.getAssignedThroughOUUuid()).isEqualTo(testOrgUnit.getUuid());
			assertThat(result.getAssignedThroughOUName()).isEqualTo(testOrgUnit.getName());
			assertThat(result.getAssignedThroughTitleUuid()).isNull();
			assertThat(result.getAssignedThroughTitleName()).isNull();
		}

		@Test
		@DisplayName("Should set assignedThroughType to ROLEGROUP when only roleGroup is present (no orgUnit or title)")
		void shouldSetAssignedThroughRoleGroupWhenOnlyRoleGroupPresent() {
			// Arrange
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, null, null, testRoleGroup);

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThrough.ROLEGROUP);
			assertThat(result.getAssignedThroughRoleGroupId()).isEqualTo(testRoleGroup.getId());
			assertThat(result.getAssignedThroughRoleGroupName()).isEqualTo(testRoleGroup.getName());
			assertThat(result.getAssignedThroughOUUuid()).isNull();
			assertThat(result.getAssignedThroughTitleUuid()).isNull();
		}

		@Test
		@DisplayName("Should set assignedThroughType to DIRECT when no orgUnit, title, or roleGroup are present")
		void shouldSetAssignedThroughDirectWhenNoSpecialContext() {
			// Arrange
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, null, null, null);

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThrough.DIRECT);
			assertThat(result.getAssignedThroughOUUuid()).isNull();
			assertThat(result.getAssignedThroughTitleUuid()).isNull();
			assertThat(result.getAssignedThroughRoleGroupId()).isNull();
		}
	}

	@Nested
	@DisplayName("Field mapping")
	class FieldMappingTests {

		@Test
		@DisplayName("Should map user fields correctly")
		void shouldMapUserFieldsCorrectly() {
			// Arrange
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, null, null, null);

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getUserUuid()).isEqualTo(testUser.getUuid());
			assertThat(result.getUserId()).isEqualTo(testUser.getUserId());
			assertThat(result.getUserName()).isEqualTo(testUser.getName());
		}

		@Test
		@DisplayName("Should map userRole fields correctly")
		void shouldMapUserRoleFieldsCorrectly() {
			// Arrange
			testUserRole.setSensitiveRole(true);
			testUserRole.setExtraSensitiveRole(true);
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, null, null, null);

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getUserRoleId()).isEqualTo(testUserRole.getId());
			assertThat(result.getUserRoleName()).isEqualTo(testUserRole.getName());
			assertThat(result.getUserRoleDescription()).isEqualTo(testUserRole.getDescription());
			assertThat(result.getSensitiveRole()).isTrue();
			assertThat(result.getExtraSensitiveRole()).isTrue();
		}

		@Test
		@DisplayName("Should map itSystem fields correctly")
		void shouldMapItSystemFieldsCorrectly() {
			// Arrange
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, null, null, null);

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getItSystemId()).isEqualTo(testItSystem.getId());
			assertThat(result.getItSystemName()).isEqualTo(testItSystem.getName());
		}

		@Test
		@DisplayName("Should map roleGroup fields when roleGroup is present")
		void shouldMapRoleGroupFieldsWhenPresent() {
			// Arrange
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, testOrgUnit, null, testRoleGroup);

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getRoleGroupId()).isEqualTo(testRoleGroup.getId());
			assertThat(result.getRoleGroupName()).isEqualTo(testRoleGroup.getName());
			assertThat(result.getRoleGroupDescription()).isEqualTo(testRoleGroup.getDescription());
		}

		@Test
		@DisplayName("Should set roleGroup fields to null when roleGroup is absent")
		void shouldSetRoleGroupFieldsToNullWhenAbsent() {
			// Arrange
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, null, null, null);

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getRoleGroupId()).isNull();
			assertThat(result.getRoleGroupName()).isNull();
			assertThat(result.getRoleGroupDescription()).isNull();
		}

		@Test
		@DisplayName("Should map validFrom from createdAt and leave validTo null")
		void shouldMapTemporalFields() {
			// Arrange
			LocalDateTime createdAt = LocalDateTime.of(2025, 1, 15, 10, 0);
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, null, null, null);
			currentAssignment.setCreatedAt(createdAt);

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getValidFrom()).isEqualTo(createdAt);
			assertThat(result.getValidTo()).isNull();
		}

		@Test
		@DisplayName("Should map assignedBy field")
		void shouldMapAssignedBy() {
			// Arrange
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, null, null, null);
			currentAssignment.setAssignedBy("admin (admin-user)");

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getAssignedBy()).isEqualTo("admin (admin-user)");
		}

		@Test
		@DisplayName("Should map postponed constraints with correct fields")
		void shouldMapPostponedConstraints() {
			// Arrange
			CurrentAssignmentPostponedConstraint constraint = createPostponedConstraint(
				"constraint-type-uuid", "It-system", "entity-id", List.of("42", "99")
			);
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, null, null, null);
			currentAssignment.setPostponedConstraints(new HashSet<>(Set.of(constraint)));

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getConstraints()).hasSize(1);
			HistoricAssignmentConstraint mapped = result.getConstraints().iterator().next();
			assertThat(mapped.getConstraintTypeUuid()).isEqualTo("constraint-type-uuid");
			assertThat(mapped.getConstraintTypeName()).isEqualTo("It-system");
			assertThat(mapped.getConstraintTypeEntityId()).isEqualTo("entity-id");
			assertThat(mapped.getValue()).containsExactlyInAnyOrder("42", "99");
		}

		@Test
		@DisplayName("Should produce no constraints when postponedConstraints is empty")
		void shouldProduceNoConstraintsWhenEmpty() {
			// Arrange
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, null, null, null);

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getConstraints()).isEmpty();
		}
	}

	@Nested
	@DisplayName("Responsible resolution")
	class ResponsibleResolutionTests {

		@Test
		@DisplayName("Should set responsibleOUUuid and responsibleOUName when OU manager is responsible")
		void shouldSetResponsibleOuWhenOuManagerIsResponsible() {
			// Arrange
			OrgUnit responsibleOu = createNamedOrgUnit("responsible-ou-uuid", "Responsible OU");
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, null, null, null);
			currentAssignment.setResponsibleOrgUnit(responsibleOu);

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getResponsibleOUUuid()).isEqualTo("responsible-ou-uuid");
			assertThat(result.getResponsibleOUName()).isEqualTo("Responsible OU");
			assertThat(result.getResponsibleUserUuid()).isNull();
		}

		@Test
		@DisplayName("Should set responsibleUserUuid and clear OU fields when IT system responsible is set and role flag is true")
		void shouldSetResponsibleUserWhenItSystemResponsibleFlagIsTrue() {
			// Arrange
			User responsible = createUser("it-system-responsible-uuid");
			testItSystem.setAttestationResponsible(responsible);
			testUserRole.setRoleAssignmentAttestationByAttestationResponsible(true);

			OrgUnit responsibleOu = createNamedOrgUnit("responsible-ou-uuid", "Responsible OU");
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, null, null, null);
			currentAssignment.setResponsibleOrgUnit(responsibleOu);

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getResponsibleUserUuid()).isEqualTo("it-system-responsible-uuid");
			assertThat(result.getResponsibleOUUuid()).isNull();
			assertThat(result.getResponsibleOUName()).isNull();
		}

		@Test
		@DisplayName("Should not set responsibleUserUuid for role group assignments, even if role flag is true")
		void shouldNotSetResponsibleUserForRoleGroupAssignments() {
			// Arrange
			User responsible = createUser("it-system-responsible-uuid");
			testItSystem.setAttestationResponsible(responsible);
			testUserRole.setRoleAssignmentAttestationByAttestationResponsible(true);

			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, null, null, testRoleGroup);

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getResponsibleUserUuid()).isNull();
		}

		@Test
		@DisplayName("Should set both responsible fields to null when no responsible OU is set")
		void shouldLeaveResponsibleNullWhenNoResponsibleOuIsSet() {
			// Arrange
			CurrentAssignment currentAssignment = createFullCurrentAssignment(testUser, testUserRole, testItSystem, null, null, null);
			currentAssignment.setResponsibleOrgUnit(null);

			// Act
			HistoricAssignment result = HistoricAssignmentMapper.createFromCurrentAssignment(currentAssignment);

			// Assert
			assertThat(result.getResponsibleUserUuid()).isNull();
			assertThat(result.getResponsibleOUUuid()).isNull();
			assertThat(result.getResponsibleOUName()).isNull();
		}
	}
}
