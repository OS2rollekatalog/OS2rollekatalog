package dk.digitalidentity.rc.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentCalculator;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentChangeResult;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentService;
import dk.digitalidentity.rc.service.assignment.CurrentExceptedAssignmentService;
import dk.digitalidentity.simple_queue.BulkQueueMessage;
import dk.digitalidentity.simple_queue.QueueMessage;
import dk.digitalidentity.simple_queue.json.JsonSimpleMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

import static dk.digitalidentity.rc.event.RoleMembershipChangedEventHandler.ROLE_MEMBERSHIP_CHANGED_QUEUE_IDENTIFIER;

@RequiredArgsConstructor
@Component
public class AssignmentChangeEventHandlerService {
	private static final long QUEUE_PRIORITY = 1L;

	private final UserService userService;
	private final CurrentAssignmentCalculator currentAssignmentCalculator;
	private final CurrentAssignmentService currentAssignmentService;
	private final CurrentExceptedAssignmentService currentExceptedAssignmentService;
	private final ApplicationEventPublisher eventPublisher;

	@PersistenceContext
	private EntityManager entityManager;

	// InvalidDataAccessApiUsageException is retried because concurrent deletes (e.g. IT-system deletion)
	// can cause Hibernate to reference entities that were removed mid-transaction, which is transient
	@Retryable(retryFor = {CannotAcquireLockException.class, InvalidDataAccessApiUsageException.class}, maxAttempts = 5, backoff = @Backoff(delay = 500, multiplier = 2))
	@Transactional
	public List<User> updateUsers(final Set<String> userUuids) {
		// Do not do endless flushing
		entityManager.setFlushMode(FlushModeType.COMMIT);

		final List<User> users = userService.getAllByUuidIn(userUuids);

		final Map<User, Set<CurrentAssignment>> assignmentsByUser = new HashMap<>();
		final Map<User, ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>>> calculatedByUser = new HashMap<>();

		for (User user : users) {
			final ImmutablePair<Set<CurrentAssignment>, Set<CurrentExceptedAssignment>> assignments = currentAssignmentCalculator.calculateAllAssignmentsForUser(user);
			assignmentsByUser.put(user, assignments.getLeft());
			calculatedByUser.put(user, assignments);
		}

		// upsert by recordHash (batch)
		final CurrentAssignmentChangeResult saveResult = currentAssignmentService.saveAllForUsers(assignmentsByUser);
		final Set<User> assignmentChangedUsers = saveResult.changedUsers();

		// Udsend RoleMembershipChanged for de roller hvis medlemskab faktisk ændrede sig, så AD/KSP-CICS
		// re-synkroniserer netop dem. Publiceres her inde i @Transactional, så kø-indsættelsen (via
		// simple-queue's @Transactional(REQUIRED)-listener) committer atomisk med CurrentAssignment.
		publishRoleMembershipChanged(saveResult.affectedUserRoleIds());

		// save exceptions to inherited ou assignments
		final Set<User> exceptionChangedUsers = new HashSet<>();
		for (User user : users) {
			boolean exceptionsChanged = currentExceptedAssignmentService.saveAllForUser(user, calculatedByUser.get(user).getRight());
			if (exceptionsChanged) {
				exceptionChangedUsers.add(user);
			}
		}

		return users.stream()
			.filter(u -> assignmentChangedUsers.contains(u) || exceptionChangedUsers.contains(u))
			.toList();
	}

	private void publishRoleMembershipChanged(final Set<Long> affectedUserRoleIds) {
		if (affectedUserRoleIds.isEmpty()) {
			return;
		}

		eventPublisher.publishEvent(BulkQueueMessage.builder()
			.messages(affectedUserRoleIds.stream()
				.map(userRoleId -> QueueMessage.builder()
					.queue(ROLE_MEMBERSHIP_CHANGED_QUEUE_IDENTIFIER)
					.messageId(Long.toString(userRoleId)) // dedup: flere ændringer til samme rolle kollapser
					.priority(QUEUE_PRIORITY)
					.dequeueTime(Instant.now())
					.body(JsonSimpleMessage.toJson(RoleMembershipChangedMessage.builder()
						.userRoleId(userRoleId)
						.timestamp(LocalDateTime.now())
						.build()))
					.build())
				.toList())
			.build());
	}

}
