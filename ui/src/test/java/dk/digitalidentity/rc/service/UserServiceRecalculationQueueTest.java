package dk.digitalidentity.rc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import dk.digitalidentity.rc.event.UpdateUserAssignmentsMessage;
import dk.digitalidentity.rc.security.RolePostProcessor;
import dk.digitalidentity.saml.service.model.TokenUser;
import dk.digitalidentity.simple_queue.BulkQueueMessage;
import dk.digitalidentity.simple_queue.json.JsonSimpleMessage;

@ExtendWith(MockitoExtension.class)
class UserServiceRecalculationQueueTest {

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private UserService userService;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("Bulk enqueue carries the logged-in person on every message, also those built on other threads")
	void bulkEnqueueCarriesActorOnEveryMessage() {
		Authentication authentication = mock(Authentication.class);
		doReturn(TokenUser.builder()
			.attributes(Map.of(RolePostProcessor.ATTRIBUTE_USERID, "admin", RolePostProcessor.ATTRIBUTE_NAME, "Admin Person"))
			.build()).when(authentication).getDetails();
		SecurityContext securityContext = mock(SecurityContext.class);
		when(securityContext.getAuthentication()).thenReturn(authentication);
		SecurityContextHolder.setContext(securityContext);

		// enough users that the parallel stream fans out to pool threads without the SecurityContext
		Set<String> userUuids = IntStream.range(0, 1000).mapToObj(i -> "uuid-" + i).collect(Collectors.toSet());

		userService.queueMultipleForRecalculation(userUuids);

		ArgumentCaptor<BulkQueueMessage> published = ArgumentCaptor.forClass(BulkQueueMessage.class);
		verify(eventPublisher).publishEvent(published.capture());
		assertThat(published.getValue().getMessages())
			.extracting(m -> JsonSimpleMessage.fromJson(m.getBody(), UpdateUserAssignmentsMessage.class).getChangedBy())
			.hasSize(1000)
			.containsOnly("Admin Person (admin)");
	}
}
