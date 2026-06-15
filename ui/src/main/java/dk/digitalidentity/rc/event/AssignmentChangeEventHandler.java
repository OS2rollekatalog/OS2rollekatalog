package dk.digitalidentity.rc.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.simple_queue.BulkQueueMessage;
import dk.digitalidentity.simple_queue.QueueMessage;
import dk.digitalidentity.simple_queue.SimpleMessageHandler;
import dk.digitalidentity.simple_queue.json.JsonSimpleMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static dk.digitalidentity.rc.event.UserAssignmentsChangedEventHandler.USER_ASSIGNMENTS_CHANGED_QUEUE_IDENTIFIER;

@Slf4j
@RequiredArgsConstructor
@Component
public class AssignmentChangeEventHandler implements SimpleMessageHandler {

	private static final long QUEUE_PRIORITY = 1L;

	private final AssignmentChangeEventHandlerService assignmentChangeEventHandlerService;
	private final ApplicationEventPublisher eventPublisher;

	@PersistenceContext
	private EntityManager entityManager;

	public static final String ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER = "assignment_update_queue";

	@Override
	public Set<String> activeQueues() {
		return Set.of(ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER);
	}

	/**
	 * Checks if this message handler support the message.
	 *
	 * @param message message
	 */
	@Override
	public boolean handles(QueueMessage message) {
		return ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER.equals(message.getQueue());
	}

	/**
	 * Handle a message from the queue.
	 * If an exception is throw the message is considered failed.
	 * Return true to delete the message from the queue.
	 * Return false to keep the message in the queue with a finished status.
	 *
	 * @param queueMessage handled message
	 */
	@Override
	public boolean handleMessage(QueueMessage queueMessage) {
		// Do not do endless flushing
		entityManager.setFlushMode(FlushModeType.COMMIT);
		final UpdateUserAssignmentsMessage message = JsonSimpleMessage.fromJson(queueMessage.getBody(), UpdateUserAssignmentsMessage.class);
		if (!StringUtils.hasText(message.getUserUuid())) {
			log.warn("Received assignment update message with missing userUuid, skipping");
			return true;
		}
		assignmentChangeEventHandlerService.updateUsers(Set.of(message.getUserUuid()));
		return true;
	}

	@Override
    public boolean handleMessages(final List<QueueMessage> queueMessages) {
		// Do not do endless flushing
		entityManager.setFlushMode(FlushModeType.COMMIT);

		Set<String> userUuids = new HashSet<>();
		for (final QueueMessage queueMessage : queueMessages) {
			final UpdateUserAssignmentsMessage message = JsonSimpleMessage.fromJson(queueMessage.getBody(), UpdateUserAssignmentsMessage.class);
			userUuids.add(message.getUserUuid());
		}

		final List<User> changedUsers = assignmentChangeEventHandlerService.updateUsers(userUuids);
		if (!changedUsers.isEmpty()) {
			eventPublisher.publishEvent(BulkQueueMessage.builder()
				.messages(changedUsers.stream()
					.map(u -> QueueMessage.builder()
						.queue(USER_ASSIGNMENTS_CHANGED_QUEUE_IDENTIFIER)
						.messageId(u.getUuid())
						.priority(QUEUE_PRIORITY)
						.dequeueTime(Instant.now())
						.body(JsonSimpleMessage.toJson(UpdateUserAssignmentsMessage.builder()
							.userUuid(u.getUuid())
							.timestamp(LocalDateTime.now())
							.build()))
						.build())
					.toList())
				.build());
		}
		return true;
	}

	/**
	 * Handle a failed message.
	 * Return true to delete the message from the queue.
	 * Return false to keep the message in the queue with a failed status.
	 *
	 * @param message   failed message
	 * @param exception exception
	 */
	@Override
	public boolean handleFailedMessage(QueueMessage message, Exception exception) {
		return exception instanceof NoSuchElementException;
	}
}
