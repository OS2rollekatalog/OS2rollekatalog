package dk.digitalidentity.rc.attestation.service.tracker;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationOuAssignmentsDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationUser;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.history.HistoryUserDao;
import dk.digitalidentity.rc.dao.history.model.HistoryUser;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.enums.CheckupIntervalEnum;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class UserAttestationTrackerService {
    @Autowired
    private RoleCatalogueConfiguration configuration;
    @Autowired
    private AttestationUserRoleAssignmentDao userRoleAssignmentDao;
    @Autowired
    private AttestationOuAssignmentsDao ouAssignmentsDao;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private AttestationDao attestationDao;
    @Autowired
    private AttestationUserDao attestationUserDao;
    @Autowired
    private HistoryUserDao historyUserDao;
    @Autowired
    private OrgUnitDao orgUnitDao;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(timeout = 600, propagation = Propagation.REQUIRES_NEW)
    public void updateSystemUserAttestations(final LocalDate when) {
        cnt = cntUA = cntOu = 0;
        entityManager.setFlushMode(FlushModeType.COMMIT);
        final LocalDate deadlineNormal = findNextAttestationDate(when, false);
        final LocalDate deadlineSensitive = findNextAttestationDate(when, true);
        userRoleAssignmentDao.findValidGroupByResponsibleUserUuidAndUserUuidAndSensitiveRoleAndItSystem(when)
                .forEach(a -> ensureWeHaveSystemUsersAttestationFor(a, when, a.isSensitiveRole() ? deadlineSensitive : deadlineNormal));

        // Ensure we have attestations for all direct OU assignments
        // We should create attestation for all ou assignments that are not inherited.
        ouAssignmentsDao.findValidGroupByResponsibleUserUuidAndSensitiveRole(when)
                    .forEach(a -> ensureWeHaveSystemUsersAttestationForOu(a, when, a.isSensitiveRole() ? deadlineSensitive : deadlineNormal));
    }

    @Transactional(timeout = 600, propagation = Propagation.REQUIRES_NEW)
    public void updateOrganisationUserAttestations(final LocalDate when) {
        cnt = cntUA = cntOu = 0;
        entityManager.setFlushMode(FlushModeType.COMMIT);
        final LocalDate deadlineNormal = findNextAttestationDate(when, false);
        final LocalDate deadlineSensitive = findNextAttestationDate(when, true);
        // We start by deleting all attestation users
        // Reason for this is that the org hierarchy changes over time, so it's easier to just start from scratch every time.
        attestationDao.findByAttestationTypeAndDeadlineIsGreaterThanEqual(Attestation.AttestationType.ORGANISATION_ATTESTATION, when)
            .forEach(a -> {
                attestationUserDao.deleteAll(a.getUsersForAttestation());
                a.setUsersForAttestation(Collections.emptySet());
            });
        entityManager.flush();
        final Set<String> exemptedOus = findExemptedOUs();

        // Ensure we have attestations for all direct assignments
        // We should create attestation for all ou assignments that are not inherited.
        ouAssignmentsDao.findValidGroupByResponsibleOuUuidAndSensitiveRole(when).stream()
                .filter(a -> !exemptedOus.contains(a.getResponsibleOuUuid()))
                .forEach(a -> ensureWeHaveOrganisationAttestationForOu(a, when, a.isSensitiveRole() ? deadlineSensitive : deadlineNormal));

        userRoleAssignmentDao.findValidGroupByResponsibleOuAndUserUuidAndSensitiveRole(when).stream()
                // DO not create attestation for OUs that have been exempt in settings
                .filter(a -> !exemptedOus.contains(a.getResponsibleOuUuid()))
                .filter(a -> !a.isInherited())
                .forEach(a -> ensureWeHaveOrganisationAttestationFor(a, when, a.isSensitiveRole() ? deadlineSensitive : deadlineNormal));
    }

    private Set<String> findExemptedOUs() {
        return settingsService.getScheduledAttestationFilter().stream()
                .map(uuid -> orgUnitDao.findById(uuid))
                .filter(Optional::isPresent)
                .flatMap(ou -> getChildrenRecursive(ou.get(), 0))
                .map(OrgUnit::getUuid)
                .collect(Collectors.toSet());
    }

    private Stream<OrgUnit> getChildrenRecursive(final OrgUnit orgUnit, int level) {
        if (level > 15) {
            log.warn("Recursive loop detected reached level ${}, current ou: ${}", level, orgUnit.getUuid());
            return Stream.empty();
        }
        return Stream.concat(Stream.of(orgUnit), orgUnit.getChildren().stream()
                .flatMap(ou -> getChildrenRecursive(ou, level + 1)));
    }

    static int cntUA = 0;
    private void ensureWeHaveSystemUsersAttestationFor(final AttestationUserRoleAssignment assignment,
                                                       final LocalDate when, final LocalDate deadline) {
        Attestation attestation = findSystemUsersAttestationFor(assignment, when).orElse(null);
        if (attestation == null) {
            if (deadline.minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
                // Deadline is in the future do not create an attestation entity yet
                return;
            } else {
                // Deadline is soon we need to create a new attestation
                attestation = createItSystemUsersAttestationFor(assignment, when, deadline);
            }
        } else {
            // If we have another open attestation for this it-system the responsible manager might have changed, in that
            // case we need to delete the old attestation (unless its already been verified).
            if (!attestation.getResponsibleUserUuid().equals(assignment.getResponsibleUserUuid())) {
                log.info("It-system responsible changed updating attestation");
                final HistoryUser historyUser = historyUserDao.findFirstByDatoAndUserUuid(when, assignment.getResponsibleUserUuid());
                attestation.setResponsibleUserUuid(assignment.getResponsibleUserUuid());
                attestation.setResponsibleUserId(historyUser.getUserUserId());
            }
        }
        addUser(attestation, assignment);
        if (cntUA++ % 100 == 0) {
            log.info("Attestation system user " + attestation.getUuid() + " found, progress count=" + cntUA);
        }
    }

    private void ensureWeHaveSystemUsersAttestationForOu(final AttestationOuRoleAssignment assignment,
                                                         final LocalDate when, final LocalDate deadline) {
        Attestation attestation = findSystemUsersAttestationFor(assignment, when).orElse(null);
        if (attestation == null) {
            if (deadline.minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
                // Deadline is in the future do not create an attestation entity yet
                return;
            } else {
                // Deadline is soon we need to create a new attestation
                attestation = createItSystemUsersAttestationFor(assignment, when, deadline);
            }
        }
        if (cntUA++ % 100 == 0) {
            log.info("Attestation system user " + attestation.getUuid() + " found, progress count=" + cntUA);
        }
    }

    static int cntOu = 0;
    private void ensureWeHaveOrganisationAttestationForOu(final AttestationOuRoleAssignment assignment, final LocalDate when, final LocalDate deadline) {
        Attestation attestation = findOrganisationAttestationFor(assignment, when).orElse(null);
        if (attestation == null) {
            if (deadline.minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
                // Deadline is in the future do not create an attestation entity yet
                return;
            } else {
                // Deadline is soon we need to create a new attestation
                attestation = createOrganisationUsersAttestationFor(assignment, when, deadline);
            }
        }
        if (cntOu++ % 100 == 0) {
            log.info("Attestation for organisation role " + attestation.getUuid() + " found, progress count=" + cntOu);
        }
    }

    static int cnt = 0;
    private void ensureWeHaveOrganisationAttestationFor(final AttestationUserRoleAssignment assignment,
                                                        final LocalDate when, final LocalDate deadline) {
        Attestation attestation = findOrganisationAttestationFor(assignment, when).orElse(null);
        if (attestation == null) {
            if (deadline.minusDays(configuration.getAttestation().getDaysForAttestation()).isAfter(when)) {
                // Deadline is in the future do not create an attestation entity yet
                return;
            } else {
                // Deadline is soon we need to create a new attestation
                attestation = createOrganisationUsersAttestationFor(assignment, when, deadline);
            }
        }
        addUser(attestation, assignment);
        if (cnt++ % 100 == 0) {
            log.info("Attestation for organisation role " + attestation.getUuid() + " found, progress count=" + cnt);
        }
    }

    private Optional<Attestation> findSystemUsersAttestationFor(final AttestationUserRoleAssignment assignment, final LocalDate when) {
        return attestationDao.findByAttestationTypeAndItSystemIdAndDeadlineGreaterThanEqual(
                Attestation.AttestationType.IT_SYSTEM_ATTESTATION, assignment.getItSystemId(), when);
    }

    private Optional<Attestation> findSystemUsersAttestationFor(final AttestationOuRoleAssignment assignment, final LocalDate when) {
        return attestationDao.findByAttestationTypeAndItSystemIdAndDeadlineGreaterThanEqual(
                Attestation.AttestationType.IT_SYSTEM_ATTESTATION, assignment.getItSystemId(), when);
    }

    private Optional<Attestation> findOrganisationAttestationFor(final AttestationUserRoleAssignment assignment, final LocalDate when) {
        return attestationDao.findByAttestationTypeAndResponsibleOuUuidAndDeadlineGreaterThanEqual(
                Attestation.AttestationType.ORGANISATION_ATTESTATION, assignment.getResponsibleOuUuid(), when);
    }

    private Optional<Attestation> findOrganisationAttestationFor(final AttestationOuRoleAssignment assignment, final LocalDate when) {
        return attestationDao.findByAttestationTypeAndResponsibleOuUuidAndDeadlineGreaterThanEqual(
                Attestation.AttestationType.ORGANISATION_ATTESTATION, assignment.getResponsibleOuUuid(), when);
    }

    private Attestation createOrganisationUsersAttestationFor(final AttestationUserRoleAssignment assignment, final LocalDate when,
                                                              final LocalDate deadline) {
        return attestationDao.save(Attestation.builder()
                .attestationType(Attestation.AttestationType.ORGANISATION_ATTESTATION)
                .responsibleOuUuid(assignment.getResponsibleOuUuid())
                .responsibleOuName(assignment.getResponsibleOuName())
                .deadline(deadline)
                .createdAt(when)
                .uuid(UUID.randomUUID().toString())
                .build()
        );
    }
    private Attestation createOrganisationUsersAttestationFor(final AttestationOuRoleAssignment assignment, final LocalDate when,
                                                              final LocalDate deadline) {
        return attestationDao.save(Attestation.builder()
                .attestationType(Attestation.AttestationType.ORGANISATION_ATTESTATION)
                .responsibleOuUuid(assignment.getResponsibleOuUuid())
                .responsibleOuName(assignment.getResponsibleOuName())
                .deadline(deadline)
                .createdAt(when)
                .uuid(UUID.randomUUID().toString())
                .build()
        );
    }

    private Attestation createItSystemUsersAttestationFor(final AttestationUserRoleAssignment assignment, final LocalDate when,
                                                          final LocalDate deadline) {
        final HistoryUser historyUser = historyUserDao.findFirstByDatoAndUserUuid(when, assignment.getResponsibleUserUuid());
        return attestationDao.save(Attestation.builder()
                .attestationType(Attestation.AttestationType.IT_SYSTEM_ATTESTATION)
                .itSystemId(assignment.getItSystemId())
                .itSystemName(assignment.getItSystemName())
                .responsibleUserId(historyUser.getUserUserId())
                .responsibleUserUuid(assignment.getResponsibleUserUuid())
                .deadline(deadline)
                .createdAt(when)
                .uuid(UUID.randomUUID().toString())
                .build()
        );
    }


    private Attestation createItSystemUsersAttestationFor(final AttestationOuRoleAssignment assignment, final LocalDate when,
                                                          final LocalDate deadline) {
        final HistoryUser historyUser = historyUserDao.findFirstByDatoAndUserUuid(when, assignment.getResponsibleUserUuid());
        return attestationDao.save(Attestation.builder()
                .attestationType(Attestation.AttestationType.IT_SYSTEM_ATTESTATION)
                .itSystemId(assignment.getItSystemId())
                .itSystemName(assignment.getItSystemName())
                .responsibleUserId(historyUser.getUserUserId())
                .responsibleUserUuid(assignment.getResponsibleUserUuid())
                .deadline(deadline)
                .createdAt(when)
                .uuid(UUID.randomUUID().toString())
                .build()
        );
    }

    private void addUser(final Attestation attestation, final AttestationUserRoleAssignment assignment) {
        if (attestation.getUsersForAttestation().stream()
                .anyMatch(a -> a.getUserUuid().equals(assignment.getUserUuid()))) {
            // The user already exist as user with sensitive role
            return;
        }
        final AttestationUser attestationUser = attestation.getUsersForAttestation().stream()
                .filter(a -> a.getUserUuid().equals(assignment.getUserUuid()))
                .findFirst()
                .orElseGet(() -> {
                    final AttestationUser user = attestationUserDao.save(AttestationUser.builder()
                            .userUuid(assignment.getUserUuid())
                            .attestation(attestation)
                            .sensitiveRoles(false)
                            .build());
                    final HashSet<AttestationUser> attestationUsers = new HashSet<>(attestation.getUsersForAttestation());
                    attestationUsers.add(user);
                    attestation.setUsersForAttestation(attestationUsers);
                    return user;
                });
        if (assignment.isSensitiveRole()) {
            attestationUser.setSensitiveRoles(true);
        }
    }

    private LocalDate findNextAttestationDate(final LocalDate when, final boolean sensitive) {
        final CheckupIntervalEnum interval = settingsService.getScheduledAttestationInterval();
        LocalDate deadline = settingsService.getFirstAttestationDate();
        while (deadline.isBefore(when)) {
            deadline = deadline.plusMonths(sensitive
                    ? sensitiveIntervalToMonths(interval)
                    : intervalToMonths(interval));
        }
        return deadline;
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
