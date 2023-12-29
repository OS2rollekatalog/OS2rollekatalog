package dk.digitalidentity.rc.attestation.service.temporal;

import dk.digitalidentity.rc.attestation.exception.AttestationDataUpdaterException;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithExceptions;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithTitles;
import dk.digitalidentity.rc.dao.history.model.HistoryOUUser;
import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.transaction.TransactionDefinition.ISOLATION_READ_UNCOMMITTED;

/**
 * JDBC Notice!
 * These classes are made to save memory, hibernate will consume around 1gb whereas these use much less.
 * Warning though: the hibernate entity classes are used when fetching from the DB, they are obviously not attached to
 * the hibernate session also all relations are not populated, so be aware!
 */
@Component
@Slf4j
public class UserAssignmentsUpdaterJdbc {

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private UpdaterContextService updaterContextService;
    @Autowired
    private UserDao userDao;
    @Autowired
    private TemporalDao temporalDao;
    @Autowired
    private EntityManager entityManager;

    public void updateUserRoleAssignments(final LocalDate when) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.setIsolationLevel(ISOLATION_READ_UNCOMMITTED);
        transactionTemplate.setTimeout(600);
        entityManager.setFlushMode(FlushModeType.COMMIT);
        Long recordCount = transactionTemplate.execute(t -> {
            final List<HistoryItSystem> itSystemList = temporalDao.listHistoryItSystems(when);
            return itSystemList.stream()
                    .flatMap(itSystem -> {
                        log.info("Processing it-system: " + itSystem.getItSystemName());
                        return temporalDao.streamHistoryRoleAssignmentsByItSystemAndDate(when, itSystem.getItSystemId()).stream()
                                .map(r -> toUserRoleAssignment(itSystem, r))
                                .filter(Objects::nonNull);
                    })
                    .peek(a -> {
                        logProgress();
                        persist(when, a);
                    })
                    .count();
        });
        recordCount += flattenOURoles(transactionTemplate, when);
        if (recordCount == 0) {
            throw new AttestationDataUpdaterException("No system assignments for date %s in the history tables.", when);
        }
        int invalidated = temporalDao.invalidateAttestationUserRoleAssignmentsByUpdatedAtLessThan(when);
        log.info("Invalidated " + invalidated + " AttestationUserRoleAssignment records");
    }

    private void persist(final LocalDate when, final AttestationUserRoleAssignment assignment) {
        final String recordHash = TemporalHasher.hashEntity(assignment);
        assignment.setRecordHash(recordHash);
        final AttestationUserRoleAssignment existingRecord = temporalDao.findValidUserRoleAssignmentWithHash(when, recordHash);
        if (existingRecord != null) {
            TemporalFieldUpdater.updateFields(existingRecord, assignment);
            existingRecord.setUpdatedAt(when);
            if (temporalDao.updateAttestationUserRoleAssignment(existingRecord) == 0) {
                log.error("Failed to update AttestationUserRoleAssignment with id=" + assignment.getId());
            }
        } else {
            assignment.setValidFrom(when);
            assignment.setUpdatedAt(when);
            temporalDao.saveAttestationUserRoleAssignment(assignment);
        }
    }

    public long flattenOURoles(TransactionTemplate transactionTemplate, final LocalDate when) {
        final List<HistoryOU> allOus = temporalDao.listHistoryOUs(when);
        long recordCount = 0;
        Long recordCountL = transactionTemplate.execute(t -> {
            final List<HistoryOURoleAssignment> ouAssignments = temporalDao.listHistoryOURoleAssignmentsByDate(when);
            return ouAssignments.stream().flatMap(a -> toUserRoleAssignment(getCurrentOu(allOus, a.getOuUuid()), a, when).stream())
                    .peek(a -> logProgress())
                    .peek(a -> persist(when, a))
                    .count();
        });
        recordCount += recordCountL != null ? recordCountL : 0L;

        recordCountL = transactionTemplate.execute(t -> {
            final List<HistoryOURoleAssignmentWithExceptions> ouWithExceptionsAssignments = temporalDao.listHistoryOURoleAssignmentWithExceptionsByDate(when);
            return ouWithExceptionsAssignments.stream().flatMap(a -> toUserRoleAssignment(getCurrentOu(allOus, a.getOuUuid()), a, when).stream())
                    .peek(a -> logProgress())
                    .peek(a -> persist(when, a))
                    .count();
        });
        recordCount += recordCountL != null ? recordCountL : 0L;

        recordCountL = transactionTemplate.execute(t -> {
            final List<HistoryOURoleAssignmentWithTitles> ouWithTitlesAssignments = temporalDao.listHistoryOURoleAssignmentWithTitlesByDate(when);
            return ouWithTitlesAssignments.stream().flatMap(a -> toUserRoleAssignment(getCurrentOu(allOus, a.getOuUuid()), a, when).stream())
                    .peek(a -> logProgress())
                    .peek(a -> persist(when, a))
                    .count();
        });

        recordCount += recordCountL != null ? recordCountL : 0L;
        return recordCount;
    }

    private static String getResponsibleOuUuid(UpdaterContextService.UpdaterContext context, boolean isManager, boolean itSystemResponsible) {
        if (itSystemResponsible) {
            return null;
        }
        return isManager
                ? context.parentOuUuid()
                : context.ouUuid();
    }

    private static String getResponsibleOuName(UpdaterContextService.UpdaterContext context, boolean isManager, boolean itSystemResponsible) {
        if (itSystemResponsible) {
            return null;
        }
        return isManager
                ? context.parentOuName()
                : context.ouName();
    }

    private static HistoryOU getCurrentOu(final List<HistoryOU> allOus, final String ouUuid) {
        return allOus.stream()
                .filter(o -> o.getOuUuid().equals(ouUuid))
                .findFirst().orElse(null);
    }

    private static AssignedThroughType toAssignedThrough(final HistoryRoleAssignment historyRoleAssignment) {
        if (historyRoleAssignment.getAssignedThroughType() == null) {
            return null;
        }
        return switch (historyRoleAssignment.getAssignedThroughType()) {
            // ROLEGROUP and DIRECT are both directly assigned.
            case DIRECT, ROLEGROUP -> AssignedThroughType.DIRECT;
            case POSITION -> AssignedThroughType.POSITION;
            case ORGUNIT -> AssignedThroughType.ORGUNIT;
            case TITLE -> AssignedThroughType.TITLE;
        };
    }

    private AttestationUserRoleAssignment toUserRoleAssignment(final HistoryItSystem itSystem, final HistoryRoleAssignment historyRoleAssignment) {
        final UpdaterContextService.UpdaterContext context = updaterContextService.contextBuilder(historyRoleAssignment.getDato(), itSystem.getItSystemId())
                .withOrgUnit(historyRoleAssignment.getOrgUnitUuid())
                .withRole(historyRoleAssignment.getRoleId())
                .withRoleGroup(historyRoleAssignment.getRoleRoleGroupId())
                .withUser(historyRoleAssignment.getUserUuid())
                .getContext();
        if (context.isItSystemExempt()) {
            return null;
        }

        boolean itSystemResponsible = historyRoleAssignment.getRoleRoleGroupId() == null
                && context.isRoleAssignmentAttestationByAttestationResponsible();

        return AttestationUserRoleAssignment.builder()
                .itSystemId(itSystem.getItSystemId())
                .itSystemName(itSystem.getItSystemName())
                .userUuid(historyRoleAssignment.getUserUuid())
                .userId(context.userId())
                .userName(context.userName())
                .userRoleId(historyRoleAssignment.getRoleId())
                .userRoleName(context.roleName())
                .userRoleDescription(context.roleDescription())
                .roleGroupId(historyRoleAssignment.getRoleRoleGroupId())
                .roleGroupName(historyRoleAssignment.getRoleRoleGroup())
                .roleGroupDescription(context.roleGroupDescription())
                .responsibleUserUuid(itSystemResponsible ? itSystem.getAttestationResponsible() : null)
                .responsibleOuName(getResponsibleOuName(context, context.isManager(), itSystemResponsible))
                .responsibleOuUuid(getResponsibleOuUuid(context, context.isManager(), itSystemResponsible))
                .manager(!itSystemResponsible && context.isManager())
                .assignedThroughType(toAssignedThrough(historyRoleAssignment))
                .assignedThroughName(historyRoleAssignment.getAssignedThroughName())
                .assignedThroughUuid(historyRoleAssignment.getAssignedThroughUuid())
                .inherited(false)
                .sensitiveRole(context.isRoleSensitive())
                .roleOuUuid(context.ouUuid())
                .roleOuName(context.ouName())
                .build();
    }


    private List<AttestationUserRoleAssignment> toUserRoleAssignment(final HistoryOU currentOu,
                                                                     final HistoryOURoleAssignment assignment, final LocalDate when) {
        final UpdaterContextService.UpdaterContext context = updaterContextService.contextBuilder(when, assignment.getRoleItSystemId())
                .withOrgUnit(assignment.getOuUuid())
                .withRole(assignment.getRoleId())
                .withRoleGroup(assignment.getRoleRoleGroupId())
                .getContext();
        if (context.isItSystemExempt()) {
            return Collections.emptyList();
        }

        boolean itSystemResponsible = assignment.getRoleRoleGroupId() == null
                && context.isRoleAssignmentAttestationByAttestationResponsible();
        final List<HistoryOUUser> users = temporalDao.listHistoryOUUsers(currentOu.getId());
        return users.stream()
                .filter(u -> !u.getDoNotInherit())
                .map(u -> {
                    final User currentUser = userDao.findById(u.getUserUuid())
                            .orElseThrow();
                    final boolean isManager = context.isManager(currentUser);
                    boolean inherited = assignment.getAssignedThroughType() == AssignedThrough.ORGUNIT;
                    return AttestationUserRoleAssignment.builder()
                            .itSystemId(assignment.getRoleItSystemId())
                            .itSystemName(context.itSystemName())
                            .userId(currentUser.getUserId())
                            .userName(currentUser.getName())
                            .userUuid(currentUser.getUuid())
                            .userRoleId(assignment.getRoleId())
                            .userRoleName(context.roleName())
                            .userRoleDescription(context.roleDescription())
                            .roleGroupId(assignment.getRoleRoleGroupId())
                            .roleGroupName(assignment.getRoleRoleGroup())
                            .roleGroupDescription(context.roleGroupDescription())
                            .assignedThroughType(AssignedThroughType.ORGUNIT)
                            .assignedThroughName(assignment.getAssignedThroughName() != null
                                    ? assignment.getAssignedThroughName()
                                    : context.ouName())
                            .assignedThroughUuid(assignment.getAssignedThroughUuid() != null
                                    ? assignment.getAssignedThroughUuid()
                                    : context.ouUuid())
                            .responsibleUserUuid(itSystemResponsible && !inherited ? context.responsibleUserUuid() : null)
                            .responsibleOuName(getResponsibleOuName(context, isManager, itSystemResponsible))
                            .responsibleOuUuid(getResponsibleOuUuid(context, isManager, itSystemResponsible))
                            .manager(!itSystemResponsible && isManager)
                            .inherited(inherited)
                            .sensitiveRole(context.isRoleSensitive())
                            .roleOuUuid(context.ouUuid())
                            .roleOuName(context.ouName())
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<AttestationUserRoleAssignment> toUserRoleAssignment(final HistoryOU currentOu,
                                                                     final HistoryOURoleAssignmentWithExceptions assignment, final LocalDate when) {
        final UpdaterContextService.UpdaterContext context = updaterContextService.contextBuilder(when, assignment.getRoleItSystemId())
                .withOrgUnit(assignment.getOuUuid())
                .withRole(assignment.getRoleId())
                .withRoleGroup(assignment.getRoleRoleGroupId())
                .getContext();
        if (context.isItSystemExempt()) {
            return Collections.emptyList();
        }

        boolean itSystemResponsible = assignment.getRoleRoleGroupId() == null
                && context.isRoleAssignmentAttestationByAttestationResponsible();

        final List<HistoryOUUser> users = temporalDao.listHistoryOUUsers(currentOu.getId());
        return users.stream()
                .filter(
                        // Do not create roles for excepted users
                        u -> assignment.getUserUuids() == null ||
                                !assignment.getUserUuids().contains(u.getUserUuid())
                )
                .filter(u -> !u.getDoNotInherit())
                .map(u -> {
                    final User currentUser = userDao.findById(u.getUserUuid())
                            .orElseThrow();
                    final boolean isManager = context.isManager(currentUser);
                    return AttestationUserRoleAssignment.builder()
                            .itSystemId(assignment.getRoleItSystemId())
                            .itSystemName(context.itSystemName())
                            .userId(currentUser.getUserId())
                            .userName(currentUser.getName())
                            .userUuid(currentUser.getUuid())
                            .userRoleId(assignment.getRoleId())
                            .userRoleName(context.roleName())
                            .userRoleDescription(context.roleDescription())
                            .roleGroupId(assignment.getRoleRoleGroupId())
                            .roleGroupName(assignment.getRoleRoleGroup())
                            .roleGroupDescription(context.roleGroupDescription())
                            .responsibleUserUuid(itSystemResponsible ? context.responsibleUserUuid() : null)
                            .responsibleOuName(getResponsibleOuName(context, isManager, itSystemResponsible))
                            .responsibleOuUuid(getResponsibleOuUuid(context, isManager, itSystemResponsible))
                            .manager(!itSystemResponsible && isManager)
                            .assignedThroughName(currentOu.getOuName())
                            .assignedThroughUuid(currentOu.getOuUuid())
                            .assignedThroughType(AssignedThroughType.ORGUNIT)
                            .inherited(false)
                            .sensitiveRole(context.isRoleSensitive())
                            .roleOuUuid(context.ouUuid())
                            .roleOuName(context.ouName())
                            .build();
                })
                .collect(Collectors.toList());
    }


    private List<AttestationUserRoleAssignment> toUserRoleAssignment(final HistoryOU currentOu,
                                                                     final HistoryOURoleAssignmentWithTitles assignment, final LocalDate when) {
        final UpdaterContextService.UpdaterContext context = updaterContextService.contextBuilder(when, assignment.getRoleItSystemId())
                .withOrgUnit(assignment.getOuUuid())
                .withRole(assignment.getRoleId())
                .withRoleGroup(assignment.getRoleRoleGroupId())
                .getContext();
        if (context.isItSystemExempt()) {
            return Collections.emptyList();
        }

        boolean itSystemResponsible = assignment.getRoleRoleGroupId() == null
                && context.isRoleAssignmentAttestationByAttestationResponsible();

        final List<HistoryOUUser> users = temporalDao.listHistoryOUUsers(currentOu.getId());
        return users.stream()
                .filter(
                        u -> assignment.getTitleUuids().contains(u.getTitleUuid())
                )
                .filter(u -> !u.getDoNotInherit())
                .map(u -> {
                    final User currentUser = userDao.findById(u.getUserUuid())
                            .orElseThrow();
                    final boolean isManager = context.isManager(currentUser);
                    return AttestationUserRoleAssignment.builder()
                            .itSystemId(assignment.getRoleItSystemId())
                            .itSystemName(context.itSystemName())
                            .userId(currentUser.getUserId())
                            .userName(currentUser.getName())
                            .userUuid(currentUser.getUuid())
                            .userRoleId(assignment.getRoleId())
                            .userRoleName(context.roleName())
                            .userRoleDescription(context.roleDescription())
                            .roleGroupId(assignment.getRoleRoleGroupId())
                            .roleGroupName(assignment.getRoleRoleGroup())
                            .roleGroupDescription(context.roleGroupDescription())
                            .responsibleUserUuid(itSystemResponsible ? context.responsibleUserUuid() : null)
                            .responsibleOuName(getResponsibleOuName(context, isManager, itSystemResponsible))
                            .responsibleOuUuid(getResponsibleOuUuid(context, isManager, itSystemResponsible))
                            .manager(!itSystemResponsible && isManager)
                            .assignedThroughName(currentOu.getOuName())
                            .assignedThroughUuid(currentOu.getOuUuid())
                            .assignedThroughType(AssignedThroughType.ORGUNIT)
                            .inherited(false)
                            .sensitiveRole(context.isRoleSensitive())
                            .roleOuUuid(context.ouUuid())
                            .roleOuName(context.ouName())
                            .build();
                })
                .collect(Collectors.toList());
    }


    private static int progressCount = 0;
    private void logProgress() {
        if (++progressCount % 100 == 0) {
            log.info("Processing user assignment, count=" + progressCount);
        }
    }

}
