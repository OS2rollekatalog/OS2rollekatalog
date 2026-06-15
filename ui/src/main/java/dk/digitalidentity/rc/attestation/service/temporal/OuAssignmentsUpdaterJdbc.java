package dk.digitalidentity.rc.attestation.service.temporal;

import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * JDBC Notice!
 * These classes are made to save memory, hibernate will consume around 1gb whereas these use much less.
 * Warning though: the hibernate section classes are used when fetching from the DB, they are obviously not attached to
 * the hibernate session also all relations are not populated, so be aware!
 */
@Slf4j
@Component
public class OuAssignmentsUpdaterJdbc {
    private static int progressCount = 0;

    @Autowired
    private TemporalDao temporalDao;

    @Transactional
    public void updateOuAssignments(final LocalDate when) {
        progressCount = 0;
        List<HistoricOuAssignment> ouAssignments = temporalDao.listHistoricOuAssignmentsByDate(when);
        ouAssignments.stream().map(OuAssignmentsUpdaterJdbc::toOuRoleAssignment)
                .filter(Objects::nonNull)
                .forEach(a -> {
					logProgress();
					persist(when, a);
				});

        int invalidated = temporalDao.invalidateAttestationOuRoleAssignmentsByUpdatedAtLessThan(when);
        log.info("Invalidated " + invalidated + " AttestationOuRoleAssignment records");
    }

    private void persist(final LocalDate now, final AttestationOuRoleAssignment assignment) {
        final String recordHash = TemporalHasher.hashEntity(assignment);
        assignment.setRecordHash(recordHash);
        final AttestationOuRoleAssignment existingRecord = temporalDao.findValidOuRoleAssignmentWithHash(now, recordHash);
        if (existingRecord != null) {
            TemporalFieldUpdater.updateFields(existingRecord, assignment);
            existingRecord.setUpdatedAt(now);
            if (temporalDao.updateAttestationOuRoleAssignment(existingRecord) == 0) {
                log.error("Failed to update AttestationOuRoleAssignment with id=" + assignment.getId());
            }
        } else {
            assignment.setValidFrom(now);
            assignment.setUpdatedAt(now);
            temporalDao.saveAttestationOuRoleAssignment(assignment);
        }
    }

    public void updateAllOuHashOnly(final LocalDate now) {
        List<AttestationOuRoleAssignment> assignments = temporalDao.findAllValidOuRoleAssignment(now);
        for (AttestationOuRoleAssignment assignment : assignments) {
            final String newHash = TemporalHasher.hashEntity(assignment);
            if (!newHash.equals(assignment.getRecordHash())) {
                assignment.setRecordHash(newHash);
                temporalDao.updateAttestationOuRoleAssignment(assignment);
            }
        }
    }

    private static AttestationOuRoleAssignment toOuRoleAssignment(final HistoricOuAssignment h) {
        if (h.isItSystemAttestationExempt()) {
            return null;
        }

        boolean inherited = h.getAssignedThroughType() == AssignedThrough.ORGUNIT
                && !Objects.equals(h.getAssignedThroughUuid(), h.getOuUuid());

        String responsibleOuUuid = !inherited && h.getResponsibleCollectionId() == null
                ? h.getAssignedThroughUuid() : null;
        String responsibleOuName = !inherited && h.getResponsibleCollectionId() == null
                ? h.getAssignedThroughName() : null;

        List<String> titleUuids     = exclusionsOfType(h, HistoricOuAssignmentExclusion.ExclusionType.POSITIVE_TITLES);
        List<String> exceptedTitles = exclusionsOfType(h, HistoricOuAssignmentExclusion.ExclusionType.NEGATIVE_TITLES);
        List<String> exceptedUsers  = exclusionsOfType(h, HistoricOuAssignmentExclusion.ExclusionType.EXCEPTED_USERS);
        List<String> functionUuids  = exclusionsOfType(h, HistoricOuAssignmentExclusion.ExclusionType.FUNCTIONS);

        return AttestationOuRoleAssignment.builder()
                .roleId(h.getRoleId())
                .roleName(h.getRoleName())
                .roleDescription(h.getRoleDescription())
                .roleGroupId(h.getRoleRoleGroupId())
                .roleGroupName(h.getRoleRoleGroupName())
                .roleGroupDescription(h.getRoleGroupDescription())
                .ouUuid(h.getOuUuid())
                .ouName(h.getOuName())
                .responsibleCollectionId(h.getResponsibleCollectionId())
                .responsibleOuUuid(responsibleOuUuid)
                .responsibleOuName(responsibleOuName)
                .titleUuids(titleUuids)
                .exceptedTitleUuids(exceptedTitles)
                .exceptedUserUuids(exceptedUsers)
                .functionUuids(functionUuids)
                .itSystemId(h.getItSystemId())
                .itSystemName(h.getItSystemName())
                .assignedThroughType(toAssignedThroughType(h.getAssignedThroughType()))
                .assignedThroughName(h.getAssignedThroughName() != null ? h.getAssignedThroughName() : h.getOuName())
                .assignedThroughUuid(h.getAssignedThroughUuid() != null ? h.getAssignedThroughUuid() : h.getOuUuid())
                .inherited(inherited)
                .inherit(h.isInheritToChildren())
                .sensitiveRole(h.isSensitiveRole())
                .extraSensitiveRole(h.isExtraSensitiveRole())
                .manager(h.isAppliesOnlyToManager())
                .substitutes(h.isAppliesAlsoToSubstitutes())
                .build();
    }

    private static AssignedThroughType toAssignedThroughType(AssignedThrough type) {
        if (type == null) {
            // Defensive fallback: HistoricOuAssignmentService always sets this, but guard anyway
            return AssignedThroughType.DIRECT;
        }
        return AssignedThroughType.valueOf(type.name());
    }

    private static List<String> exclusionsOfType(HistoricOuAssignment h,
                                                  HistoricOuAssignmentExclusion.ExclusionType type) {
        return h.getExclusions().stream()
                .filter(e -> e.getExclusionType() == type)
                .findFirst()
                .map(HistoricOuAssignmentExclusion::getUuids)
                .orElse(Collections.emptyList());
    }

    private void logProgress() {
        if (++progressCount % 100 == 0) {
            log.info("Processing ou assignment, count=" + progressCount);
        }
    }
}
