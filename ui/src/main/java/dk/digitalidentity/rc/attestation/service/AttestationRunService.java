package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationRunDao;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttestationRunService {
    private final AttestationRunDao attestationRunDao;

    public Optional<AttestationRun> getCurrentRun() {
        return attestationRunDao.findFirstByFinishedFalseOrderByDeadlineDesc();
    }

    public Optional<AttestationRun> getRun(final Long runId) {
        return attestationRunDao.findById(runId);
    }

	public List<AttestationRun> getLatestRuns(int limit) {
		return attestationRunDao.findLatestRuns(limit);
	}

}
