package dk.digitalidentity.rc.attestation.service.tracker;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationRunDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.enums.CheckupIntervalEnum;
import dk.digitalidentity.rc.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class AttestationRunTrackerService {
    @Autowired
    private RoleCatalogueConfiguration configuration;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private AttestationRunDao attestationRunDao;
    @Autowired
    private AttestationDao attestationDao;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateRuns(final LocalDate when) throws Exception {
        boolean yearly = settingsService.getScheduledAttestationInterval() == CheckupIntervalEnum.YEARLY;
        // Check if we need to create a new run
        final LocalDate deadlineNormal = findNextAttestationDate(when, false, false);
        final LocalDate deadlineSensitive = findNextAttestationDate(when, true, false);
        final LocalDate deadlineExtraSensitive = findNextAttestationDate(when, false, true);
        // Check if we already have an attestation run active
        final boolean normalRun = shouldCreateAttestationRun(when, deadlineNormal);
        final boolean sensitiveRun = !normalRun && shouldCreateAttestationRun(when, deadlineSensitive);
        final boolean extraSensitiveRun = !normalRun && yearly && shouldCreateAttestationRun(when, deadlineExtraSensitive);
        if (normalRun || sensitiveRun || extraSensitiveRun) {
            final LocalDate expectedDeadline = extraSensitiveRun ? deadlineExtraSensitive : (sensitiveRun ? deadlineSensitive : deadlineNormal);
            final List<AttestationRun> unfinishedRuns = getActiveAttestationRunsDesc();

            // Check if the active runs, have deadline before our deadline, in that case they must be old runs,
            // so create a new run here
            if (unfinishedRuns.stream().allMatch(r -> r.getDeadline().isBefore(when))) {
                unfinishedRuns.addFirst(createNewAttestationRun(expectedDeadline, sensitiveRun, extraSensitiveRun));
            }

            //Find or create the active run
            final Optional<AttestationRun> activeRun = unfinishedRuns.isEmpty() ? Optional.empty() : Optional.of(unfinishedRuns.getFirst());
            //Close all other than the active one
            for (AttestationRun run : unfinishedRuns) {
                if (!run.getId().equals(activeRun.get().getId())) {
                    run.setFinished(true);
                }
            }

        }
    }

    /**
     * This method will create {@link AttestationRun}s for the old {@link dk.digitalidentity.rc.attestation.model.entity.Attestation}s
     * which was created before the {@link AttestationRun} where introduced.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void migrateAttestationsWithoutRun() {
        // TODO Remove this method when all have been migrated - probably 2024r3
        attestationDao.findByAttestationRunIsNull().forEach(this::findOrCreateRunFor);
    }

    @SuppressWarnings("deprecation")
    private void findOrCreateRunFor(final Attestation att) {
        if (att.getAttestationRun() == null) {
            attestationRunDao.findByDeadlineIs(att.getDeadline())
                    .ifPresentOrElse(att::setAttestationRun,
                            () -> att.setAttestationRun(createNewAttestationRun(att.getDeadline(), att.isSensitive(), false)));
        }
    }

    public Optional<AttestationRun> getAttestationRunWithDeadlineNotAfter(final LocalDate deadline) {
        return attestationRunDao.findFirstByFinishedFalseAndDeadlineGreaterThanEqual(deadline);
    }

    public List<AttestationRun> getActiveAttestationRunsDesc() {
        return attestationRunDao.findByFinishedFalseOrderByDeadlineDesc();
    }

    private AttestationRun createNewAttestationRun(final LocalDate deadline, final boolean sensitive, final boolean extraSensitive) {
        final AttestationRun attestationRun = AttestationRun.builder()
                .createdAt(LocalDate.now())
                .extraSensitive(extraSensitive)
                .sensitive(sensitive)
                .finished(false)
                .deadline(deadline)
                .build();
        return attestationRunDao.save(attestationRun);
    }

    private LocalDate findNextAttestationDate(final LocalDate when, final boolean sensitive, final boolean extraSensitive) {
        final CheckupIntervalEnum interval = settingsService.getScheduledAttestationInterval();
        LocalDate deadline = settingsService.getFirstAttestationDate();
        while (deadline.isBefore(when)) {
            if (extraSensitive) {
                deadline = deadline.plusMonths(extraSensitiveIntervalToMonths(interval));
            } else if (sensitive) {
                deadline = deadline.plusMonths(sensitiveIntervalToMonths(interval));
            } else {
                deadline = deadline.plusMonths(intervalToMonths(interval));
            }
        }
        return deadline;
    }

    /**
     * Returns true if current date is within XX (default 30) days before deadline
     * @param now
     * @param deadline
     * @return
     */
    private boolean shouldCreateAttestationRun(final LocalDate now, final LocalDate deadline) {
        return !deadline.minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(now);
    }

    /**
     * Intervals are quartered for extra sensitive roles only when interval is YEARLY.
     */
    private static int extraSensitiveIntervalToMonths(final CheckupIntervalEnum intervalEnum) {
        return switch (intervalEnum) {
            case YEARLY, EVERY_HALF_YEAR -> 3;
        };
    }

    /**
     * Intervals are halved for sensitive roles.
     */
    private static int sensitiveIntervalToMonths(final CheckupIntervalEnum intervalEnum) {
        return switch (intervalEnum) {
            case YEARLY -> 6;
            case EVERY_HALF_YEAR -> 3;
        };
    }

    private static int intervalToMonths(final CheckupIntervalEnum intervalEnum) {
        return switch (intervalEnum) {
            case YEARLY -> 12;
            case EVERY_HALF_YEAR -> 6;
        };
    }

}
