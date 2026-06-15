package dk.digitalidentity.rc.attestation.service.tracker;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.dao.AttestationSystemRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignment;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
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
    private AttestationRunTrackerService runTrackerService;
    @Autowired
    private AttestationResponsibleCollectionDao responsibleCollectionDao;


    @Transactional(timeout = 600, propagation = Propagation.REQUIRES_NEW)
    public void updateItSystemRolesAttestations(final LocalDate when) {
        runTrackerService.getAttestationRunWithDeadlineNotAfter(when)
                .filter(a -> !a.isSensitive())
                .ifPresent(run -> {
                    // Attestationer for tomme collections er usynlige for alle (synlighed afgøres af
                    // collection-medlemskab) og kan aldrig afsluttes — spring dem over.
                    final Map<Long, Boolean> collectionHasResponsibles = new HashMap<>();
                    try (Stream<AttestationSystemRoleAssignment> attestationSystemRoleAssignmentStream = systemRoleAssignmentDao.streamAllValidAssignments(when)) {
                        attestationSystemRoleAssignmentStream
                                .filter(a -> a.getResponsibleCollectionId() != null)
                                .filter(a -> collectionHasResponsibles.computeIfAbsent(a.getResponsibleCollectionId(), this::hasResponsibles))
                                .forEach(a -> ensureWeHaveAttestationFor(run, a, when));
                    }
                });
    }

    private boolean hasResponsibles(final Long collectionId) {
        return responsibleCollectionDao.findById(collectionId)
                .map(c -> !c.getUsersUuid().isEmpty())
                .orElse(false);
    }

    private void ensureWeHaveAttestationFor(final AttestationRun run, final AttestationSystemRoleAssignment assignment, final LocalDate when) {
        if (run.isExtraSensitive() || run.isSensitive()) {
            return;
        }
        Attestation attestation = findItSystemRolesAttestationFor(assignment, when).orElse(null);
        if (attestation == null) {
            if (!run.getDeadline().minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
                // Deadline is soon we need to create a new attestation for this (system × responsible) pair
                createItSystemRolesAttestationFor(run, assignment, when);
            }
            // Else deadline is far in the future do not create an attestation section yet
        } else {
            log.info("Attestation found for user role {}", attestation.getUuid());
        }
    }

    /**
     * Create and attach a new {@link Attestation}
     */
    private Attestation createItSystemRolesAttestationFor(final AttestationRun run,
                                                          final AttestationSystemRoleAssignment assignment,
                                                          final LocalDate when) {
        return attestationDao.save(Attestation.builder()
                        .attestationType(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION)
                        .responsibleCollectionId(assignment.getResponsibleCollectionId())
                        .itSystemName(assignment.getItSystemName())
                        .itSystemId(assignment.getItSystemId())
                        .uuid(UUID.randomUUID().toString())
                        .deadline(run.getDeadline())
                        .attestationRun(run)
                        .createdAt(when)
                .build()
        );
    }

    private Optional<Attestation> findItSystemRolesAttestationFor(final AttestationSystemRoleAssignment assignment, final LocalDate when) {
        return attestationDao.findByAttestationTypeAndItSystemIdAndResponsibleCollectionIdAndDeadlineGreaterThanEqual(
                Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, assignment.getItSystemId(), assignment.getResponsibleCollectionId(), when);
    }
}
