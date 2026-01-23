package dk.digitalidentity.rc.attestation.service.temporal;

import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentExclusion.ExclusionType;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
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
    @Autowired
    private UpdaterContextService updaterContextService;

    @Transactional
    public void updateOuAssignments(final LocalDate when) {
        progressCount = 0;
        List<HistoryOURoleAssignment> ouAssignments = temporalDao.listHistoryOURoleAssignmentsByDate(when);
        ouAssignments.stream().map(a -> toOuRoleAssignment(a, when))
                .filter(Objects::nonNull)
                .peek(a -> logProgress())
                .forEach(a -> persist(when, a));

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

    private AttestationOuRoleAssignment toOuRoleAssignment(final HistoryOURoleAssignment historyOURoleAssignment, final LocalDate when) {
        final UpdaterContextService.UpdaterContext context = updaterContextService.contextBuilder(when, historyOURoleAssignment.getRoleItSystemId())
                .withOrgUnit(historyOURoleAssignment.getOuUuid())
                .withRole(historyOURoleAssignment.getRoleId())
                .withRoleGroup(historyOURoleAssignment.getRoleRoleGroupId())
                .getContext();

        if (context.isItSystemExempt()) {
            return null;
        }

        boolean inherited = historyOURoleAssignment.getAssignedThroughType() == AssignedThrough.ORGUNIT
                && !StringUtils.equals(historyOURoleAssignment.getAssignedThroughUuid(), historyOURoleAssignment.getOuUuid());

        final String responsibleUuid = getResponsibleUserUuid(historyOURoleAssignment.getRoleRoleGroupId(), context);

        final String responsibleOuUuid = !inherited && responsibleUuid == null ? historyOURoleAssignment.getAssignedThroughUuid() : null;
        final String responsibleOuName = !inherited && responsibleUuid == null ? historyOURoleAssignment.getAssignedThroughName() : null;

        // Extract exclusions
        final List<String> titleUuids = historyOURoleAssignment.getExclusions().stream()
                .filter(e -> e.getExclusionType() == ExclusionType.titles)
                .flatMap(e -> splitCsv(e.getTitleUuids()).stream())
                .toList();

        final List<String> exceptedTitleUuids = historyOURoleAssignment.getExclusions().stream()
                .filter(e -> e.getExclusionType() == ExclusionType.negative_titles)
                .flatMap(e -> splitCsv(e.getTitleUuids()).stream())
                .toList();

        final List<String> exceptedUserUuids = historyOURoleAssignment.getExclusions().stream()
                .filter(e -> e.getExclusionType() == ExclusionType.excepted_users)
                .flatMap(e -> splitCsv(e.getUserUuids()).stream())
                .toList();

		final List<String> functionUuids = historyOURoleAssignment.getExclusions().stream()
				.filter(e -> e.getExclusionType() == ExclusionType.functions)
				.flatMap(e -> splitCsv(e.getFunctionUuids()).stream())
				.toList();

        return AttestationOuRoleAssignment.builder()
                .roleId(historyOURoleAssignment.getRoleId())
                .roleName(context.roleName())
                .roleDescription(context.roleDescription())
                .roleGroupId(historyOURoleAssignment.getRoleRoleGroupId())
                .roleGroupName(historyOURoleAssignment.getRoleRoleGroup())
                .roleGroupDescription(context.roleGroupDescription())
                .ouUuid(historyOURoleAssignment.getOuUuid())
                .ouName(context.ouName())
                .responsibleUserUuid(responsibleUuid)
                .responsibleOuUuid(responsibleOuUuid)
                .responsibleOuName(responsibleOuName)
                .titleUuids(titleUuids)
                .exceptedTitleUuids(exceptedTitleUuids)
                .exceptedUserUuids(exceptedUserUuids)
						.assignedThroughType(historyOURoleAssignment.getAssignedThroughType() == null
										? (!exceptedTitleUuids.isEmpty() ? AssignedThroughType.DIRECT : AssignedThroughType.ORGUNIT) // DIRECT for exceptedUser assignments
										: AssignedThroughType.valueOf(historyOURoleAssignment.getAssignedThroughType().name()))
                .assignedThroughName(historyOURoleAssignment.getAssignedThroughName() == null ? context.ouName() : historyOURoleAssignment.getAssignedThroughName())
                .assignedThroughUuid(historyOURoleAssignment.getAssignedThroughUuid() == null ? context.ouUuid() : historyOURoleAssignment.getAssignedThroughUuid())
                .itSystemId(historyOURoleAssignment.getRoleItSystemId())
                .itSystemName(context.itSystemName())
                .inherited(inherited)
                .inherit(Boolean.TRUE.equals(historyOURoleAssignment.getInherit()))
                .sensitiveRole(context.isRoleSensitive())
                .extraSensitiveRole(context.isRoleExtraSensitive())
				.manager(Boolean.TRUE.equals(historyOURoleAssignment.getManager()))
				.substitutes(Boolean.TRUE.equals(historyOURoleAssignment.getSubstitutes()))
				.functionUuids(functionUuids)
                .build();
    }

    // Helper to split comma-separated UUIDs safely
    private List<String> splitCsv(String csv) {
        return csv == null || csv.isBlank()
                ? Collections.emptyList()
                : Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
    }

    private static String getResponsibleUserUuid(final Long roleRoleGroupId, final UpdaterContextService.UpdaterContext context) {
        String responsibleUuid = null;
        final boolean itSystemResponsible = (roleRoleGroupId == null || roleRoleGroupId == 0L)
                && context.isRoleAssignmentAttestationByAttestationResponsible();
        if (itSystemResponsible && context.attestationResponsible() != null) {
            responsibleUuid = context.attestationResponsible().getUuid();
        }
        return responsibleUuid;
    }


    private void logProgress() {
        if (++progressCount % 100 == 0) {
            log.info("Processing ou assignment, count=" + progressCount);
        }
    }

}
