package dk.digitalidentity.rc.attestation.service.tracker;

import dk.digitalidentity.rc.attestation.dao.AttestationRunDao;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AttestationRunTrackerServiceTest {

	@Mock
	private AttestationRunDao attestationRunDao;

	@InjectMocks
	private AttestationRunTrackerService service;

	private final LocalDate when = LocalDate.of(2026, 10, 20);

	@Test
	@DisplayName("maintains the open run up to and including its deadline")
	void maintainsRunOnDeadline() {
		final AttestationRun run = AttestationRun.builder().deadline(when).build();
		given(attestationRunDao.findFirstByFinishedFalseOrderByDeadlineDesc()).willReturn(Optional.of(run));

		assertThat(service.getAttestationRunToMaintain(when)).contains(run);
	}

	@Test
	@DisplayName("leaves an open run past its deadline alone, so nothing new joins it")
	void leavesOverdueRunAlone() {
		final AttestationRun run = AttestationRun.builder().deadline(when.minusDays(1)).build();
		given(attestationRunDao.findFirstByFinishedFalseOrderByDeadlineDesc()).willReturn(Optional.of(run));

		assertThat(service.getAttestationRunToMaintain(when)).isEmpty();
	}
}
