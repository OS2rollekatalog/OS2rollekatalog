package dk.digitalidentity.rc.event;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.simple_queue.QueueMessage;
import dk.digitalidentity.simple_queue.SimpleMessageHandler;
import dk.digitalidentity.simple_queue.json.JsonSimpleMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Component
public class UserAssignmentsChangedEventHandler implements SimpleMessageHandler {
	public static final String USER_ASSIGNMENTS_CHANGED_QUEUE_IDENTIFIER = "user_assignments_changed_queue";

	private final RoleCatalogueConfiguration config;
	private final UserService userService;
	private final List<UserAssignmentsChangedListener> listeners;

	@Override
	public Set<String> activeQueues() {
		return config.getScheduled().isEnabled()
			? Set.of(USER_ASSIGNMENTS_CHANGED_QUEUE_IDENTIFIER)
			: Set.of();
	}

	@Override
	public boolean handles(QueueMessage message) {
		return config.getScheduled().isEnabled() && USER_ASSIGNMENTS_CHANGED_QUEUE_IDENTIFIER.equals(message.getQueue());
	}

	@Override
	public boolean handleMessage(QueueMessage queueMessage) {
		final UpdateUserAssignmentsMessage message = JsonSimpleMessage.fromJson(queueMessage.getBody(), UpdateUserAssignmentsMessage.class);
		handleChangedUser(message.getUserUuid());
		return true;
	}

	@Override
	public boolean handleMessages(final List<QueueMessage> queueMessages) {
		for (final QueueMessage queueMessage : queueMessages) {
			final UpdateUserAssignmentsMessage message = JsonSimpleMessage.fromJson(queueMessage.getBody(), UpdateUserAssignmentsMessage.class);
			handleChangedUser(message.getUserUuid());
		}
		return true;
	}

	@Override
	public boolean handleFailedMessage(QueueMessage message, Exception exception) {
		return exception instanceof NoSuchElementException;
	}

	private void handleChangedUser(final String userUuid) {
		if (!StringUtils.hasText(userUuid)) {
			log.warn("Received user assignments changed message with missing userUuid, skipping");
			return;
		}
		userService.getOptionalByUuid(userUuid).ifPresent(user -> {
			for (final UserAssignmentsChangedListener listener : listeners) {
				listener.onUserAssignmentsChanged(user);
			}
		});
	}
}
