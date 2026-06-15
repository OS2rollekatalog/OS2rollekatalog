package dk.digitalidentity.rc.controller.rest;

import static dk.digitalidentity.rc.event.AssignmentChangeEventHandler.ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.simple_queue.service.QueueService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class QueueRestController {

	public static final String SINCE_HEADER = "X-Queue-Drain-Since";

	private static final Set<String> ALLOWED_QUEUES = Set.of(ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER);

	private final QueueService queueService;

	@GetMapping("/rest/queue/{queueName}/drained")
	public ResponseEntity<String> drained(
			@NotNull @PathVariable("queueName") String queueName,
			@RequestParam(value = "since", required = false) String sinceParam,
			@RequestParam(value = "messageId", required = false) String messageId) {
		if (!ALLOWED_QUEUES.contains(queueName)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		if (messageId != null && !messageId.isEmpty()) {
			// Per-message mode: only wait for items with this messageId.
			// No cut-off needed — isMessageActive isn't time-based.
			if (queueService.isMessageActive(queueName, messageId)) {
				// 202 Accepted (not 409) so browsers don't log polling as errors.
				return ResponseEntity.accepted()
						.header("Retry-After", "1")
						.build();
			}
			return ResponseEntity.noContent().build();
		}
		Instant since;
		if (sinceParam == null || sinceParam.isEmpty()) {
			// First poll: capture cut-off on the server so client clock drift
			// cannot cause a premature "drained" signal. Add 1s buffer so items
			// enqueued immediately before this call are included even when
			// createdAt is truncated to second precision by the database.
			since = Instant.now().plusSeconds(1);
		} else {
			try {
				since = Instant.parse(sinceParam);
			} catch (DateTimeParseException e) {
				try {
					since = LocalDateTime.parse(sinceParam)
						.atZone(ZoneId.of("Europe/Copenhagen"))
						.toInstant();
				} catch (DateTimeParseException e2) {
					return ResponseEntity.badRequest().build();
				}
			}
		}
		if (queueService.hasActiveItemsOlderThan(queueName, since)) {
			return ResponseEntity.accepted()
					.header("Retry-After", "1")
					.header(SINCE_HEADER, since.toString())
					.build();
		}
		return ResponseEntity.noContent()
				.header(SINCE_HEADER, since.toString())
				.build();
	}
}
