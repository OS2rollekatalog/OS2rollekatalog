package dk.digitalidentity.rc.attestation.service.temporal;

import com.google.common.collect.Lists;
import dk.digitalidentity.rc.attestation.exception.AttestationDataUpdaterException;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.attestation.service.AttestationCachedOuService;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithExceptions;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithNegativeTitles;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithTitles;
import dk.digitalidentity.rc.dao.history.model.HistoryOUUser;
import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
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
    private AttestationCachedOuService cachedOuService;
    @Autowired
    private EntityManager entityManager;

    public void updateUserRoleAssignments(final LocalDate when) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.setIsolationLevel(ISOLATION_READ_UNCOMMITTED);
        transactionTemplate.setTimeout(600);
        entityManager.setFlushMode(FlushModeType.COMMIT);

        long recordCount = 0;
        final List<HistoryItSystem> itSystemList = temporalDao.listHistoryItSystems(when);
        for (final HistoryItSystem itSystem : itSystemList) {
            log.info("Processing it-system: " + itSystem.getItSystemName());
            final Long cnt = transactionTemplate.execute(t -> {
                final List<Long> updatedIds = new ArrayList<>();

                // List all the HistoryRoleAssignment for current date and update their AttestationUserRoleAssignment counterparts
                //noinspection MappingBeforeCount
                long processedCount = temporalDao.listHistoryRoleAssignmentsByItSystemAndDate(when, itSystem.getItSystemId()).stream()
                        .map(r -> toUserRoleAssignment(itSystem, r, when))
                        .filter(Objects::nonNull)
                        .peek(a -> {
                            logProgress();
                            updateOrCreate(when, a, updatedIds);
                        })
                        .count();
                // Now update the updated timestamp for all touched entities
                Lists.partition(updatedIds, 500)
                        .forEach(updatedId -> temporalDao.setUpdatedTimestampForUserRoleAssignmentsWithIdsIn(updatedId, when));

                return processedCount;
            });
            recordCount += cnt != null ? cnt : 0;
        }
        recordCount += flattenOURoles(transactionTemplate, when);
        if (recordCount == 0) {
            throw new AttestationDataUpdaterException("No system assignments for date %s in the history tables.", when);
        }
        final List<Long> idsToDisable = transactionTemplate.execute(t -> temporalDao.findAllValidUserRoleAssignmentIdsByUpdatedAtLessThan(when));
        for (List<Long> currentIdsToDisable : Lists.partition(idsToDisable, 500)) {
            transactionTemplate.execute(t -> temporalDao.invalidateUserRoleAssignmentsWithIdsIn(currentIdsToDisable, when));
        }
        log.info("Invalidated " + idsToDisable.size() + " AttestationUserRoleAssignment records");
    }

    private void updateOrCreate(final LocalDate when, final AttestationUserRoleAssignment a, final List<Long> updatedIds) {
        temporalDao.findValidUserRoleAssignmentWithHash(when, a.getRecordHash())
                .ifPresentOrElse(
                        // If a record with the calculated hash already exist, we update it
                        existingAssignment -> updatedIds.add(
                                update(existingAssignment, a)
                        ),
                        // The record does not exist, create it
                        () -> persist(when, a)
                );
    }

    /**
     * @return id of the updated entity
     */
    private Long update(final AttestationUserRoleAssignment existingAssignment, final AttestationUserRoleAssignment updatedAssignment) {
        // If content is the same there is no reason to update (as it fills up the binlog of mariadb etc...)
        if (!existingAssignment.contentEquals(updatedAssignment)) {
            final LocalDate originalAssignedFrom = existingAssignment.getAssignedFrom();
            TemporalFieldUpdater.updateFields(existingAssignment, updatedAssignment);
            // BEGIN Workaround, if there are multiple direct assignments that are exactly the same only the assignedDate will differ, in that case use the oldest
            if (originalAssignedFrom != null && updatedAssignment.getAssignedFrom() != null) {
                if (originalAssignedFrom.isBefore(updatedAssignment.getAssignedFrom())) {
                    existingAssignment.setAssignedFrom(originalAssignedFrom);
                }
            }
            // END Workaround
            try {
                if (temporalDao.updateAttestationUserRoleAssignment(existingAssignment) == 0) {
                    log.error("Failed to update AttestationUserRoleAssignment with id={}", existingAssignment.getId());
                }
            } catch (Exception e) {
                log.error("Failed to update AttestationUserRoleAssignment with id={}", existingAssignment.getId(), e);
            }
        }
        return existingAssignment.getId();
    }

    private void persist(final LocalDate when, final AttestationUserRoleAssignment assignment) {
        assignment.setValidFrom(when);
        assignment.setUpdatedAt(when);
        temporalDao.saveAttestationUserRoleAssignment(assignment);
    }

    public long flattenOURoles(TransactionTemplate transactionTemplate, final LocalDate when) {
        final List<HistoryOU> allOus = temporalDao.listHistoryOUs(when);
        long recordCount = 0;
        final List<HistoryOURoleAssignment> allOuAssignments = transactionTemplate
                .execute(t -> temporalDao.listHistoryOURoleAssignmentsByDate(when));
        recordCount += flattenAndPersistPartitioned(transactionTemplate, allOuAssignments,
                a -> toUserRoleAssignment(getCurrentOu(allOus, a.getOuUuid()), a, when), when);

        final List<HistoryOURoleAssignmentWithExceptions> allOuAssignmentsWithExceptions = transactionTemplate
                .execute(t -> temporalDao.listHistoryOURoleAssignmentWithExceptionsByDate(when));
        recordCount += flattenAndPersistPartitioned(transactionTemplate, allOuAssignmentsWithExceptions,
                a -> toUserRoleAssignment(getCurrentOu(allOus, a.getOuUuid()), a, when), when);

        final List<HistoryOURoleAssignmentWithTitles> allOuAssignmentsWithTitles = transactionTemplate
                .execute(t -> temporalDao.listHistoryOURoleAssignmentWithTitlesByDate(when));
        recordCount += flattenAndPersistPartitioned(transactionTemplate, allOuAssignmentsWithTitles,
                a -> toUserRoleAssignment(getCurrentOu(allOus, a.getOuUuid()), a, when), when);

        final List<HistoryOURoleAssignmentWithNegativeTitles> allOuAssignmentsWithNegativeTitles = transactionTemplate
                .execute(t -> temporalDao.listHistoryOURoleAssignmentWithNegativeTitlesByDate(when));
        recordCount += flattenAndPersistPartitioned(transactionTemplate, allOuAssignmentsWithNegativeTitles,
                a -> toUserRoleAssignment(getCurrentOu(allOus, a.getOuUuid()), a, when), when);

        return recordCount;
    }

    /**
     * Takes a list of assignments and partitions it into smaller chunks and creates a transaction for each chunk.
     * Inside the transactions the assignments are converted to a {@link AttestationUserRoleAssignment} and persisted.
     * @return total number of records saved.
     */
    private <T> long flattenAndPersistPartitioned(final TransactionTemplate transactionTemplate, final List<T> allOuAssignments,
                                                  final Function<T, List<AttestationUserRoleAssignment>> converter, final LocalDate when) {
        final List<List<T>> ouAssignmentsPartitions = Lists.partition(allOuAssignments, 10000);
        long recordCount = 0;
        final List<Long> updatedIds = new ArrayList<>();
        for (List<T> ouRoleAssignmentsPartition : ouAssignmentsPartitions) {
            final List<AttestationUserRoleAssignment> attestationUserRoleAssignments = ouRoleAssignmentsPartition.stream()
                    .flatMap(a -> converter.apply(a).stream())
                    .toList();
            final List<List<AttestationUserRoleAssignment>> partitioned = Lists.partition(attestationUserRoleAssignments, 10000);
            for (List<AttestationUserRoleAssignment> userRoleAssignments : partitioned) {
                transactionTemplate.execute(t -> {
                            userRoleAssignments.forEach(a -> {
                                logProgress();
                                updateOrCreate(when, a, updatedIds);
                            });
                            return null;
                        });
                recordCount += userRoleAssignments.size();
            }
        }
        // Now update the updated timestamp for all touched entities
        Lists.partition(updatedIds, 500)
                .forEach(updatedId -> temporalDao.setUpdatedTimestampForUserRoleAssignmentsWithIdsIn(updatedId, when));
        return recordCount;
    }

    private String getOuName(final LocalDate when, final String ouUuid) {
        return temporalDao.findActiveOUByUuid(ouUuid)
                .map(OrgUnit::getName)
                .orElseGet(
                        () -> temporalDao.findHistoricOUByUuid(when, ouUuid)
                                .map(HistoryOU::getOuName)
                                .orElse(null)
                );
    }

    private String getResponsibleOuUuid(final String ouUuid, final String currentUserUuid,
                                         boolean isManager, boolean itSystemResponsible) {
        if (itSystemResponsible) {
            return null;
        }
        if (isManager) {
            return cachedOuService.findParentOuWithDifferentManager(currentUserUuid, ouUuid);
        } else {
            return ouUuid;
        }
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

    private AttestationUserRoleAssignment toUserRoleAssignment(final HistoryItSystem itSystem, final HistoryRoleAssignment historyRoleAssignment, final LocalDate when) {
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
        final String responsibleOu = getResponsibleOuUuid(historyRoleAssignment.getOrgUnitUuid(), historyRoleAssignment.getUserUuid(),
                context.isManager(), itSystemResponsible);
		final LocalDate assignedFrom = historyRoleAssignment.getAssignedWhen() != null
			? historyRoleAssignment.getAssignedWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
			: null;
        AttestationUserRoleAssignment assignment = AttestationUserRoleAssignment.builder()
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
                .responsibleOuName(getOuName(when, responsibleOu))
                .responsibleOuUuid(responsibleOu)
                .manager(!itSystemResponsible && context.isManager())
                .assignedThroughType(toAssignedThrough(historyRoleAssignment))
                .assignedThroughName(historyRoleAssignment.getAssignedThroughName())
                .assignedThroughUuid(historyRoleAssignment.getAssignedThroughUuid())
                .inherited(false)
                .sensitiveRole(context.isRoleSensitive())
                .extraSensitiveRole(context.isRoleExtraSensitive())
                .roleOuUuid(context.ouUuid())
                .roleOuName(context.ouName())
                .postponedConstraints(historyRoleAssignment.getPostponedConstraints())
                .assignedFrom(assignedFrom)
                .build();
        // Hash are calculated afterward, as the hash calculation needs all the fields to be filled in first.
        assignment.setRecordHash(TemporalHasher.hashEntity(assignment));
        return assignment;
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

        final String systemResponsibleUserUuid = context.responsibleUserUuid();
        final boolean itSystemResponsible = assignment.getRoleRoleGroupId() == null
                && context.isRoleAssignmentAttestationByAttestationResponsible()
                && systemResponsibleUserUuid != null;
        final List<HistoryOUUser> users = temporalDao.listHistoryOUUsers(currentOu.getId());
        return users.stream()
                .filter(u -> !u.getDoNotInherit())
                .map(u -> {
                    final User currentUser = userDao.findById(u.getUserUuid())
                            .orElseThrow();
                    final boolean isManager = context.isManager(currentUser);
                    final String responsibleOuUuid = getResponsibleOuUuid(context.ouUuid(), u.getUserUuid(), isManager, itSystemResponsible);
                    boolean inherited = assignment.getAssignedThroughType() == AssignedThrough.ORGUNIT;
                    final AttestationUserRoleAssignment userRoleAssignment = AttestationUserRoleAssignment.builder()
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
                            .responsibleUserUuid(itSystemResponsible && !inherited ? systemResponsibleUserUuid : null)
                            .responsibleOuName(getOuName(when, responsibleOuUuid))
                            .responsibleOuUuid(responsibleOuUuid)
                            .manager(!itSystemResponsible && isManager)
                            .inherited(inherited)
                            .sensitiveRole(context.isRoleSensitive())
                            .extraSensitiveRole(context.isRoleExtraSensitive())
                            .roleOuUuid(context.ouUuid())
                            .roleOuName(context.ouName())
                            .assignedFrom(assignment.getAssignedWhen().toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate())
                            .build();
                    // Hash are calculated afterward, as the hash calculation needs all the fields to be filled in first.
                    userRoleAssignment.setRecordHash(TemporalHasher.hashEntity(userRoleAssignment));
                    return userRoleAssignment;
                })
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

        final String systemResponsibleUserUuid = context.responsibleUserUuid();
        boolean itSystemResponsible = assignment.getRoleRoleGroupId() == null
                && context.isRoleAssignmentAttestationByAttestationResponsible()
                && systemResponsibleUserUuid != null;

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
                    final String responsibleOuUuid = getResponsibleOuUuid(context.ouUuid(), u.getUserUuid(), isManager, itSystemResponsible);
                    final AttestationUserRoleAssignment userRoleAssignment = AttestationUserRoleAssignment.builder()
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
                            .responsibleUserUuid(itSystemResponsible ? systemResponsibleUserUuid : null)
                            .responsibleOuName(getOuName(when, responsibleOuUuid))
                            .responsibleOuUuid(responsibleOuUuid)
                            .manager(!itSystemResponsible && isManager)
                            .assignedThroughName(currentOu.getOuName())
                            .assignedThroughUuid(currentOu.getOuUuid())
                            .assignedThroughType(AssignedThroughType.ORGUNIT)
                            .inherited(false)
                            .sensitiveRole(context.isRoleSensitive())
                            .extraSensitiveRole(context.isRoleExtraSensitive())
                            .roleOuUuid(context.ouUuid())
                            .roleOuName(context.ouName())
                            .assignedFrom(assignment.getAssignedWhen().toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate())
                            .build();
                    // Hash are calculated afterward, as the hash calculation needs all the fields to be filled in first.
                    userRoleAssignment.setRecordHash(TemporalHasher.hashEntity(userRoleAssignment));
                    return userRoleAssignment;
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

        final String systemResponsibleUserUuid = context.responsibleUserUuid();
        boolean itSystemResponsible = assignment.getRoleRoleGroupId() == null
                && context.isRoleAssignmentAttestationByAttestationResponsible()
                && systemResponsibleUserUuid != null;

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
                    final String responsibleOuUuid = getResponsibleOuUuid(context.ouUuid(), u.getUserUuid(), isManager, itSystemResponsible);
                    final AttestationUserRoleAssignment userRoleAssignment = AttestationUserRoleAssignment.builder()
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
                            .responsibleUserUuid(itSystemResponsible ? systemResponsibleUserUuid : null)
                            .responsibleOuName(getOuName(when, responsibleOuUuid))
                            .responsibleOuUuid(responsibleOuUuid)
                            .manager(!itSystemResponsible && isManager)
                            .assignedThroughName(currentOu.getOuName())
                            .assignedThroughUuid(currentOu.getOuUuid())
                            .assignedThroughType(AssignedThroughType.ORGUNIT)
                            .inherited(false)
                            .sensitiveRole(context.isRoleSensitive())
                            .extraSensitiveRole(context.isRoleExtraSensitive())
                            .roleOuUuid(context.ouUuid())
                            .roleOuName(context.ouName())
                            .assignedFrom(assignment.getAssignedWhen().toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate())
                            .build();
                    // Hash are calculated afterward, as the hash calculation needs all the fields to be filled in first.
                    userRoleAssignment.setRecordHash(TemporalHasher.hashEntity(userRoleAssignment));
                    return userRoleAssignment;
                })
                .collect(Collectors.toList());
    }

    private List<AttestationUserRoleAssignment> toUserRoleAssignment(final HistoryOU currentOu,
                                                                     final HistoryOURoleAssignmentWithNegativeTitles assignment, final LocalDate when) {
        final UpdaterContextService.UpdaterContext context = updaterContextService.contextBuilder(when, assignment.getRoleItSystemId())
                .withOrgUnit(assignment.getOuUuid())
                .withRole(assignment.getRoleId())
                .withRoleGroup(assignment.getRoleRoleGroupId())
                .getContext();
        if (context.isItSystemExempt()) {
            return Collections.emptyList();
        }

        final String systemResponsibleUserUuid = context.responsibleUserUuid();
        boolean itSystemResponsible = assignment.getRoleRoleGroupId() == null
                && context.isRoleAssignmentAttestationByAttestationResponsible()
                && systemResponsibleUserUuid != null;

        final List<HistoryOUUser> users = temporalDao.listHistoryOUUsers(currentOu.getId());
        return users.stream()
                .filter(
                        u -> assignment.getTitleUuids().isEmpty() || !assignment.getTitleUuids().contains(u.getTitleUuid())
                )
                .map(u -> {
                    final User currentUser = userDao.findById(u.getUserUuid())
                            .orElseThrow();
                    final boolean isManager = context.isManager(currentUser);
                    final String responsibleOuUuid = getResponsibleOuUuid(context.ouUuid(), u.getUserUuid(), isManager, itSystemResponsible);
                    final AttestationUserRoleAssignment userRoleAssignment = AttestationUserRoleAssignment.builder()
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
                            .responsibleUserUuid(itSystemResponsible ? systemResponsibleUserUuid : null)
                            .responsibleOuName(getOuName(when, responsibleOuUuid))
                            .responsibleOuUuid(responsibleOuUuid)
                            .manager(!itSystemResponsible && isManager)
                            .assignedThroughName(currentOu.getOuName())
                            .assignedThroughUuid(currentOu.getOuUuid())
                            .assignedThroughType(AssignedThroughType.ORGUNIT)
                            .inherited(false)
                            .sensitiveRole(context.isRoleSensitive())
                            .extraSensitiveRole(context.isRoleExtraSensitive())
                            .roleOuUuid(context.ouUuid())
                            .roleOuName(context.ouName())
                            .assignedFrom(assignment.getAssignedWhen().toInstant()
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate())
                            .build();
                    // Hash are calculated afterward, as the hash calculation needs all the fields to be filled in first.
                    userRoleAssignment.setRecordHash(TemporalHasher.hashEntity(userRoleAssignment));
                    return userRoleAssignment;
                })
                .toList();
    }


    private static int progressCount = 0;
    private void logProgress() {
        if (++progressCount % 100 == 0) {
            log.info("Processing user assignment, count={}", progressCount);
        }
    }

}
