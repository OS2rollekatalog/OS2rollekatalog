package dk.digitalidentity.rc.attestation.service.temporal;

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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

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
    private TemporalDao temporalDao;

    @Transactional()
    public void updateItSystemAssignments(final LocalDate when) {
        progressCount = 0;
        List<HistoryItSystem> itSystems = temporalDao.listHistoryItSystems(when);
        final long recordCount = itSystems.stream()
                .flatMap(system ->
                    temporalDao.listHistoryUserRoles(system.getId()).stream()
                            .flatMap(role ->
                                    temporalDao.listHistorySystemRoleAssignment(role.getId()).stream()
                                            .map(assignment -> toAttestationAssignment(role, system, assignment))
                            )
                )
                .map(a -> {
                    logProgress();
                    persist(when, a);
                    return true;
                }).count();
        if (recordCount == 0) {
            throw new AttestationDataUpdaterException("No system assignments for date %s in the history tables.", when);
        }
        int invalidated = temporalDao.invalidateAttestationSystemRoleAssignmentsByUpdatedAtLessThan(when);
        log.info("Invalidated " + invalidated + " AttestationSystemRoleAssignment records");
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
