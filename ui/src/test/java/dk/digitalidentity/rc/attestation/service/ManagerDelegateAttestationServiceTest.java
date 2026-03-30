package dk.digitalidentity.rc.attestation.service;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUserAttestationDto;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dk.digitalidentity.rc.attestation.model.dto.AttestationOverviewDTO;
import dk.digitalidentity.rc.attestation.model.dto.ManagerDelegateOrganisationAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrgUnitUserRoleAssignmentItSystemDTO;
import dk.digitalidentity.rc.dao.ManagerDelegateDao;
import dk.digitalidentity.rc.dao.model.ManagerDelegate;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.OrgUnitService;

@ExtendWith(MockitoExtension.class)
@DisplayName("Manager Delegate Attestation Service Tests")
class ManagerDelegateAttestationServiceTest {

	@Mock
	private ManagerDelegateDao managerDelegateDao;

	@Mock
	private OrgUnitService orgUnitService;

	@InjectMocks
	private ManagerDelegateAttestationService managerDelegateAttestationService;

	@Nested
	@DisplayName("getManagedUsersForDelegate() Tests")
	class GetManagedUsersForDelegateTests {

		@Test
		@DisplayName("Should return empty list when delegate has no managed users")
		void getManagedUsersForDelegate_WhenNoManagedUsers_ShouldReturnEmptyList() {
			// Arrange
			User delegate = createUser("delegate-uuid", "delegate-id", "Delegate User");
			when(managerDelegateDao.getByDelegateAndFromDateAfterAndToDateBeforeOrIndefinitelyTrue(
					eq(delegate), any(LocalDate.class), any(LocalDate.class)))
					.thenReturn(Collections.emptyList());

			// Act
			List<User> result = managerDelegateAttestationService.getManagedUsersForDelegate(delegate);

			// Assert
			assertNotNull(result);
			assertTrue(result.isEmpty());
		}

		@Test
		@DisplayName("Should return list of managers when delegate has managed users")
		void getManagedUsersForDelegate_WhenHasManagedUsers_ShouldReturnManagerList() {
			// Arrange
			User delegate = createUser("delegate-uuid", "delegate-id", "Delegate User");
			User manager1 = createUser("manager1-uuid", "manager1-id", "Manager One");
			User manager2 = createUser("manager2-uuid", "manager2-id", "Manager Two");

			ManagerDelegate managerDelegate1 = ManagerDelegate.builder()
					.delegate(delegate)
					.manager(manager1)
					.fromDate(LocalDate.now().minusDays(10))
					.toDate(LocalDate.now().plusDays(10))
					.build();

			ManagerDelegate managerDelegate2 = ManagerDelegate.builder()
					.delegate(delegate)
					.manager(manager2)
					.fromDate(LocalDate.now().minusDays(5))
					.indefinitely(true)
					.build();

			when(managerDelegateDao.getByDelegateAndFromDateAfterAndToDateBeforeOrIndefinitelyTrue(
					eq(delegate), any(LocalDate.class), any(LocalDate.class)))
					.thenReturn(List.of(managerDelegate1, managerDelegate2));

			// Act
			List<User> result = managerDelegateAttestationService.getManagedUsersForDelegate(delegate);

			// Assert
			assertNotNull(result);
			assertEquals(2, result.size());
			assertTrue(result.contains(manager1));
			assertTrue(result.contains(manager2));
		}

		@Test
		@DisplayName("Should return single manager when delegate has one managed user")
		void getManagedUsersForDelegate_WhenHasOneManagedUser_ShouldReturnSingleManager() {
			// Arrange
			User delegate = createUser("delegate-uuid", "delegate-id", "Delegate User");
			User manager = createUser("manager-uuid", "manager-id", "Manager");

			ManagerDelegate managerDelegate = ManagerDelegate.builder()
					.delegate(delegate)
					.manager(manager)
					.fromDate(LocalDate.now().minusDays(10))
					.indefinitely(true)
					.build();

			when(managerDelegateDao.getByDelegateAndFromDateAfterAndToDateBeforeOrIndefinitelyTrue(
					eq(delegate), any(LocalDate.class), any(LocalDate.class)))
					.thenReturn(List.of(managerDelegate));

			// Act
			List<User> result = managerDelegateAttestationService.getManagedUsersForDelegate(delegate);

			// Assert
			assertNotNull(result);
			assertEquals(1, result.size());
			assertEquals(manager, result.get(0));
		}
	}

	@Nested
	@DisplayName("buildOrgUnitOverview() Tests")
	class BuildOrgUnitOverviewTests {

		@Test
		@DisplayName("Should build overview with correct user counts")
		void buildOrgUnitOverview_ShouldShowCorrectUserCounts() {
			// Arrange
			ManagerDelegateOrganisationAttestationDTO dto = ManagerDelegateOrganisationAttestationDTO.builder()
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
					.associatedManagerNames(new HashSet<>())
					.build();

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(null);

			// Act
			AttestationOverviewDTO result = managerDelegateAttestationService.buildOrgUnitOverview(dto, false);

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
			ManagerDelegateOrganisationAttestationDTO dto = ManagerDelegateOrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(List.of(
							createUserAttestationDto("user1", null, null, true)
					))
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.associatedManagerNames(new HashSet<>())
					.build();

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(null);

			// Act
			AttestationOverviewDTO result = managerDelegateAttestationService.buildOrgUnitOverview(dto, false);

			// Assert
			assertEquals(1, result.getNumberAttestated());
			assertEquals(0, result.getNumberToAttestate());
		}

		@Test
		@DisplayName("Should show org unit roles attestation status when roles exist and verified")
		void buildOrgUnitOverview_WhenOrgRolesExistAndVerified_ShouldShowCorrectStatus() {
			// Arrange
			ManagerDelegateOrganisationAttestationDTO dto = ManagerDelegateOrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(List.of(new OrgUnitUserRoleAssignmentItSystemDTO()))
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.orgUnitRolesVerified(true)
					.associatedManagerNames(new HashSet<>())
					.build();

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(null);

			// Act
			AttestationOverviewDTO result = managerDelegateAttestationService.buildOrgUnitOverview(dto, false);

			// Assert
			assertEquals(1, result.getOrgUnitNumberAttestated());
			assertEquals(0, result.getOrgUnitNumberToAttestate());
			assertEquals(1, result.getOrgUnitTotalNumber());
		}

		@Test
		@DisplayName("Should show org unit roles not attested when roles exist but not verified")
		void buildOrgUnitOverview_WhenOrgRolesExistButNotVerified_ShouldShowCorrectStatus() {
			// Arrange
			ManagerDelegateOrganisationAttestationDTO dto = ManagerDelegateOrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(List.of(new OrgUnitUserRoleAssignmentItSystemDTO()))
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.orgUnitRolesVerified(false)
					.associatedManagerNames(new HashSet<>())
					.build();

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(null);

			// Act
			AttestationOverviewDTO result = managerDelegateAttestationService.buildOrgUnitOverview(dto, false);

			// Assert
			assertEquals(0, result.getOrgUnitNumberAttestated());
			assertEquals(1, result.getOrgUnitNumberToAttestate());
			assertEquals(1, result.getOrgUnitTotalNumber());
		}

		@Test
		@DisplayName("Should mark as passed deadline when deadline is in the past")
		void buildOrgUnitOverview_WhenDeadlinePassed_ShouldMarkAsPassedDeadline() {
			// Arrange
			ManagerDelegateOrganisationAttestationDTO dto = ManagerDelegateOrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().minusDays(1))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.associatedManagerNames(new HashSet<>())
					.build();

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(null);

			// Act
			AttestationOverviewDTO result = managerDelegateAttestationService.buildOrgUnitOverview(dto, false);

			// Assert
			assertTrue(result.isPassedDeadline());
		}

		@Test
		@DisplayName("Should not mark as passed deadline when deadline is today or future")
		void buildOrgUnitOverview_WhenDeadlineNotPassed_ShouldNotMarkAsPassedDeadline() {
			// Arrange
			ManagerDelegateOrganisationAttestationDTO dto = ManagerDelegateOrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now())
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.associatedManagerNames(new HashSet<>())
					.build();

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(null);

			// Act
			AttestationOverviewDTO result = managerDelegateAttestationService.buildOrgUnitOverview(dto, false);

			// Assert
			assertFalse(result.isPassedDeadline());
		}

		@Test
		@DisplayName("Should set readOnly flag correctly")
		void buildOrgUnitOverview_WithReadOnlyTrue_ShouldSetReadOnlyFlag() {
			// Arrange
			ManagerDelegateOrganisationAttestationDTO dto = ManagerDelegateOrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.associatedManagerNames(new HashSet<>())
					.build();

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(null);

			// Act
			AttestationOverviewDTO result = managerDelegateAttestationService.buildOrgUnitOverview(dto, true);

			// Assert
			assertTrue(result.isReadOnly());
		}

		@Test
		@DisplayName("Should include associated manager names in result")
		void buildOrgUnitOverview_WithAssociatedManagerNames_ShouldIncludeInResult() {
			// Arrange
			Set<String> managerNames = new HashSet<>();
			managerNames.add("Manager One");
			managerNames.add("Manager Two");

			ManagerDelegateOrganisationAttestationDTO dto = ManagerDelegateOrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.associatedManagerNames(managerNames)
					.build();

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(null);

			// Act
			AttestationOverviewDTO result = managerDelegateAttestationService.buildOrgUnitOverview(dto, false);

			// Assert
			assertNotNull(result.getManagerNames());
			assertEquals(2, result.getManagerNames().size());
			assertTrue(result.getManagerNames().contains("Manager One"));
			assertTrue(result.getManagerNames().contains("Manager Two"));
		}

		@Test
		@DisplayName("Should return substitutes when manager has global substitutes")
		void buildOrgUnitOverview_WhenManagerHasGlobalSubstitutes_ShouldReturnSubstituteNames() {
			// Arrange
			ManagerDelegateOrganisationAttestationDTO dto = ManagerDelegateOrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.associatedManagerNames(new HashSet<>())
					.build();

			User manager = createUser("manager-uuid", "manager-id", "Manager");
			User substitute = createUser("substitute-uuid", "substitute-id", "Substitute User");

			ManagerSubstitute managerSubstitute = new ManagerSubstitute();
			managerSubstitute.setSubstitute(substitute);
			managerSubstitute.setOrgUnit(null); // Global substitute

			manager.setManagerSubstitutes(List.of(managerSubstitute));

			OrgUnit orgUnit = new OrgUnit();
			orgUnit.setUuid("ou-uuid");
			orgUnit.setManager(manager);

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(orgUnit);

			// Act
			AttestationOverviewDTO result = managerDelegateAttestationService.buildOrgUnitOverview(dto, false);

			// Assert
			assertNotNull(result.getSubstitutes());
			assertEquals(1, result.getSubstitutes().size());
			assertTrue(result.getSubstitutes().contains("Substitute User"));
		}

		@Test
		@DisplayName("Should return substitutes assigned to the same OrgUnit")
		void buildOrgUnitOverview_WhenManagerHasOuSpecificSubstitutes_ShouldReturnMatchingSubstituteNames() {
			// Arrange
			ManagerDelegateOrganisationAttestationDTO dto = ManagerDelegateOrganisationAttestationDTO.builder()
					.createdAt(LocalDate.now())
					.ouUuid("ou-uuid")
					.ouName("Test OU")
					.deadLine(LocalDate.now().plusDays(7))
					.userAttestations(Collections.emptyList())
					.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
					.orgUnitRoleGroupAssignments(Collections.emptyList())
					.associatedManagerNames(new HashSet<>())
					.build();

			OrgUnit targetOrgUnit = new OrgUnit();
			targetOrgUnit.setUuid("ou-uuid");

			OrgUnit otherOrgUnit = new OrgUnit();
			otherOrgUnit.setUuid("other-ou-uuid");

			User manager = createUser("manager-uuid", "manager-id", "Manager");

			User substituteForTargetOu = createUser("sub1-uuid", "sub1-id", "Substitute For Target OU");
			User substituteForOtherOu = createUser("sub2-uuid", "sub2-id", "Substitute For Other OU");

			ManagerSubstitute managerSubstitute1 = new ManagerSubstitute();
			managerSubstitute1.setSubstitute(substituteForTargetOu);
			managerSubstitute1.setOrgUnit(targetOrgUnit);

			ManagerSubstitute managerSubstitute2 = new ManagerSubstitute();
			managerSubstitute2.setSubstitute(substituteForOtherOu);
			managerSubstitute2.setOrgUnit(otherOrgUnit);

			manager.setManagerSubstitutes(List.of(managerSubstitute1, managerSubstitute2));
			targetOrgUnit.setManager(manager);

			when(orgUnitService.getByUuid("ou-uuid")).thenReturn(targetOrgUnit);

			// Act
			AttestationOverviewDTO result = managerDelegateAttestationService.buildOrgUnitOverview(dto, false);

			// Assert
			assertNotNull(result.getSubstitutes());
			assertEquals(1, result.getSubstitutes().size());
			assertTrue(result.getSubstitutes().contains("Substitute For Target OU"));
			assertFalse(result.getSubstitutes().contains("Substitute For Other OU"));
		}
	}

	@Nested
	@DisplayName("buildOrgUnitsOverviews() Tests")
	class BuildOrgUnitsOverviewsTests {

		@Test
		@DisplayName("Should build overviews for multiple org units")
		void buildOrgUnitsOverviews_WithMultipleOUs_ShouldBuildAll() {
			// Arrange
			List<ManagerDelegateOrganisationAttestationDTO> orgs = List.of(
					ManagerDelegateOrganisationAttestationDTO.builder()
							.createdAt(LocalDate.now())
							.ouUuid("ou-1")
							.ouName("OU 1")
							.deadLine(LocalDate.now().plusDays(7))
							.userAttestations(Collections.emptyList())
							.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
							.orgUnitRoleGroupAssignments(Collections.emptyList())
							.associatedManagerNames(new HashSet<>())
							.build(),
					ManagerDelegateOrganisationAttestationDTO.builder()
							.createdAt(LocalDate.now())
							.ouUuid("ou-2")
							.ouName("OU 2")
							.deadLine(LocalDate.now().plusDays(7))
							.userAttestations(Collections.emptyList())
							.orgUnitUserRoleAssignmentsPrItSystem(Collections.emptyList())
							.orgUnitRoleGroupAssignments(Collections.emptyList())
							.associatedManagerNames(new HashSet<>())
							.build()
			);

			when(orgUnitService.getByUuid("ou-1")).thenReturn(null);
			when(orgUnitService.getByUuid("ou-2")).thenReturn(null);

			User currentUser = createUser("current-user-uuid", "current-user-id", "Current User");

			// Act
			List<AttestationOverviewDTO> results = managerDelegateAttestationService.buildOrgUnitsOverviews(orgs, currentUser, false);

			// Assert
			assertEquals(2, results.size());
			assertEquals("OU 1", results.get(0).getName());
			assertEquals("OU 2", results.get(1).getName());
		}

		@Test
		@DisplayName("Should return empty list when no orgs provided")
		void buildOrgUnitsOverviews_WhenEmpty_ShouldReturnEmptyList() {
			// Arrange
			User currentUser = createUser("current-user-uuid", "current-user-id", "Current User");

			// Act
			List<AttestationOverviewDTO> results = managerDelegateAttestationService.buildOrgUnitsOverviews(
					Collections.emptyList(), currentUser, false);

			// Assert
			assertTrue(results.isEmpty());
		}
	}

}
