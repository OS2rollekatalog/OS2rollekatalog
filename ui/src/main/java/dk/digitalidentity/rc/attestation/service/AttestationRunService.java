package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationRunDao;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

import static dk.digitalidentity.rc.attestation.AttestationConstants.FINISHED_DAYS_AFTER_DEADLINE;

@Service
@RequiredArgsConstructor
    public class AttestationRunService {
    private final AttestationRunDao attestationRunDao;

    public Optional<AttestationRun> getCurrentRun() {
        return attestationRunDao.findFirstByFinishedFalseAndDeadlineGreaterThanEqual(LocalDate.now().minusDays(FINISHED_DAYS_AFTER_DEADLINE));
    }

    public Optional<AttestationRun> getRun(final Long runId) {
        return attestationRunDao.findById(runId);
    }

}
