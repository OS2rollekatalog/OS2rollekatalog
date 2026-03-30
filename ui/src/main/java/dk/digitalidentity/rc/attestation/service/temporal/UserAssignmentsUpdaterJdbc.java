package dk.digitalidentity.rc.attestation.service.temporal;

import static org.springframework.transaction.TransactionDefinition.ISOLATION_READ_UNCOMMITTED;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.common.collect.Lists;

import dk.digitalidentity.rc.attestation.exception.AttestationDataUpdaterException;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import lombok.extern.slf4j.Slf4j;

/**
 * JDBC Notice!
 * These classes are made to save memory, hibernate will consume around 1gb whereas these use much less.
 * Warning though: the hibernate section classes are used when fetching from the DB, they are obviously not attached to
 * the hibernate session also all relations are not populated, so be aware!
 */
@Component
@Slf4j
public class UserAssignmentsUpdaterJdbc {

    @Autowired
    private PlatformTransactionManager transactionManager;
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

		long recordCount = 0;

		final List<Long> itSystemIds = temporalDao.getDistinctAssignedItSystems(when);
		for (final Long itSystemId : itSystemIds) {
			log.info("Processing it-system id: " + itSystemId);
			final Long cnt = transactionTemplate.execute(_ -> {
				final List<Long> updatedIds = new ArrayList<>();
				List<AttestationUserRoleAssignment> assignments = temporalDao
					.findHistoricAssignmentsByItSystemAndDate(when, itSystemId).stream()
					.map(r -> toUserRoleAssignment(r, when))
					.toList();

				assignments.forEach(a -> {
					logProgress();
					updateOrCreate(when, a, updatedIds);
				});

				long processedCount = assignments.size();
				Lists.partition(updatedIds, 500)
					.forEach(updatedId -> temporalDao.setUpdatedTimestampForUserRoleAssignmentsWithIdsIn(updatedId, when));
				return processedCount;
			});
			recordCount += cnt != null ? cnt : 0;
		}

        if (recordCount == 0) {
            throw new AttestationDataUpdaterException("No system assignments for date %s in the history tables.", when);
        }
        final List<Long> idsToDisable = transactionTemplate.execute(_ -> temporalDao.findAllValidUserRoleAssignmentIdsByUpdatedAtLessThan(when));
        for (List<Long> currentIdsToDisable : Lists.partition(idsToDisable, 500)) {
            transactionTemplate.execute(_ -> temporalDao.invalidateUserRoleAssignmentsWithIdsIn(currentIdsToDisable, when));
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
     * @return id of the updated section
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

    private static AssignedThroughType toAssignedThrough(final AssignedThrough assignedThrough) {
        if (assignedThrough == null) {
            return null;
        }
        return switch (assignedThrough) {
            // ROLEGROUP and DIRECT are both directly assigned.
            case DIRECT, ROLEGROUP -> AssignedThroughType.DIRECT; // TODO - Is this correct? Is a userrole assigned through a group "direct"?
            case POSITION -> AssignedThroughType.POSITION;
            case ORGUNIT -> AssignedThroughType.ORGUNIT;
            case TITLE -> AssignedThroughType.TITLE;
        };
    }

    private static AttestationUserRoleAssignment toUserRoleAssignment(final HistoricAssignment r, final LocalDate when) {
        final LocalDate assignedFrom = r.getValidFrom() != null ? r.getValidFrom().toLocalDate() : null;

		AssignedThroughPair assignedThroughPair = resolveAssignedThroughValues(r);

        final String postponedConstraints = formatConstraints(r);

        AttestationUserRoleAssignment assignment = AttestationUserRoleAssignment.builder()
                .itSystemId(r.getItSystemId())
                .itSystemName(r.getItSystemName())
                .userUuid(r.getUserUuid())
                .userId(r.getUserId())
                .userName(r.getUserName())
                .userRoleId(r.getUserRoleId())
                .userRoleName(r.getUserRoleName())
                .userRoleDescription(r.getUserRoleDescription())
                .roleGroupId(r.getRoleGroupId())
                .roleGroupName(r.getRoleGroupName())
                .roleGroupDescription(r.getRoleGroupDescription())
                .responsibleUserUuid(r.getResponsibleUserUuid())
                .responsibleOuName(r.getResponsibleOUName())
                .responsibleOuUuid(r.getResponsibleOUUuid())
                .assignedThroughType(toAssignedThrough(r.getAssignedThroughType()))
                .assignedThroughName(assignedThroughPair.assignedThroughName)
                .assignedThroughUuid(assignedThroughPair.assignedThroughUuid)
                .inherited(false)
                .sensitiveRole(r.getSensitiveRole())
                .extraSensitiveRole(r.getExtraSensitiveRole())
                .roleOuUuid(r.getAssignedThroughOUUuid())
                .roleOuName(r.getAssignedThroughOUName())
                .postponedConstraints(postponedConstraints)
                .assignedFrom(assignedFrom)
                .build();
        assignment.setRecordHash(TemporalHasher.hashEntity(assignment));
        return assignment;
    }

	private record AssignedThroughPair(String assignedThroughUuid, String assignedThroughName) { }
	private static AssignedThroughPair resolveAssignedThroughValues(final HistoricAssignment r) {
		if (r.getAssignedThroughType() == null) {
			return new AssignedThroughPair(null, null);
		}
		String assignedThroughUuid;
		String assignedThroughName;
		switch (r.getAssignedThroughType()) {
			case DIRECT -> {
				assignedThroughUuid = null;
				assignedThroughName = null;
			}
			case TITLE -> {
				assignedThroughUuid = r.getAssignedThroughTitleUuid();
				assignedThroughName = r.getAssignedThroughTitleName();
			}
			case ROLEGROUP -> {
				assignedThroughUuid = r.getAssignedThroughRoleGroupId() != null ? String.valueOf(r.getAssignedThroughRoleGroupId()) : null;
				assignedThroughName = r.getAssignedThroughRoleGroupName();
			}
			default -> {
				assignedThroughUuid = r.getAssignedThroughOUUuid();
				assignedThroughName = r.getAssignedThroughOUName();
			}
		}
		return new AssignedThroughPair(assignedThroughUuid, assignedThroughName);
	}

    private static String formatConstraints(final HistoricAssignment r) {
        if (r.getConstraints() == null || r.getConstraints().isEmpty()) {
            return null;
        }
        return r.getConstraints().stream()
                .map(c -> c.getConstraintTypeName() + ": " + String.join(",", c.getValue()))
                .collect(Collectors.joining("\n"));
    }

    private static int progressCount = 0;
    private void logProgress() {
        if (++progressCount % 100 == 0) {
            log.info("Processing user assignment, count={}", progressCount);
        }
    }

}
