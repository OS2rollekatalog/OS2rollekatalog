package dk.digitalidentity.rc.event;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentCalculator;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentChangeResult;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentService;
import dk.digitalidentity.rc.service.assignment.CurrentExceptedAssignmentService;
import dk.digitalidentity.simple_queue.BulkQueueMessage;
import jakarta.persistence.EntityManager;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssignmentChangeEventHandlerServiceTest {

	@Mock
	private UserService userService;
	@Mock
	private CurrentAssignmentCalculator currentAssignmentCalculator;
	@Mock
	private CurrentAssignmentService currentAssignmentService;
	@Mock
	private CurrentExceptedAssignmentService currentExceptedAssignmentService;
	@Mock
	private ApplicationEventPublisher eventPublisher;
	@Mock
	private EntityManager entityManager;

	@InjectMocks
	private AssignmentChangeEventHandlerService service;

	private User user;

	@BeforeEach
	void setup() {
		// @PersistenceContext-feltet injiceres ikke af @InjectMocks (constructor-injection vælges)
		ReflectionTestUtils.setField(service, "entityManager", entityManager);

		user = new User();
		user.setUuid("user-1");

		given(userService.getAllByUuidIn(Set.of("user-1"))).willReturn(List.of(user));
		given(currentAssignmentCalculator.calculateAllAssignmentsForUser(user))
			.willReturn(ImmutablePair.of(Set.<CurrentAssignment>of(), Set.<CurrentExceptedAssignment>of()));
	}

	@Test
	@DisplayName("publishes RoleMembershipChanged when assignments delta has affected userRoles")
	void publishesWhenAffectedRolesPresent() {
		given(currentAssignmentService.saveAllForUsers(anyMap()))
			.willReturn(new CurrentAssignmentChangeResult(Set.of(user), Set.of(5L, 6L)));

		service.updateUsers(Set.of("user-1"));

		verify(eventPublisher).publishEvent(any(BulkQueueMessage.class));
	}

	@Test
	@DisplayName("does not publish when there is no membership delta")
	void doesNotPublishWhenNoDelta() {
		given(currentAssignmentService.saveAllForUsers(anyMap()))
			.willReturn(new CurrentAssignmentChangeResult(Set.of(), Set.of()));

		service.updateUsers(Set.of("user-1"));

		verify(eventPublisher, never()).publishEvent(any(BulkQueueMessage.class));
	}
}
