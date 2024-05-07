package dk.digitalidentity.rc.attestation.service.tracker;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationSystemRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignment;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.history.HistoryUserDao;
import dk.digitalidentity.rc.dao.history.model.HistoryUser;
import dk.digitalidentity.rc.dao.model.enums.CheckupIntervalEnum;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;


@Slf4j
@Component
public class ItSystemAttestationTrackerService {
    @Autowired
    private RoleCatalogueConfiguration configuration;
    @Autowired
    private AttestationSystemRoleAssignmentDao systemRoleAssignmentDao;
    @Autowired
    private AttestationDao attestationDao;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private HistoryUserDao historyUserDao;


    @Transactional(timeout = 600, propagation = Propagation.REQUIRES_NEW)
    public void updateItSystemRolesAttestations(final LocalDate when) {
        final LocalDate deadline = findNextAttestationDate(when);
        try (Stream<AttestationSystemRoleAssignment> attestationSystemRoleAssignmentStream = systemRoleAssignmentDao.streamAllValidAssignments(when)) {
            attestationSystemRoleAssignmentStream
                    .filter(a -> a.getResponsibleUserUuid() != null)
                    .forEach(a -> ensureWeHaveAttestationFor(a, deadline, when));
        }
    }

    private void ensureWeHaveAttestationFor(final AttestationSystemRoleAssignment assignment, final LocalDate deadline, final LocalDate when) {

        Attestation attestation = findItSystemRolesAttestationFor(assignment, when).orElse(null);
        if (attestation == null) {
            if (!deadline.minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
                // Deadline is soon we need to create a new attestation
                attestation = createItSystemRolesAttestationFor(assignment, deadline, when);
            }
            // Else deadline is far in the future do not create an attestation entity yet
        } else {
            log.info("Attestation found for user role " + attestation.getUuid());
            // If we have another open attestation for this it-system the responsible manager might have changed, in that
            // case we need to delete the old attestation (unless its already been verified).
            if (!attestation.getResponsibleUserUuid().equals(assignment.getResponsibleUserUuid())) {
                log.info("It-system responsible changed updating attestation");
                final HistoryUser historyUser = historyUserDao.findFirstByDatoAndUserUuid(when, assignment.getResponsibleUserUuid());
                attestation.setResponsibleUserUuid(assignment.getResponsibleUserUuid());
                attestation.setResponsibleUserId(historyUser.getUserUserId());
            }
        }
    }

    /**
     * Create and attach a new {@link Attestation}
     */
    private Attestation createItSystemRolesAttestationFor(final AttestationSystemRoleAssignment assignment,
                                                          final LocalDate deadline,
                                                          final LocalDate when) {
        final HistoryUser historyUser = historyUserDao.findFirstByDatoAndUserUuid(when, assignment.getResponsibleUserUuid());
        if (historyUser == null) {
            return null;
        }
        return attestationDao.save(Attestation.builder()
                        .attestationType(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION)
                        .responsibleUserId(historyUser.getUserUserId())
                        .responsibleUserUuid(assignment.getResponsibleUserUuid())
                        .itSystemName(assignment.getItSystemName())
                        .itSystemId(assignment.getItSystemId())
                        .uuid(UUID.randomUUID().toString())
                        .deadline(deadline)
                        .createdAt(when)
                .build()
        );
    }

    private LocalDate findNextAttestationDate(final LocalDate when) {
        final CheckupIntervalEnum interval = settingsService.getScheduledAttestationInterval();
        LocalDate deadline = settingsService.getFirstAttestationDate();
        while (deadline.isBefore(when)) {
            deadline = deadline.plusMonths(intervalToMonths(interval));
        }
        return deadline;
    }

    private static int intervalToMonths(final CheckupIntervalEnum intervalEnum) {
        return switch (intervalEnum) {
            case YEARLY -> 12;
            case EVERY_HALF_YEAR -> 6;
        };
    }

    private Optional<Attestation> findItSystemRolesAttestationFor(final AttestationSystemRoleAssignment assignment, final LocalDate when) {
        return attestationDao.findByAttestationTypeAndItSystemIdAndDeadlineGreaterThanEqual(
                Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, assignment.getItSystemId(), when);
    }

}
