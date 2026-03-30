package dk.digitalidentity.rc.event;

import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.stereotype.Component;

import dk.digitalidentity.simple_queue.QueueMessage;
import dk.digitalidentity.simple_queue.SimpleMessageHandler;
import dk.digitalidentity.simple_queue.json.JsonSimpleMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class AssignmentChangeEventHandler implements SimpleMessageHandler {

	private final AssignmentChangeEventHandlerService assignmentChangeEventHandlerService;

	@PersistenceContext
	private EntityManager entityManager;

	public static final String ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER = "assignment_update_queue";

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
		assignmentChangeEventHandlerService.updateUser(message.getUserUuid());
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

		assignmentChangeEventHandlerService.updateUsers(userUuids);
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
