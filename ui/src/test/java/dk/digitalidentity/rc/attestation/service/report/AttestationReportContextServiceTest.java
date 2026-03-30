package dk.digitalidentity.rc.attestation.service.report;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("Attestation Report Context Service Tests")
class AttestationReportContextServiceTest {

	@Mock
	private AttestationDao attestationDao;

	@InjectMocks
	private AttestationReportContextService attestationReportContextService;

	private LocalDate fromDate;

	@BeforeEach
	void setUp() {
		fromDate = LocalDate.now().minusMonths(1);
	}

	@Nested
	@DisplayName("createContext() Tests")
	class CreateContextTests {

		@Test
		@DisplayName("Should return context with all attestation types populated")
		void createContext_ShouldReturnContextWithAllAttestationTypes() {
			// Arrange
			Attestation itSystemUserAttestation = createAttestation(1L, Attestation.AttestationType.IT_SYSTEM_ATTESTATION);
			Attestation itSystemRolesAttestation = createAttestation(2L, Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION);
			Attestation organisationAttestation = createAttestation(3L, Attestation.AttestationType.ORGANISATION_ATTESTATION);

			when(attestationDao.findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(
					eq(Attestation.AttestationType.IT_SYSTEM_ATTESTATION), eq(fromDate)))
					.thenReturn(List.of(itSystemUserAttestation));

			when(attestationDao.findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(
					eq(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION), eq(fromDate)))
					.thenReturn(List.of(itSystemRolesAttestation));

			when(attestationDao.findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(
					eq(Attestation.AttestationType.ORGANISATION_ATTESTATION), eq(fromDate)))
					.thenReturn(List.of(organisationAttestation));

			// Act
			AttestationReportContextService.AttestationReportContext result = attestationReportContextService.createContext(fromDate);

			// Assert
			assertNotNull(result);
			assertEquals(1, result.getItSystemUserAttestations().size());
			assertEquals(1, result.getItSystemRolesAttestations().size());
			assertEquals(1, result.getOrganisationRolesAttestations().size());
			assertEquals(itSystemUserAttestation, result.getItSystemUserAttestations().get(0));
			assertEquals(itSystemRolesAttestation, result.getItSystemRolesAttestations().get(0));
			assertEquals(organisationAttestation, result.getOrganisationRolesAttestations().get(0));
		}

		@Test
		@DisplayName("Should return context with empty lists when no attestations found")
		void createContext_WhenNoAttestations_ShouldReturnEmptyLists() {
			// Arrange
			when(attestationDao.findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(
					any(Attestation.AttestationType.class), any(LocalDate.class)))
					.thenReturn(Collections.emptyList());

			// Act
			AttestationReportContextService.AttestationReportContext result = attestationReportContextService.createContext(fromDate);

			// Assert
			assertNotNull(result);
			assertTrue(result.getItSystemUserAttestations().isEmpty());
			assertTrue(result.getItSystemRolesAttestations().isEmpty());
			assertTrue(result.getOrganisationRolesAttestations().isEmpty());
		}

		@Test
		@DisplayName("Should call dao with correct attestation types")
		void createContext_ShouldCallDaoWithCorrectAttestationTypes() {
			// Arrange
			when(attestationDao.findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(
					any(Attestation.AttestationType.class), any(LocalDate.class)))
					.thenReturn(Collections.emptyList());

			// Act
			attestationReportContextService.createContext(fromDate);

			// Assert
			verify(attestationDao).findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(
					eq(Attestation.AttestationType.IT_SYSTEM_ATTESTATION), eq(fromDate));
			verify(attestationDao).findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(
					eq(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION), eq(fromDate));
			verify(attestationDao).findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(
					eq(Attestation.AttestationType.ORGANISATION_ATTESTATION), eq(fromDate));
			verifyNoMoreInteractions(attestationDao);
		}

		@Test
		@DisplayName("Should return context with multiple attestations per type")
		void createContext_WithMultipleAttestations_ShouldReturnAllAttestations() {
			// Arrange
			Attestation itSystemUserAttestation1 = createAttestation(1L, Attestation.AttestationType.IT_SYSTEM_ATTESTATION);
			Attestation itSystemUserAttestation2 = createAttestation(2L, Attestation.AttestationType.IT_SYSTEM_ATTESTATION);
			Attestation itSystemRolesAttestation1 = createAttestation(3L, Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION);
			Attestation itSystemRolesAttestation2 = createAttestation(4L, Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION);
			Attestation organisationAttestation1 = createAttestation(5L, Attestation.AttestationType.ORGANISATION_ATTESTATION);
			Attestation organisationAttestation2 = createAttestation(6L, Attestation.AttestationType.ORGANISATION_ATTESTATION);

			when(attestationDao.findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(
					eq(Attestation.AttestationType.IT_SYSTEM_ATTESTATION), eq(fromDate)))
					.thenReturn(List.of(itSystemUserAttestation1, itSystemUserAttestation2));

			when(attestationDao.findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(
					eq(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION), eq(fromDate)))
					.thenReturn(List.of(itSystemRolesAttestation1, itSystemRolesAttestation2));

			when(attestationDao.findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(
					eq(Attestation.AttestationType.ORGANISATION_ATTESTATION), eq(fromDate)))
					.thenReturn(List.of(organisationAttestation1, organisationAttestation2));

			// Act
			AttestationReportContextService.AttestationReportContext result = attestationReportContextService.createContext(fromDate);

			// Assert
			assertNotNull(result);
			assertEquals(2, result.getItSystemUserAttestations().size());
			assertEquals(2, result.getItSystemRolesAttestations().size());
			assertEquals(2, result.getOrganisationRolesAttestations().size());
		}

		@Test
		@DisplayName("Should pass fromDate parameter to dao correctly")
		void createContext_ShouldPassFromDateCorrectly() {
			// Arrange
			LocalDate specificDate = LocalDate.of(2024, 6, 15);
			when(attestationDao.findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(
					any(Attestation.AttestationType.class), any(LocalDate.class)))
					.thenReturn(Collections.emptyList());

			// Act
			attestationReportContextService.createContext(specificDate);

			// Assert
			verify(attestationDao, times(3)).findByAttestationTypeAndCreatedAtGreaterThanEqualAndVerifiedAtIsNotNull(
					any(Attestation.AttestationType.class), eq(specificDate));
		}
	}

	private Attestation createAttestation(Long id, Attestation.AttestationType type) {
		Attestation attestation = new Attestation();
		attestation.setId(id);
		attestation.setAttestationType(type);
		attestation.setCreatedAt(LocalDate.now());
		attestation.setVerifiedAt(ZonedDateTime.now());
		return attestation;
	}
}
