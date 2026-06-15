package dk.digitalidentity.rc.attestation.service.tracker;

import dk.digitalidentity.rc.attestation.config.AttestationConfig;
import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.model.entity.AttestationUser;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.attestation.service.HistoricAssignmentAttestationService;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserAttestationTrackerServiceTest {

	@Mock
	private RoleCatalogueConfiguration configuration;

	@Mock
	private AttestationRunTrackerService runTrackerService;

	@Mock
	private HistoricAssignmentAttestationService historicAssignmentService;

	@Mock
	private AttestationDao attestationDao;

	@Mock
	private AttestationUserDao attestationUserDao;

	@Mock
	private AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;

	@Mock
	private EntityManager entityManager;

	@InjectMocks
	private UserAttestationTrackerService service;

	private final LocalDate when = LocalDate.of(2026, 6, 1);
	private AttestationRun run;

	@BeforeEach
	void setup() {
		run = AttestationRun.builder()
			.id(1L)
			.deadline(when.plusDays(10))
			.sensitive(false)
			.extraSensitive(false)
			.build();
		given(runTrackerService.getAttestationRunWithDeadlineNotAfter(when)).willReturn(Optional.of(run));
		lenient().when(configuration.getAttestation()).thenReturn(new AttestationConfig());
	}

	private AttestationUserRoleAssignment assignmentWithCollection(Long collectionId) {
		return AttestationUserRoleAssignment.builder()
			.itSystemId(10L)
			.itSystemName("Test IT System")
			.userUuid("user-uuid")
			.responsibleCollectionId(collectionId)
			.build();
	}

	@Test
	@DisplayName("creates an IT system users attestation for an assignment whose collection has responsibles")
	void createsAttestationWhenCollectionHasResponsibles() {
		given(historicAssignmentService.findValidGroupByResponsibleCollectionIdAndUserUuidAndSensitiveRoleAndItSystem(when))
			.willReturn(List.of(assignmentWithCollection(42L)));
		given(historicAssignmentService.findValidGroupByResponsibleCollectionIdAndSensitiveRole(when))
			.willReturn(List.of());
		given(attestationResponsibleCollectionDao.findById(42L))
			.willReturn(Optional.of(new AttestationResponsibleCollection(42L, 10L, List.of("responsible-uuid"))));
		given(attestationDao.findByAttestationTypeAndItSystemIdAndResponsibleCollectionIdAndDeadlineGreaterThanEqual(
			Attestation.AttestationType.IT_SYSTEM_ATTESTATION, 10L, 42L, when)).willReturn(Optional.empty());
		given(attestationDao.save(any(Attestation.class))).willAnswer(invocation -> invocation.getArgument(0));
		given(attestationUserDao.save(any(AttestationUser.class))).willAnswer(invocation -> invocation.getArgument(0));

		service.updateSystemUserAttestations(when);

		verify(attestationDao).save(any(Attestation.class));
	}

	@Test
	@DisplayName("skips assignments whose collection has no responsibles — the attestation would be invisible and unfinishable")
	void skipsAssignmentWithEmptyCollection() {
		given(historicAssignmentService.findValidGroupByResponsibleCollectionIdAndUserUuidAndSensitiveRoleAndItSystem(when))
			.willReturn(List.of(assignmentWithCollection(42L)));
		given(historicAssignmentService.findValidGroupByResponsibleCollectionIdAndSensitiveRole(when))
			.willReturn(List.of(assignmentWithCollection(42L)));
		given(attestationResponsibleCollectionDao.findById(42L))
			.willReturn(Optional.of(new AttestationResponsibleCollection(42L, 10L, List.of())));

		service.updateSystemUserAttestations(when);

		verify(attestationDao, never()).save(any(Attestation.class));
		// Tom-tjekket caches pr. kørsel — én lookup selvom collectionen optræder i begge streams.
		verify(attestationResponsibleCollectionDao).findById(42L);
	}

	@Test
	@DisplayName("skips assignments whose collection no longer exists")
	void skipsAssignmentWithMissingCollection() {
		given(historicAssignmentService.findValidGroupByResponsibleCollectionIdAndUserUuidAndSensitiveRoleAndItSystem(when))
			.willReturn(List.of(assignmentWithCollection(42L)));
		given(historicAssignmentService.findValidGroupByResponsibleCollectionIdAndSensitiveRole(when))
			.willReturn(List.of());
		given(attestationResponsibleCollectionDao.findById(42L)).willReturn(Optional.empty());

		service.updateSystemUserAttestations(when);

		verify(attestationDao, never()).save(any(Attestation.class));
	}
}
