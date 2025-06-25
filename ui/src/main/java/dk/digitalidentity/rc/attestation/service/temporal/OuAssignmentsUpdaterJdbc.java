package dk.digitalidentity.rc.attestation.service.temporal;


import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithExceptions;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithNegativeTitles;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithTitles;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * JDBC Notice!
 * These classes are made to save memory, hibernate will consume around 1gb whereas these use much less.
 * Warning though: the hibernate entity classes are used when fetching from the DB, they are obviously not attached to
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

        final List<HistoryOURoleAssignmentWithExceptions> ouWithExceptionsAssignments = temporalDao.listHistoryOURoleAssignmentWithExceptionsByDate(when);
        ouWithExceptionsAssignments.stream().map(a -> toOuRoleAssignment(a, when))
                .filter(Objects::nonNull)
                .peek(a -> logProgress())
                .forEach(a -> persist(when, a));

        final List<HistoryOURoleAssignmentWithTitles> ouWithTitlesAssignments = temporalDao.listHistoryOURoleAssignmentWithTitlesByDate(when);
        ouWithTitlesAssignments.stream().map(a -> toOuRoleAssignment(a, when))
                .filter(Objects::nonNull)
                .peek(a -> logProgress())
                .forEach(a -> persist(when, a));

        final List<HistoryOURoleAssignmentWithNegativeTitles> ouWithNegativeTitlesAssignments = temporalDao.listHistoryOURoleAssignmentWithNegativeTitlesByDate(when);
        ouWithNegativeTitlesAssignments.stream().map(a -> toOuRoleAssignment(a, when))
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
                .exceptedUserUuids(Collections.emptyList())
                .titleUuids(Collections.emptyList())
                .assignedThroughUuid(historyOURoleAssignment.getAssignedThroughUuid())
                .assignedThroughName(historyOURoleAssignment.getAssignedThroughName())
                .assignedThroughType(AssignedThroughType.valueOf(historyOURoleAssignment.getAssignedThroughType().name()))
                .itSystemId(historyOURoleAssignment.getRoleItSystemId())
                .itSystemName(context.itSystemName())
                .inherited(inherited)
                .inherit(historyOURoleAssignment.getInherit())
                .sensitiveRole(context.isRoleSensitive())
                .extraSensitiveRole(context.isRoleExtraSensitive())
                .exceptedTitleUuids(Collections.emptyList())
                .build();
    }

    private AttestationOuRoleAssignment toOuRoleAssignment(final HistoryOURoleAssignmentWithTitles historyOURoleAssignmentWithTitles, final LocalDate when) {
        final UpdaterContextService.UpdaterContext context = updaterContextService.contextBuilder(when, historyOURoleAssignmentWithTitles.getRoleItSystemId())
                .withOrgUnit(historyOURoleAssignmentWithTitles.getOuUuid())
                .withRole(historyOURoleAssignmentWithTitles.getRoleId())
                .withRoleGroup(historyOURoleAssignmentWithTitles.getRoleRoleGroupId())
                .getContext();
        if (context.isItSystemExempt()) {
            return null;
        }
        boolean inherited = historyOURoleAssignmentWithTitles.getAssignedThroughType() == AssignedThrough.ORGUNIT
                && !StringUtils.equals(historyOURoleAssignmentWithTitles.getAssignedThroughUuid(), historyOURoleAssignmentWithTitles.getOuUuid());
        final String responsibleUuid = getResponsibleUserUuid(historyOURoleAssignmentWithTitles.getRoleRoleGroupId(), context);
        final String responsibleOuUuid = responsibleUuid == null ? historyOURoleAssignmentWithTitles.getOuUuid() : null;
        final String responsibleOuName = responsibleUuid == null ? context.ouName() : null;

        return AttestationOuRoleAssignment.builder()
                .roleId(historyOURoleAssignmentWithTitles.getRoleId())
                .roleName(context.roleName())
                .roleDescription(context.roleDescription())
                .roleGroupId(historyOURoleAssignmentWithTitles.getRoleRoleGroupId())
                .roleGroupName(historyOURoleAssignmentWithTitles.getRoleRoleGroup())
                .roleGroupDescription(context.roleGroupDescription())
                .ouUuid(historyOURoleAssignmentWithTitles.getOuUuid())
                .ouName(context.ouName())
                .responsibleUserUuid(responsibleUuid)
                .responsibleOuUuid(responsibleOuUuid)
                .responsibleOuName(responsibleOuName)
                .titleUuids(new ArrayList<>(historyOURoleAssignmentWithTitles.getTitleUuids()))
                .assignedThroughType(AssignedThroughType.ORGUNIT)
                .assignedThroughName(historyOURoleAssignmentWithTitles.getAssignedThroughName() == null ? context.ouName() : historyOURoleAssignmentWithTitles.getAssignedThroughName())
                .assignedThroughUuid(historyOURoleAssignmentWithTitles.getAssignedThroughUuid() == null ? historyOURoleAssignmentWithTitles.getOuUuid() : historyOURoleAssignmentWithTitles.getAssignedThroughUuid())
                .exceptedUserUuids(Collections.emptyList())
                .itSystemId(historyOURoleAssignmentWithTitles.getRoleItSystemId())
                .itSystemName(context.itSystemName())
                .inherited(inherited)
                .inherit(historyOURoleAssignmentWithTitles.getInherit())
                .sensitiveRole(context.isRoleSensitive())
                .extraSensitiveRole(context.isRoleExtraSensitive())
                .exceptedTitleUuids(Collections.emptyList())
                .build();
    }

    private AttestationOuRoleAssignment toOuRoleAssignment(final HistoryOURoleAssignmentWithNegativeTitles historyOURoleAssignmentWithNegativeTitles, final LocalDate when) {
        final UpdaterContextService.UpdaterContext context = updaterContextService.contextBuilder(when, historyOURoleAssignmentWithNegativeTitles.getRoleItSystemId())
                .withOrgUnit(historyOURoleAssignmentWithNegativeTitles.getOuUuid())
                .withRole(historyOURoleAssignmentWithNegativeTitles.getRoleId())
                .withRoleGroup(historyOURoleAssignmentWithNegativeTitles.getRoleRoleGroupId())
                .getContext();
        if (context.isItSystemExempt()) {
            return null;
        }
        final String responsibleUuid = getResponsibleUserUuid(historyOURoleAssignmentWithNegativeTitles.getRoleRoleGroupId(), context);
        final String responsibleOuUuid = responsibleUuid == null ? historyOURoleAssignmentWithNegativeTitles.getOuUuid() : null;
        final String responsibleOuName = responsibleUuid == null ? context.ouName() : null;

        boolean inherited = historyOURoleAssignmentWithNegativeTitles.getAssignedThroughType() == AssignedThrough.ORGUNIT
                && !StringUtils.equals(historyOURoleAssignmentWithNegativeTitles.getAssignedThroughUuid(), historyOURoleAssignmentWithNegativeTitles.getOuUuid());

        return AttestationOuRoleAssignment.builder()
                .roleId(historyOURoleAssignmentWithNegativeTitles.getRoleId())
                .roleName(context.roleName())
                .roleDescription(context.roleDescription())
                .roleGroupId(historyOURoleAssignmentWithNegativeTitles.getRoleRoleGroupId())
                .roleGroupName(historyOURoleAssignmentWithNegativeTitles.getRoleRoleGroup())
                .roleGroupDescription(context.roleGroupDescription())
                .ouUuid(historyOURoleAssignmentWithNegativeTitles.getOuUuid())
                .ouName(context.ouName())
                .responsibleUserUuid(responsibleUuid)
                .responsibleOuUuid(responsibleOuUuid)
                .responsibleOuName(responsibleOuName)
                .titleUuids(Collections.emptyList())
                .assignedThroughType(AssignedThroughType.ORGUNIT)
                .assignedThroughName(context.ouName())
                .assignedThroughUuid(historyOURoleAssignmentWithNegativeTitles.getOuUuid())
                .exceptedUserUuids(Collections.emptyList())
                .itSystemId(historyOURoleAssignmentWithNegativeTitles.getRoleItSystemId())
                .itSystemName(context.itSystemName())
                .inherited(inherited)
                .inherit(historyOURoleAssignmentWithNegativeTitles.getInherit())
                .sensitiveRole(context.isRoleSensitive())
                .extraSensitiveRole(context.isRoleExtraSensitive())
                .exceptedTitleUuids(new ArrayList<>(historyOURoleAssignmentWithNegativeTitles.getTitleUuids()))
                .build();
    }

    private AttestationOuRoleAssignment toOuRoleAssignment(final HistoryOURoleAssignmentWithExceptions historyOURoleAssignmentWithExceptions, final LocalDate when) {
        final UpdaterContextService.UpdaterContext context = updaterContextService.contextBuilder(when, historyOURoleAssignmentWithExceptions.getRoleItSystemId())
                .withOrgUnit(historyOURoleAssignmentWithExceptions.getOuUuid())
                .withRole(historyOURoleAssignmentWithExceptions.getRoleId())
                .withRoleGroup(historyOURoleAssignmentWithExceptions.getRoleRoleGroupId())
                .getContext();
        if (context.isItSystemExempt()) {
            return null;
        }
        final String responsibleUuid = getResponsibleUserUuid(historyOURoleAssignmentWithExceptions.getRoleRoleGroupId(), context);
        final String responsibleOuUuid = responsibleUuid == null ? historyOURoleAssignmentWithExceptions.getOuUuid() : null;
        final String responsibleOuName = responsibleUuid == null ? context.ouName() : null;

        return AttestationOuRoleAssignment.builder()
                .roleId(historyOURoleAssignmentWithExceptions.getRoleId())
                .roleName(context.roleName())
                .roleDescription(context.roleDescription())
                .roleGroupId(historyOURoleAssignmentWithExceptions.getRoleRoleGroupId())
                .roleGroupName(historyOURoleAssignmentWithExceptions.getRoleRoleGroup())
                .roleGroupDescription(context.roleGroupDescription())
                .ouUuid(historyOURoleAssignmentWithExceptions.getOuUuid())
                .ouName(context.ouName())
                .responsibleUserUuid(responsibleUuid)
                .responsibleOuUuid(responsibleOuUuid)
                .responsibleOuName(responsibleOuName)
                .exceptedUserUuids(historyOURoleAssignmentWithExceptions.getUserUuids())
                .titleUuids(Collections.emptyList())
                .assignedThroughType(AssignedThroughType.DIRECT)
                .itSystemId(historyOURoleAssignmentWithExceptions.getRoleItSystemId())
                .itSystemName(context.itSystemName())
                .inherited(false)
                .inherit(false)
                .sensitiveRole(context.isRoleSensitive())
                .extraSensitiveRole(context.isRoleExtraSensitive())
                .build();
    }

    private static String getResponsibleUserUuid(final Long roleRoleGroupId, final UpdaterContextService.UpdaterContext context) {
        String responsibleUuid = null;
        final boolean itSystemResponsible = roleRoleGroupId == null
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
