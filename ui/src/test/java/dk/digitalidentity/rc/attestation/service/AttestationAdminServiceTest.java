package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.ItSystemRoleAttestationEntryDao;
import dk.digitalidentity.rc.attestation.dao.ItSystemUserAttestationEntryDao;
import dk.digitalidentity.rc.attestation.dao.OrganisationUserAttestationEntryDao;
import dk.digitalidentity.rc.attestation.model.AttestationMailMapper;
import dk.digitalidentity.rc.attestation.model.dto.AdminAttestationDetailsDTO;
import dk.digitalidentity.rc.attestation.model.dto.AttestationOverviewDTO;
import dk.digitalidentity.rc.attestation.model.dto.ItSystemAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.ItSystemRoleAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrganisationAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.AdminAttestationStatus;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Attestation Admin Service Tests")
class AttestationAdminServiceTest {

	@Mock
	private OrganisationUserAttestationEntryDao organisationUserAttestationEntryDao;

	@Mock
	private ItSystemUserAttestationEntryDao itSystemUserAttestationEntryDao;

	@Mock
	private ItSystemRoleAttestationEntryDao itSystemRoleAttestationEntryDao;

	@Mock
	private ItSystemUserRolesAttestationService itSystemUserRolesAttestationService;

	@Mock
	private ItSystemUsersAttestationService itSystemUsersAttestationService;

	@Mock
	private OrganisationAttestationService organisationAttestationService;

	@Mock
	private AttestationOverviewService attestationOverviewService;

	@Mock
	private AttestationMailMapper mailMapper;

	@Mock
	private ManagerDelegateAttestationService managerDelegateAttestationService;

	@InjectMocks
	private AttestationAdminService attestationAdminService;

	private Attestation attestation;
	private AttestationOverviewDTO mockOverview;

	@BeforeEach
	void setUp() {
		attestation = new Attestation();
		attestation.setId(1L);
		mockOverview = new AttestationOverviewDTO();
	}

	@Nested
	@DisplayName("findAttestationDetails() Tests")
	class FindAttestationDetailsTests {

		@BeforeEach
		void setUp() {
			when(mailMapper.sentMail(any(Attestation.class))).thenReturn(new ArrayList<>());
		}

		@Nested
		@DisplayName("When attestation type is ORGANISATION_ATTESTATION")
		class OrganisationAttestationDetailsTests {

			@Test
			@DisplayName("Should build details DTO with organisation overview")
			void findAttestationDetails_OrganisationAttestation_ShouldBuildCorrectDTO() {
				// Arrange
				attestation.setAttestationType(Attestation.AttestationType.ORGANISATION_ATTESTATION);

				OrganisationAttestationDTO orgAttestationDTO = new OrganisationAttestationDTO();
				when(organisationAttestationService.getAttestation(
					eq(attestation), eq(""), eq(false),
					eq(Attestation.AttestationType.ORGANISATION_ATTESTATION)))
					.thenReturn(orgAttestationDTO);

				when(attestationOverviewService.buildOrgUnitOverview(orgAttestationDTO, true))
					.thenReturn(mockOverview);

				// Act
				AdminAttestationDetailsDTO result = attestationAdminService.findAttestationDetails(attestation);

				// Assert
				assertNotNull(result);
				assertEquals(mockOverview, result.getOverview());
				assertEquals(Attestation.AttestationType.ORGANISATION_ATTESTATION, result.getAttestationType());
				assertEquals(1L, result.getId());
				assertNotNull(result.getSentEmails());
			}
		}

		@Nested
		@DisplayName("When attestation type is IT_SYSTEM_ROLES_ATTESTATION")
		class ItSystemRolesAttestationDetailsTests {

			@Test
			@DisplayName("Should build details DTO with IT system roles overview")
			void findAttestationDetails_ItSystemRolesAttestation_ShouldBuildCorrectDTO() {
				// Arrange
				attestation.setAttestationType(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION);

				ItSystemAttestationDTO itSystemAttestationDTO = new ItSystemAttestationDTO();
				itSystemAttestationDTO.setUserRoles(new ArrayList<>());
				itSystemAttestationDTO.setCreatedAt(LocalDate.now());
				itSystemAttestationDTO.setDeadLine(LocalDate.now().plusDays(7));
				itSystemAttestationDTO.setItSystemName("Test System");
				itSystemAttestationDTO.setItSystemId(1L);

				when(itSystemUserRolesAttestationService.getItSystemAttestation(attestation))
					.thenReturn(itSystemAttestationDTO);

				// Act
				AdminAttestationDetailsDTO result = attestationAdminService.findAttestationDetails(attestation);

				// Assert
				assertNotNull(result);
				assertEquals(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, result.getAttestationType());
				assertEquals(1L, result.getId());
				assertNotNull(result.getSentEmails());
				assertNotNull(result.getOverview());
			}
		}

		@Nested
		@DisplayName("When attestation type is IT_SYSTEM_ATTESTATION")
		class ItSystemAttestationDetailsTests {

			@Test
			@DisplayName("Should build details DTO with IT system users overview")
			void findAttestationDetails_ItSystemAttestation_ShouldBuildCorrectDTO() {
				// Arrange
				attestation.setAttestationType(Attestation.AttestationType.IT_SYSTEM_ATTESTATION);

				ItSystemRoleAttestationDTO itSystemRoleAttestationDTO = new ItSystemRoleAttestationDTO();
				itSystemRoleAttestationDTO.setUsers(new ArrayList<>());
				itSystemRoleAttestationDTO.setOrgUnits(new ArrayList<>());
				itSystemRoleAttestationDTO.setCreatedAt(LocalDate.now());
				itSystemRoleAttestationDTO.setDeadline(LocalDate.now().plusDays(7));
				itSystemRoleAttestationDTO.setItSystemName("Test System");
				itSystemRoleAttestationDTO.setItSystemId(1L);

				when(itSystemUsersAttestationService.getAttestation(attestation, false))
					.thenReturn(itSystemRoleAttestationDTO);

				// Act
				AdminAttestationDetailsDTO result = attestationAdminService.findAttestationDetails(attestation);

				// Assert
				assertNotNull(result);
				assertEquals(Attestation.AttestationType.IT_SYSTEM_ATTESTATION, result.getAttestationType());
				assertEquals(1L, result.getId());
				assertNotNull(result.getSentEmails());
				assertNotNull(result.getOverview());
			}
		}

		@Nested
		@DisplayName("When attestation type is MANAGER_DELEGATED_ATTESTATION")
		class ManagerDelegatedAttestationDetailsTests {

			@Test
			@DisplayName("Should build details DTO with manager delegated overview")
			void findAttestationDetails_ManagerDelegatedAttestation_ShouldBuildCorrectDTO() {
				// Arrange
				attestation.setAttestationType(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION);

				OrganisationAttestationDTO managerDelegateDTO = new OrganisationAttestationDTO();
				when(managerDelegateAttestationService.getAttestationDTO(attestation, "", false))
					.thenReturn(managerDelegateDTO);

				when(attestationOverviewService.buildOrgUnitOverview(managerDelegateDTO, true))
					.thenReturn(mockOverview);

				// Act
				AdminAttestationDetailsDTO result = attestationAdminService.findAttestationDetails(attestation);

				// Assert
				assertNotNull(result);
				assertEquals(mockOverview, result.getOverview());
				assertEquals(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION, result.getAttestationType());
				assertEquals(1L, result.getId());
				assertNotNull(result.getSentEmails());
			}

			@Test
			@DisplayName("Should build details DTO with actual manager delegate data")
			void findAttestationDetails_ManagerDelegatedWithData_ShouldBuildCorrectDTO() {
				// Arrange
				attestation.setAttestationType(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION);

				OrganisationAttestationDTO managerDelegateDTO = new OrganisationAttestationDTO();
				managerDelegateDTO.setOuName("Test Org Unit");
				managerDelegateDTO.setOuUuid("test-uuid");
				managerDelegateDTO.setCreatedAt(LocalDate.now());
				managerDelegateDTO.setDeadLine(LocalDate.now().plusDays(7));
				managerDelegateDTO.setUserAttestations(new ArrayList<>());
				managerDelegateDTO.setOrgUnitUserRoleAssignmentsPrItSystem(new ArrayList<>());
				managerDelegateDTO.setOrgUnitRoleGroupAssignments(new ArrayList<>());

				when(managerDelegateAttestationService.getAttestationDTO(attestation, "", false))
					.thenReturn(managerDelegateDTO);

				when(attestationOverviewService.buildOrgUnitOverview(managerDelegateDTO, true))
					.thenReturn(mockOverview);

				// Act
				AdminAttestationDetailsDTO result = attestationAdminService.findAttestationDetails(attestation);

				// Assert
				assertNotNull(result);
				assertEquals(mockOverview, result.getOverview());
				assertEquals(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION, result.getAttestationType());
				assertEquals(1L, result.getId());
				assertNotNull(result.getSentEmails());
			}
		}
	}

	@Nested
	@DisplayName("findAttestationStatus() Tests")
	class FindAttestationStatusTests {

		@Nested
		@DisplayName("When attestation is verified")
		class VerifiedAttestationTests {

			@Test
			@DisplayName("Should return FINISHED when verifiedAt is set")
			void findAttestationStatus_WhenVerified_ShouldReturnFinished() {
				// Arrange
				attestation.setVerifiedAt(ZonedDateTime.now());
				attestation.setAttestationType(Attestation.AttestationType.ORGANISATION_ATTESTATION);

				// Act
				AdminAttestationStatus result = attestationAdminService.findAttestationStatus(attestation);

				// Assert
				verifyNoInteractions(organisationUserAttestationEntryDao);
				verifyNoInteractions(itSystemUserAttestationEntryDao);
				verifyNoInteractions(itSystemRoleAttestationEntryDao);
				assertEquals(AdminAttestationStatus.FINISHED, result);
			}

			@Test
			@DisplayName("Should return FINISHED regardless of attestation type when verified")
			void findAttestationStatus_WhenVerified_ShouldReturnFinishedForAnyType() {
				// Arrange - test with different attestation type
				attestation.setVerifiedAt(ZonedDateTime.now());
				attestation.setAttestationType(Attestation.AttestationType.IT_SYSTEM_ATTESTATION);

				// Act
				AdminAttestationStatus result = attestationAdminService.findAttestationStatus(attestation);

				// Assert
				assertEquals(AdminAttestationStatus.FINISHED, result);
				verifyNoInteractions(itSystemUserAttestationEntryDao);
			}
		}

		@Nested
		@DisplayName("When attestation type is ORGANISATION_ATTESTATION")
		class OrganisationAttestationTests {

			@BeforeEach
			void setUp() {
				attestation.setVerifiedAt(null);
				attestation.setAttestationType(Attestation.AttestationType.ORGANISATION_ATTESTATION);
			}

			@Test
			@DisplayName("Should return ON_GOING when organisation has entries")
			void findAttestationStatus_OrganisationWithEntries_ShouldReturnOnGoing() {
				// Arrange
				when(organisationUserAttestationEntryDao.countByAttestationId(1L)).thenReturn(5L);

				// Act
				AdminAttestationStatus result = attestationAdminService.findAttestationStatus(attestation);

				// Assert
				assertEquals(AdminAttestationStatus.ON_GOING, result);
			}

			@Test
			@DisplayName("Should return NOT_STARTED when organisation has no entries")
			void findAttestationStatus_OrganisationWithNoEntries_ShouldReturnNotStarted() {
				// Arrange
				when(organisationUserAttestationEntryDao.countByAttestationId(1L)).thenReturn(0L);

				// Act
				AdminAttestationStatus result = attestationAdminService.findAttestationStatus(attestation);

				// Assert
				assertEquals(AdminAttestationStatus.NOT_STARTED, result);
			}
		}

		@Nested
		@DisplayName("When attestation type is IT_SYSTEM_ATTESTATION")
		class ItSystemAttestationTests {

			@BeforeEach
			void setUp() {
				attestation.setVerifiedAt(null);
				attestation.setAttestationType(Attestation.AttestationType.IT_SYSTEM_ATTESTATION);
			}

			@Test
			@DisplayName("Should return ON_GOING when IT system has entries")
			void findAttestationStatus_ItSystemWithEntries_ShouldReturnOnGoing() {
				// Arrange
				when(itSystemUserAttestationEntryDao.countByAttestationId(1L)).thenReturn(3L);

				// Act
				AdminAttestationStatus result = attestationAdminService.findAttestationStatus(attestation);

				// Assert
				assertEquals(AdminAttestationStatus.ON_GOING, result);
			}

			@Test
			@DisplayName("Should return NOT_STARTED when IT system has no entries")
			void findAttestationStatus_ItSystemWithNoEntries_ShouldReturnNotStarted() {
				// Arrange
				when(itSystemUserAttestationEntryDao.countByAttestationId(1L)).thenReturn(0L);

				// Act
				AdminAttestationStatus result = attestationAdminService.findAttestationStatus(attestation);

				// Assert
				assertEquals(AdminAttestationStatus.NOT_STARTED, result);
			}
		}

		@Nested
		@DisplayName("When attestation type is IT_SYSTEM_ROLES_ATTESTATION")
		class ItSystemRolesAttestationTests {

			@BeforeEach
			void setUp() {
				attestation.setVerifiedAt(null);
				attestation.setAttestationType(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION);
			}

			@Test
			@DisplayName("Should return ON_GOING when IT system roles has entries")
			void findAttestationStatus_ItSystemRolesWithEntries_ShouldReturnOnGoing() {
				// Arrange
				when(itSystemRoleAttestationEntryDao.countByAttestationId(1L)).thenReturn(7L);

				// Act
				AdminAttestationStatus result = attestationAdminService.findAttestationStatus(attestation);

				// Assert
				assertEquals(AdminAttestationStatus.ON_GOING, result);
			}

			@Test
			@DisplayName("Should return NOT_STARTED when IT system roles has no entries")
			void findAttestationStatus_ItSystemRolesWithNoEntries_ShouldReturnNotStarted() {
				// Arrange
				when(itSystemRoleAttestationEntryDao.countByAttestationId(1L)).thenReturn(0L);

				// Act
				AdminAttestationStatus result = attestationAdminService.findAttestationStatus(attestation);

				// Assert
				assertEquals(AdminAttestationStatus.NOT_STARTED, result);
			}
		}

		@Nested
		@DisplayName("When attestation type is MANAGER_DELEGATED_ATTESTATION")
		class ManagerDelegatedAttestationTests {

			@BeforeEach
			void setUp() {
				attestation.setVerifiedAt(null);
				attestation.setAttestationType(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION);
			}

			@Test
			@DisplayName("Should return ON_GOING when manager delegated has entries")
			void findAttestationStatus_ManagerDelegatedWithEntries_ShouldReturnOnGoing() {
				// Arrange
				when(itSystemRoleAttestationEntryDao.countByAttestationId(1L)).thenReturn(2L);

				// Act
				AdminAttestationStatus result = attestationAdminService.findAttestationStatus(attestation);

				// Assert
				assertEquals(AdminAttestationStatus.ON_GOING, result);
			}

			@Test
			@DisplayName("Should return NOT_STARTED when manager delegated has no entries")
			void findAttestationStatus_ManagerDelegatedWithNoEntries_ShouldReturnNotStarted() {
				// Arrange
				when(itSystemRoleAttestationEntryDao.countByAttestationId(1L)).thenReturn(0L);

				// Act
				AdminAttestationStatus result = attestationAdminService.findAttestationStatus(attestation);

				// Assert
				assertEquals(AdminAttestationStatus.NOT_STARTED, result);
			}
		}
	}
}
