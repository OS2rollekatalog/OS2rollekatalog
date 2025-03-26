package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationRunDao;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttestationRunService {
    private final RoleCatalogueConfiguration configuration;
    private final AttestationRunDao attestationRunDao;

    public Optional<AttestationRun> getCurrentRun() {
        return attestationRunDao.findFirstByFinishedFalseOrderByDeadlineDesc();
    }

    public Optional<AttestationRun> getRun(final Long runId) {
        return attestationRunDao.findById(runId);
    }

}
