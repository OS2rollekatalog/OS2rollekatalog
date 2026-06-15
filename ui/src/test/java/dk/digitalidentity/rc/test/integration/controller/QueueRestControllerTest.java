package dk.digitalidentity.rc.test.integration.controller;

import static dk.digitalidentity.rc.event.AssignmentChangeEventHandler.ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MvcResult;

import dk.digitalidentity.rc.controller.rest.QueueRestController;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.test.integration.setup.BaseIntegrationTest;
import dk.digitalidentity.rc.test.integration.setup.BasicTestDataFactory;
import dk.digitalidentity.simple_queue.QueueMessage;
import dk.digitalidentity.simple_queue.dao.QueueItemDao;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class QueueRestControllerTest extends BaseIntegrationTest {

	private static final String LOGGED_IN_USER_UUID = "logged-in-user-uuid";
	private static final String LOGGED_IN_USER_ID = "logged-in-user-id";

	private final BasicTestDataFactory testDataFactory;
	private final ApplicationEventPublisher eventPublisher;
	private final QueueItemDao queueItemDao;

	private User loggedInUser;

	@BeforeEach
	void setUp() {
		final BasicTestDataFactory.BasicTestData testData = testDataFactory.createBasicTestData();
		loggedInUser = testDataFactory.createUser(LOGGED_IN_USER_UUID, LOGGED_IN_USER_ID, "test user name", testData.itSystem().getDomain());
		grantAssigningAccess(LOGGED_IN_USER_UUID, Section.USER);
		queueItemDao.deleteAll();
		flushAndClear();
	}

	@Test
	@DisplayName("empty queue returns 204 with since header")
	void emptyQueueReturnsNoContent() throws Exception {
		mockMvc.perform(get("/rest/queue/{q}/drained", ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER)
						.with(mockLogin(loggedInUser, List.of())))
				.andExpect(status().isNoContent())
				.andExpect(header().exists(QueueRestController.SINCE_HEADER));
	}

	@Test
	@DisplayName("queue with active items returns 202 until drained")
	void activeItemsReturnAccepted() throws Exception {
		// Enqueue an item, then give it a moment so its createdAt is before the
		// server's now() captured by the endpoint.
		eventPublisher.publishEvent(QueueMessage.builder()
				.queue(ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER)
				.messageId("test-message-1")
				.priority(1L)
				.dequeueTime(Instant.now().plusSeconds(60 * 60))
				.body("{}")
				.build());
		flushAndClear();
		Thread.sleep(50);

		// First poll: 202, capture cut-off
		final MvcResult initial = mockMvc.perform(get("/rest/queue/{q}/drained", ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER)
						.with(mockLogin(loggedInUser, List.of())))
				.andExpect(status().isAccepted())
				.andExpect(header().string("Retry-After", "1"))
				.andExpect(header().exists(QueueRestController.SINCE_HEADER))
				.andReturn();
		final String since = initial.getResponse().getHeader(QueueRestController.SINCE_HEADER);

		// Subsequent poll with captured cut-off: still 202
		mockMvc.perform(get("/rest/queue/{q}/drained", ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER)
						.param("since", since)
						.with(mockLogin(loggedInUser, List.of())))
				.andExpect(status().isAccepted());

		// Drain the queue — subsequent poll returns 204
		queueItemDao.deleteAll();
		flushAndClear();

		mockMvc.perform(get("/rest/queue/{q}/drained", ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER)
						.param("since", since)
						.with(mockLogin(loggedInUser, List.of())))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("items enqueued after cut-off are ignored")
	void itemsNewerThanSinceAreIgnored() throws Exception {
		// Vælg en cut-off i fortiden: ethvert item enqueued nu har createdAt
		// væsentligt > since uanset DB-præcision eller wall-clock-jitter.
		// Round-trip af since-headeren testes separat i activeItemsReturnAccepted.
		final String since = LocalDateTime.now().minusMinutes(1).toString();

		eventPublisher.publishEvent(QueueMessage.builder()
				.queue(ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER)
				.messageId("late-message")
				.priority(1L)
				.dequeueTime(Instant.now().plusSeconds(60 * 60))
				.body("{}")
				.build());
		flushAndClear();

		// Item'et er nyere end since → må ikke tælles → 204.
		mockMvc.perform(get("/rest/queue/{q}/drained", ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER)
						.param("since", since)
						.with(mockLogin(loggedInUser, List.of())))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("unknown queue returns 404")
	void unknownQueueReturnsNotFound() throws Exception {
		mockMvc.perform(get("/rest/queue/{q}/drained", "some-other-queue")
						.with(mockLogin(loggedInUser, List.of())))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("messageId filter: active item for that id returns 202")
	void messageIdWithActiveItemReturnsAccepted() throws Exception {
		eventPublisher.publishEvent(QueueMessage.builder()
				.queue(ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER)
				.messageId("user-42")
				.priority(1L)
				.dequeueTime(Instant.now().plusSeconds(60 * 60))
				.body("{}")
				.build());
		flushAndClear();

		mockMvc.perform(get("/rest/queue/{q}/drained", ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER)
						.param("messageId", "user-42")
						.with(mockLogin(loggedInUser, List.of())))
				.andExpect(status().isAccepted())
				.andExpect(header().string("Retry-After", "1"))
				.andExpect(header().doesNotExist(QueueRestController.SINCE_HEADER));
	}

	@Test
	@DisplayName("messageId filter: items for other ids do not block")
	void messageIdIgnoresOtherMessages() throws Exception {
		// Enqueue for user-1 only; polling for user-2 must return 204.
		eventPublisher.publishEvent(QueueMessage.builder()
				.queue(ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER)
				.messageId("user-1")
				.priority(1L)
				.dequeueTime(Instant.now().plusSeconds(60 * 60))
				.body("{}")
				.build());
		flushAndClear();

		mockMvc.perform(get("/rest/queue/{q}/drained", ASSIGNMENT_UPDATE_QUEUE_IDENTIFIER)
						.param("messageId", "user-2")
						.with(mockLogin(loggedInUser, List.of())))
				.andExpect(status().isNoContent());
	}
}
