package dk.digitalidentity.rc.attestation.service.temporal;

import com.google.common.collect.Lists;
import dk.digitalidentity.rc.attestation.exception.AttestationDataUpdaterException;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignmentConstraint;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistorySystemRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistorySystemRoleAssignmentConstraint;
import dk.digitalidentity.rc.dao.history.model.HistoryUserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.transaction.TransactionDefinition.ISOLATION_READ_UNCOMMITTED;

/**
 * JDBC Notice!
 * These classes are made to save memory, hibernate will consume around 1gb whereas these use much less.
 * Warning though: the hibernate entity classes are used when fetching from the DB, they are obviously not attached to
 * the hibernate session also all relations are not populated, so be aware!
 */
@Slf4j
@Component
public class SystemRoleAssignmentsUpdaterJdbc {
    private static int progressCount = 0;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private TemporalDao temporalDao;

    @Transactional
    public void updateItSystemAssignments(final LocalDate when) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.setIsolationLevel(ISOLATION_READ_UNCOMMITTED);
        transactionTemplate.setTimeout(600);

        progressCount = 0;
        long recordCount = 0;
        final List<HistoryItSystem> itSystems = temporalDao.listHistoryItSystems(when).stream().filter(i -> !i.isAttestationExempt()).toList();
        for (final HistoryItSystem itSystem : itSystems) {
            final Long cnt = transactionTemplate.execute(t -> temporalDao.listHistoryUserRoles(itSystem.getId()).stream()
                    .flatMap(role -> temporalDao.listHistorySystemRoleAssignment(role.getId()).stream()
                            .map(assignment -> toAttestationAssignment(role, itSystem, assignment)))
                    .map(a -> {
                        logProgress();
                        persist(when, a);
                        return true;
                    }).count());
            recordCount += cnt != null ? cnt : 0;
        }
        if (recordCount == 0) {
            throw new AttestationDataUpdaterException("No system assignments for date %s in the history tables.", when);
        }
        final List<Long> idsToDisable = transactionTemplate.execute(t -> temporalDao.findAllValidSystemRoleAssignmentIdsByUpdatedAtLessThan(when));
        for (List<Long> currentIdsToDisable : Lists.partition(idsToDisable, 500)) {
            transactionTemplate.execute(t -> temporalDao.invalidateSystemRoleAssignmentsWithIdsIn(currentIdsToDisable, when));
        }
        log.info("Invalidated " + idsToDisable.size() + " AttestationSystemRoleAssignment records");
    }

    private void persist(final LocalDate now, final AttestationSystemRoleAssignment assignment) {
        final String recordHash = TemporalHasher.hashEntity(assignment);
        assignment.setRecordHash(recordHash);
        final AttestationSystemRoleAssignment existingRecord = temporalDao.findValidSystemRoleAssignmentWithHash(now, recordHash);
        if (existingRecord != null) {
            TemporalFieldUpdater.updateFields(existingRecord, assignment);
            existingRecord.setUpdatedAt(now);
            if (temporalDao.updateAttestationSystemRoleAssignment(existingRecord) == 0) {
                log.error("Failed to update AttestationSystemRoleAssignment with id=" + assignment.getId());
            }
        } else {
            assignment.setValidFrom(now);
            assignment.setUpdatedAt(now);
            temporalDao.saveAttestationSystemRoleAssignment(assignment);
        }
    }

    private AttestationSystemRoleAssignment toAttestationAssignment(final HistoryUserRole role,
                                                                           final HistoryItSystem itSystem,
                                                                           final HistorySystemRoleAssignment assignment) {
        final AttestationSystemRoleAssignment attestationAssignment = AttestationSystemRoleAssignment.builder()
                .itSystemId(itSystem.getItSystemId())
                .itSystemName(itSystem.getItSystemName())
                .systemRoleName(assignment.getSystemRoleName())
                .systemRoleId(assignment.getSystemRoleId())
                .systemRoleDescription(assignment.getSystemRoleDescription())
                .userRoleId(role.getUserRoleId())
                .userRoleName(role.getUserRoleName())
                .userRoleDescription(role.getUserRoleDescription())
                .responsibleUserUuid(itSystem.getAttestationResponsible())
                .build();
        attestationAssignment.setConstraints(temporalDao.listConstraintsForHistorySystemRoleAssignment(assignment.getId()).stream()
                .map(a -> toConstraint(a, attestationAssignment))
                .collect(Collectors.toSet()));
        return attestationAssignment;
    }

    private static AttestationSystemRoleAssignmentConstraint toConstraint(final HistorySystemRoleAssignmentConstraint constraint,
                                                                          final AttestationSystemRoleAssignment attestationAssignment) {
        return AttestationSystemRoleAssignmentConstraint.builder()
                .valueType(constraint.getConstraintValueType())
                .value(constraint.getConstraintValue())
                .name(constraint.getConstraintName())
                .assignment(attestationAssignment)
                .build();
    }

    private void logProgress() {
        if (++progressCount % 100 == 0) {
            log.info("Processing role assignment, count=" + progressCount);
        }
    }

}
