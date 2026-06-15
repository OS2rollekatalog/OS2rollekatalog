package dk.digitalidentity.rc.attestation.service.tracker;

import dk.digitalidentity.rc.attestation.config.AttestationConfig;
import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.dao.AttestationSystemRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignment;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ItSystemAttestationTrackerServiceTest {

	@Mock
	private RoleCatalogueConfiguration configuration;

	@Mock
	private AttestationSystemRoleAssignmentDao systemRoleAssignmentDao;

	@Mock
	private AttestationDao attestationDao;

	@Mock
	private AttestationRunTrackerService runTrackerService;

	@Mock
	private AttestationResponsibleCollectionDao responsibleCollectionDao;

	@InjectMocks
	private ItSystemAttestationTrackerService service;

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
		lenient().when(configuration.getAttestation()).thenReturn(new AttestationConfig());
	}

	private AttestationSystemRoleAssignment assignmentWithCollection(Long collectionId) {
		return AttestationSystemRoleAssignment.builder()
			.itSystemId(10L)
			.itSystemName("Test IT System")
			.userRoleId(20L)
			.systemRoleId(30L)
			.responsibleCollectionId(collectionId)
			.build();
	}

	@Test
	@DisplayName("creates a roles attestation for an assignment whose collection has responsibles")
	void createsAttestationWhenCollectionHasResponsibles() {
		given(runTrackerService.getAttestationRunWithDeadlineNotAfter(when)).willReturn(Optional.of(run));
		given(systemRoleAssignmentDao.streamAllValidAssignments(when)).willReturn(Stream.of(assignmentWithCollection(42L)));
		given(responsibleCollectionDao.findById(42L))
			.willReturn(Optional.of(new AttestationResponsibleCollection(42L, 10L, List.of("responsible-uuid"))));
		given(attestationDao.findByAttestationTypeAndItSystemIdAndResponsibleCollectionIdAndDeadlineGreaterThanEqual(
			Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, 10L, 42L, when)).willReturn(Optional.empty());

		service.updateItSystemRolesAttestations(when);

		ArgumentCaptor<Attestation> captor = ArgumentCaptor.forClass(Attestation.class);
		verify(attestationDao).save(captor.capture());
		assertThat(captor.getValue().getAttestationType()).isEqualTo(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION);
		assertThat(captor.getValue().getResponsibleCollectionId()).isEqualTo(42L);
		assertThat(captor.getValue().getItSystemId()).isEqualTo(10L);
	}

	@Test
	@DisplayName("skips assignments whose collection has no responsibles — the attestation would be invisible and unfinishable")
	void skipsAssignmentWithEmptyCollection() {
		given(runTrackerService.getAttestationRunWithDeadlineNotAfter(when)).willReturn(Optional.of(run));
		given(systemRoleAssignmentDao.streamAllValidAssignments(when)).willReturn(Stream.of(assignmentWithCollection(42L)));
		given(responsibleCollectionDao.findById(42L))
			.willReturn(Optional.of(new AttestationResponsibleCollection(42L, 10L, List.of())));

		service.updateItSystemRolesAttestations(when);

		verify(attestationDao, never()).save(any(Attestation.class));
	}

	@Test
	@DisplayName("skips assignments whose collection no longer exists")
	void skipsAssignmentWithMissingCollection() {
		given(runTrackerService.getAttestationRunWithDeadlineNotAfter(when)).willReturn(Optional.of(run));
		given(systemRoleAssignmentDao.streamAllValidAssignments(when)).willReturn(Stream.of(assignmentWithCollection(42L)));
		given(responsibleCollectionDao.findById(42L)).willReturn(Optional.empty());

		service.updateItSystemRolesAttestations(when);

		verify(attestationDao, never()).save(any(Attestation.class));
	}

	@Test
	@DisplayName("skips assignments without a responsible collection id")
	void skipsAssignmentWithoutCollectionId() {
		given(runTrackerService.getAttestationRunWithDeadlineNotAfter(when)).willReturn(Optional.of(run));
		given(systemRoleAssignmentDao.streamAllValidAssignments(when)).willReturn(Stream.of(assignmentWithCollection(null)));

		service.updateItSystemRolesAttestations(when);

		verify(attestationDao, never()).save(any(Attestation.class));
	}
}
