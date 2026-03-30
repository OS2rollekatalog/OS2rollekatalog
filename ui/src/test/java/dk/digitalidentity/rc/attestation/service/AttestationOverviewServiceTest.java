package dk.digitalidentity.rc.attestation.service;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createOrgUnitDto;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUserAttestationDto;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUserDto;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUserRoleDto;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dk.digitalidentity.rc.attestation.model.dto.AttestationOverviewDTO;
import dk.digitalidentity.rc.attestation.model.dto.ItSystemAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.ItSystemRoleAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrgUnitUserRoleAssignmentItSystemDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrganisationAttestationDTO;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.OrgUnitService;

@ExtendWith(MockitoExtension.class)
@DisplayName("Attestation Overview Service Tests")
class AttestationOverviewServiceTest {

	@Mock
	private OrgUnitService orgUnitService;

	@InjectMocks
	private AttestationOverviewService attestationOverviewService;

	@Nested
	@DisplayName("buildItSystemUsersOverview() Tests")
	class BuildItSystemUsersOverviewTests {

		@Test
		@DisplayName("Should build overview with correct counts when all users verified")
		void buildItSystemUsersOverview_WhenAllVerified_ShouldShowCorrectCounts() {
			// Arrange
			ItSystemRoleAttestationDTO dto = ItSystemRoleAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.itSystemName("Test System")
					.itSystemId(1L)
					.deadline(LocalDate.now().plusDays(7))
					.users(List.of(
							createUserDto("user1", "verifier1", null),
							createUserDto("user2", "verifier2", null)
					))
					.orgUnits(Collections.emptyList())
					.build();

			// Act
			AttestationOverviewDTO result = AttestationOverviewService.buildItSystemUsersOverview(dto, false);

			// Assert
			assertEquals(2, result.getNumberAttestated());
			assertEquals(0, result.getNumberToAttestate());
			assertEquals(2, result.getTotalNumber());
			assertEquals("Test System", result.getName());
			assertEquals("1", result.getId());
			assertFalse(result.isReadOnly());
		}

		@Test
		@DisplayName("Should build overview with correct counts when some users not verified")
		void buildItSystemUsersOverview_WhenSomeNotVerified_ShouldShowCorrectCounts() {
			// Arrange
			ItSystemRoleAttestationDTO dto = ItSystemRoleAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.itSystemName("Test System")
					.itSystemId(1L)
					.deadline(LocalDate.now().plusDays(7))
					.users(List.of(
							createUserDto("user1", "verifier1", null),
							createUserDto("user2", null, null),
							createUserDto("user3", null, null)
					))
					.orgUnits(Collections.emptyList())
					.build();

			// Act
			AttestationOverviewDTO result = AttestationOverviewService.buildItSystemUsersOverview(dto, false);

			// Assert
			assertEquals(1, result.getNumberAttestated());
			assertEquals(2, result.getNumberToAttestate());
			assertEquals(3, result.getTotalNumber());
		}

		@Test
		@DisplayName("Should count users with remarks as verified")
		void buildItSystemUsersOverview_WhenUserHasRemarks_ShouldCountAsVerified() {
			// Arrange
			ItSystemRoleAttestationDTO dto = ItSystemRoleAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.itSystemName("Test System")
					.itSystemId(1L)
					.deadline(LocalDate.now().plusDays(7))
					.users(List.of(
							createUserDto("user1", null, "Some remarks")
					))
					.orgUnits(Collections.emptyList())
					.build();

			// Act
			AttestationOverviewDTO result = AttestationOverviewService.buildItSystemUsersOverview(dto, false);

			// Assert
			assertEquals(1, result.getNumberAttestated());
			assertEquals(0, result.getNumberToAttestate());
		}

		@Test
		@DisplayName("Should build overview with correct org unit counts")
		void buildItSystemUsersOverview_WithOrgUnits_ShouldShowCorrectOrgUnitCounts() {
			// Arrange
			ItSystemRoleAttestationDTO dto = ItSystemRoleAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.itSystemName("Test System")
					.itSystemId(1L)
					.deadline(LocalDate.now().plusDays(7))
					.users(Collections.emptyList())
					.orgUnits(List.of(
							createOrgUnitDto("ou1", "verifier1", null),
							createOrgUnitDto("ou2", null, null),
							createOrgUnitDto("ou3", null, "Remarks")
					))
					.build();

			// Act
			AttestationOverviewDTO result = AttestationOverviewService.buildItSystemUsersOverview(dto, false);

			// Assert
			assertEquals(2, result.getOrgUnitNumberAttestated());
			assertEquals(1, result.getOrgUnitNumberToAttestate());
			assertEquals(3, result.getOrgUnitTotalNumber());
		}

		@Test
		@DisplayName("Should mark as passed deadline when deadline is in the past")
		void buildItSystemUsersOverview_WhenDeadlinePassed_ShouldMarkAsPassedDeadline() {
			// Arrange
			ItSystemRoleAttestationDTO dto = ItSystemRoleAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.itSystemName("Test System")
					.itSystemId(1L)
					.deadline(LocalDate.now().minusDays(1))
					.users(Collections.emptyList())
					.orgUnits(Collections.emptyList())
					.build();

			// Act
			AttestationOverviewDTO result = AttestationOverviewService.buildItSystemUsersOverview(dto, false);

			// Assert
			assertTrue(result.isPassedDeadline());
		}

		@Test
		@DisplayName("Should not mark as passed deadline when deadline is today or future")
		void buildItSystemUsersOverview_WhenDeadlineNotPassed_ShouldNotMarkAsPassedDeadline() {
			// Arrange
			ItSystemRoleAttestationDTO dto = ItSystemRoleAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.itSystemName("Test System")
					.itSystemId(1L)
					.deadline(LocalDate.now())
					.users(Collections.emptyList())
					.orgUnits(Collections.emptyList())
					.build();

			// Act
			AttestationOverviewDTO result = AttestationOverviewService.buildItSystemUsersOverview(dto, false);

			// Assert
			assertFalse(result.isPassedDeadline());
		}

		@Test
		@DisplayName("Should set readOnly flag correctly")
		void buildItSystemUsersOverview_WithReadOnlyTrue_ShouldSetReadOnlyFlag() {
			// Arrange
			ItSystemRoleAttestationDTO dto = ItSystemRoleAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.itSystemName("Test System")
					.itSystemId(1L)
					.deadline(LocalDate.now().plusDays(7))
					.users(Collections.emptyList())
					.orgUnits(Collections.emptyList())
					.build();

			// Act
			AttestationOverviewDTO result = AttestationOverviewService.buildItSystemUsersOverview(dto, true);

			// Assert
			assertTrue(result.isReadOnly());
		}
	}

	@Nested
	@DisplayName("buildItSystemOverview() Tests")
	class BuildItSystemOverviewTests {

		@Test
		@DisplayName("Should build overview with correct counts when all roles verified")
		void buildItSystemOverview_WhenAllVerified_ShouldShowCorrectCounts() {
			// Arrange
			ItSystemAttestationDTO dto = ItSystemAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.itSystemName("Test System")
					.itSystemId(1L)
					.deadLine(LocalDate.now().plusDays(7))
					.userRoles(List.of(
							createUserRoleDto("role1", "verifier1", null),
							createUserRoleDto("role2", "verifier2", null)
					))
					.build();

			// Act
			AttestationOverviewDTO result = AttestationOverviewService.buildItSystemOverview(dto, false);

			// Assert
			assertEquals(2, result.getNumberAttestated());
			assertEquals(0, result.getNumberToAttestate());
			assertEquals(2, result.getTotalNumber());
		}

		@Test
		@DisplayName("Should build overview with correct counts when some roles not verified")
		void buildItSystemOverview_WhenSomeNotVerified_ShouldShowCorrectCounts() {
			// Arrange
			ItSystemAttestationDTO dto = ItSystemAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.itSystemName("Test System")
					.itSystemId(1L)
					.deadLine(LocalDate.now().plusDays(7))
					.userRoles(List.of(
							createUserRoleDto("role1", "verifier1", null),
							createUserRoleDto("role2", null, null),
							createUserRoleDto("role3", null, "Remarks")
					))
					.build();

			// Act
			AttestationOverviewDTO result = AttestationOverviewService.buildItSystemOverview(dto, false);

			// Assert
			assertEquals(2, result.getNumberAttestated()); // verifier1 and Remarks
			assertEquals(1, result.getNumberToAttestate());
			assertEquals(3, result.getTotalNumber());
		}
	}

	@Nested
	@DisplayName("buildItSystemsOverviews() Tests")
	class BuildItSystemsOverviewsTests {

		@Test
		@DisplayName("Should build overviews for multiple IT systems")
		void buildItSystemsOverviews_WithMultipleSystems_ShouldBuildAll() {
			// Arrange
			List<ItSystemAttestationDTO> systems = List.of(
					ItSystemAttestationDTO.builder()
							.createdAt(LocalDate.now())
							.itSystemName("System 1")
							.itSystemId(1L)
							.deadLine(LocalDate.now().plusDays(7))
							.userRoles(Collections.emptyList())
							.build(),
					ItSystemAttestationDTO.builder()
							.createdAt(LocalDate.now())
							.itSystemName("System 2")
							.itSystemId(2L)
							.deadLine(LocalDate.now().plusDays(7))
							.userRoles(Collections.emptyList())
							.build()
			);

			// Act
			List<AttestationOverviewDTO> results = AttestationOverviewService.buildItSystemsOverviews(systems, false);

			// Assert
			assertEquals(2, results.size());
			assertEquals("System 1", results.get(0).getName());
			assertEquals("System 2", results.get(1).getName());
		}

		@Test
		@DisplayName("Should return empty list when no systems provided")
		void buildItSystemsOverviews_WhenEmpty_ShouldReturnEmptyList() {
			// Act
			List<AttestationOverviewDTO> results = AttestationOverviewService.buildItSystemsOverviews(Collections.emptyList(), false);

			// Assert
			assertTrue(results.isEmpty());
		}
	}

	@Nested
	@DisplayName("buildOrgUnitOverview() Tests")
	class BuildOrgUnitOverviewTests {

		@Test
		@DisplayName("Should build overview with correct user counts")
		void buildOrgUnitOverview_ShouldShowCorrectUserCounts() {
			// Arrange
			OrganisationAttestationDTO dto = OrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(List.of(
							createUserAttestationDto("user1", "verifier1", null, false),
							createUserAttestationDto("user2", null, null, false),
							createUserAttestationDto("user3", null, "Remarks", false)
					))
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.build();

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(null);

			// Act
			AttestationOverviewDTO result = attestationOverviewService.buildOrgUnitOverview(dto, false);

			// Assert
			assertEquals(2, result.getNumberAttestated()); // verifier1 and Remarks
			assertEquals(1, result.getNumberToAttestate());
			assertEquals(3, result.getTotalNumber());
			assertEquals("Test OU", result.getName());
			assertEquals("ou-uuid", result.getId());
		}

		@Test
		@DisplayName("Should count AD removal users as verified")
		void buildOrgUnitOverview_WhenUserHasAdRemoval_ShouldCountAsVerified() {
			// Arrange
			OrganisationAttestationDTO dto = OrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(List.of(
							createUserAttestationDto("user1", null, null, true)
					))
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.build();

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(null);

			// Act
			AttestationOverviewDTO result = attestationOverviewService.buildOrgUnitOverview(dto, false);

			// Assert
			assertEquals(1, result.getNumberAttestated());
			assertEquals(0, result.getNumberToAttestate());
		}

		@Test
		@DisplayName("Should show org unit roles attestation status when roles exist and verified")
		void buildOrgUnitOverview_WhenOrgRolesExistAndVerified_ShouldShowCorrectStatus() {
			// Arrange
			OrganisationAttestationDTO dto = OrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(List.of(new OrgUnitUserRoleAssignmentItSystemDTO()))
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.orgUnitRolesVerified(true)
					.build();

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(null);

			// Act
			AttestationOverviewDTO result = attestationOverviewService.buildOrgUnitOverview(dto, false);

			// Assert
			assertEquals(1, result.getOrgUnitNumberAttestated());
			assertEquals(0, result.getOrgUnitNumberToAttestate());
			assertEquals(1, result.getOrgUnitTotalNumber());
		}

		@Test
		@DisplayName("Should show org unit roles not attested when roles exist but not verified")
		void buildOrgUnitOverview_WhenOrgRolesExistButNotVerified_ShouldShowCorrectStatus() {
			// Arrange
			OrganisationAttestationDTO dto = OrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(List.of(new OrgUnitUserRoleAssignmentItSystemDTO()))
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.orgUnitRolesVerified(false)
					.build();

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(null);

			// Act
			AttestationOverviewDTO result = attestationOverviewService.buildOrgUnitOverview(dto, false);

			// Assert
			assertEquals(0, result.getOrgUnitNumberAttestated());
			assertEquals(1, result.getOrgUnitNumberToAttestate());
			assertEquals(1, result.getOrgUnitTotalNumber());
		}

		@Test
		@DisplayName("Should show zero org unit counts when no org roles exist")
		void buildOrgUnitOverview_WhenNoOrgRoles_ShouldShowZeroCounts() {
			// Arrange
			OrganisationAttestationDTO dto = OrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.build();

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(null);

			// Act
			AttestationOverviewDTO result = attestationOverviewService.buildOrgUnitOverview(dto, false);

			// Assert
			assertEquals(0, result.getOrgUnitNumberAttestated());
			assertEquals(0, result.getOrgUnitNumberToAttestate());
			assertEquals(0, result.getOrgUnitTotalNumber());
		}

		@Test
		@DisplayName("Should return empty substitutes when OrgUnit is null")
		void buildOrgUnitOverview_WhenOrgUnitIsNull_ShouldReturnEmptySubstitutes() {
			// Arrange
			OrganisationAttestationDTO dto = OrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.build();

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(null);

			// Act
			AttestationOverviewDTO result = attestationOverviewService.buildOrgUnitOverview(dto, false);

			// Assert
			assertNotNull(result.getSubstitutes());
			assertTrue(result.getSubstitutes().isEmpty());
		}

		@Test
		@DisplayName("Should return empty substitutes when OrgUnit has no manager")
		void buildOrgUnitOverview_WhenOrgUnitHasNoManager_ShouldReturnEmptySubstitutes() {
			// Arrange
			OrganisationAttestationDTO dto = OrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.build();

			OrgUnit orgUnit = new OrgUnit();
			orgUnit.setUuid("ou-uuid");
			orgUnit.setManager(null);

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(orgUnit);

			// Act
			AttestationOverviewDTO result = attestationOverviewService.buildOrgUnitOverview(dto, false);

			// Assert
			assertNotNull(result.getSubstitutes());
			assertTrue(result.getSubstitutes().isEmpty());
		}

		@Test
		@DisplayName("Should return substitutes with null orgUnit (global substitutes)")
		void buildOrgUnitOverview_WhenManagerHasGlobalSubstitutes_ShouldReturnSubstituteNames() {
			// Arrange
			OrganisationAttestationDTO dto = OrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.build();

			User manager = new User();
			manager.setUuid("manager-uuid");

			User substitute1 = new User();
			substitute1.setName("Substitute One");

			User substitute2 = new User();
			substitute2.setName("Substitute Two");

			ManagerSubstitute managerSubstitute1 = new ManagerSubstitute();
			managerSubstitute1.setSubstitute(substitute1);
			managerSubstitute1.setOrgUnit(null); // Global substitute

			ManagerSubstitute managerSubstitute2 = new ManagerSubstitute();
			managerSubstitute2.setSubstitute(substitute2);
			managerSubstitute2.setOrgUnit(null); // Global substitute

			manager.setManagerSubstitutes(List.of(managerSubstitute1, managerSubstitute2));

			OrgUnit orgUnit = new OrgUnit();
			orgUnit.setUuid("ou-uuid");
			orgUnit.setManager(manager);

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(orgUnit);

			// Act
			AttestationOverviewDTO result = attestationOverviewService.buildOrgUnitOverview(dto, false);

			// Assert
			assertNotNull(result.getSubstitutes());
			assertEquals(2, result.getSubstitutes().size());
			assertTrue(result.getSubstitutes().contains("Substitute One"));
			assertTrue(result.getSubstitutes().contains("Substitute Two"));
		}

		@Test
		@DisplayName("Should return substitutes assigned to the same OrgUnit")
		void buildOrgUnitOverview_WhenManagerHasOuSpecificSubstitutes_ShouldReturnMatchingSubstituteNames() {
			// Arrange
			OrganisationAttestationDTO dto = OrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.build();

			OrgUnit targetOrgUnit = new OrgUnit();
			targetOrgUnit.setUuid("ou-uuid");

			OrgUnit otherOrgUnit = new OrgUnit();
			otherOrgUnit.setUuid("other-ou-uuid");

			User manager = new User();
			manager.setUuid("manager-uuid");

			User substituteForTargetOu = new User();
			substituteForTargetOu.setName("Substitute For Target OU");

			User substituteForOtherOu = new User();
			substituteForOtherOu.setName("Substitute For Other OU");

			ManagerSubstitute managerSubstitute1 = new ManagerSubstitute();
			managerSubstitute1.setSubstitute(substituteForTargetOu);
			managerSubstitute1.setOrgUnit(targetOrgUnit); // Matches the attestation OU

			ManagerSubstitute managerSubstitute2 = new ManagerSubstitute();
			managerSubstitute2.setSubstitute(substituteForOtherOu);
			managerSubstitute2.setOrgUnit(otherOrgUnit); // Does NOT match the attestation OU

			manager.setManagerSubstitutes(List.of(managerSubstitute1, managerSubstitute2));

			targetOrgUnit.setManager(manager);

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(targetOrgUnit);

			// Act
			AttestationOverviewDTO result = attestationOverviewService.buildOrgUnitOverview(dto, false);

			// Assert
			assertNotNull(result.getSubstitutes());
			assertEquals(1, result.getSubstitutes().size());
			assertTrue(result.getSubstitutes().contains("Substitute For Target OU"));
			assertFalse(result.getSubstitutes().contains("Substitute For Other OU"));
		}

		@Test
		@DisplayName("Should return both global and OU-specific substitutes")
		void buildOrgUnitOverview_WhenManagerHasMixedSubstitutes_ShouldReturnBothTypes() {
			// Arrange
			OrganisationAttestationDTO dto = OrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.build();

			OrgUnit targetOrgUnit = new OrgUnit();
			targetOrgUnit.setUuid("ou-uuid");

			User manager = new User();
			manager.setUuid("manager-uuid");

			User globalSubstitute = new User();
			globalSubstitute.setName("Global Substitute");

			User ouSpecificSubstitute = new User();
			ouSpecificSubstitute.setName("OU Specific Substitute");

			ManagerSubstitute globalManagerSubstitute = new ManagerSubstitute();
			globalManagerSubstitute.setSubstitute(globalSubstitute);
			globalManagerSubstitute.setOrgUnit(null); // Global

			ManagerSubstitute ouSpecificManagerSubstitute = new ManagerSubstitute();
			ouSpecificManagerSubstitute.setSubstitute(ouSpecificSubstitute);
			ouSpecificManagerSubstitute.setOrgUnit(targetOrgUnit); // OU-specific

			manager.setManagerSubstitutes(List.of(globalManagerSubstitute, ouSpecificManagerSubstitute));

			targetOrgUnit.setManager(manager);

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(targetOrgUnit);

			// Act
			AttestationOverviewDTO result = attestationOverviewService.buildOrgUnitOverview(dto, false);

			// Assert
			assertNotNull(result.getSubstitutes());
			assertEquals(2, result.getSubstitutes().size());
			assertTrue(result.getSubstitutes().contains("Global Substitute"));
			assertTrue(result.getSubstitutes().contains("OU Specific Substitute"));
		}

		@Test
		@DisplayName("Should return empty substitutes when manager has no substitutes")
		void buildOrgUnitOverview_WhenManagerHasNoSubstitutes_ShouldReturnEmptySubstitutes() {
			// Arrange
			OrganisationAttestationDTO dto = OrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.build();

			User manager = new User();
			manager.setUuid("manager-uuid");
			manager.setManagerSubstitutes(Collections.emptyList());

			OrgUnit orgUnit = new OrgUnit();
			orgUnit.setUuid("ou-uuid");
			orgUnit.setManager(manager);

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(orgUnit);

			// Act
			AttestationOverviewDTO result = attestationOverviewService.buildOrgUnitOverview(dto, false);

			// Assert
			assertNotNull(result.getSubstitutes());
			assertTrue(result.getSubstitutes().isEmpty());
		}
	}

	@Nested
	@DisplayName("buildOrgUnitsOverviews() Tests")
	class BuildOrgUnitsOverviewsTests {

		@Test
		@DisplayName("Should build overviews for multiple org units")
		void buildOrgUnitsOverviews_WithMultipleOUs_ShouldBuildAll() {
			// Arrange
			List<OrganisationAttestationDTO> orgs = List.of(
					OrganisationAttestationDTO.builder()
							.createdAt(LocalDate.now())
							.ouUuid("ou-1")
							.ouName("OU 1")
							.deadLine(LocalDate.now().plusDays(7))
							.userAttestations(Collections.emptyList())
							.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
							.orgUnitRoleGroupAssignments(Collections.emptyList())
							.build(),
					OrganisationAttestationDTO.builder()
							.createdAt(LocalDate.now())
							.ouUuid("ou-2")
							.ouName("OU 2")
							.deadLine(LocalDate.now().plusDays(7))
							.userAttestations(Collections.emptyList())
							.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
							.orgUnitRoleGroupAssignments(Collections.emptyList())
							.build()
			);

			when(orgUnitService.getByUuid("ou-1")).thenReturn(null);
			when(orgUnitService.getByUuid("ou-2")).thenReturn(null);

			// Act
			List<AttestationOverviewDTO> results = attestationOverviewService.buildOrgUnitsOverviews(orgs, false);

			// Assert
			assertEquals(2, results.size());
			assertEquals("OU 1", results.get(0).getName());
			assertEquals("OU 2", results.get(1).getName());
		}
	}
}
