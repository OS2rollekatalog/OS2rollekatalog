package dk.digitalidentity.rc.event;

import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.PendingADUpdateService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.cics.KspCicsService;
import dk.digitalidentity.simple_queue.QueueMessage;
import dk.digitalidentity.simple_queue.json.JsonSimpleMessage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static dk.digitalidentity.rc.event.RoleMembershipChangedEventHandler.ROLE_MEMBERSHIP_CHANGED_QUEUE_IDENTIFIER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RoleMembershipChangedEventHandlerTest {

	@Mock
	private UserRoleService userRoleService;
	@Mock
	private PendingADUpdateService pendingADUpdateService;
	@Mock
	private KspCicsService kspCicsService;
	@Mock
	private EntityManager entityManager;

	@InjectMocks
	private RoleMembershipChangedEventHandler handler;

	@BeforeEach
	void setup() {
		// @PersistenceContext-feltet injiceres ikke af @InjectMocks (constructor-injection vælges)
		ReflectionTestUtils.setField(handler, "entityManager", entityManager);
	}

	private QueueMessage messageFor(Long userRoleId) {
		return QueueMessage.builder()
			.queue(ROLE_MEMBERSHIP_CHANGED_QUEUE_IDENTIFIER)
			.body(JsonSimpleMessage.toJson(RoleMembershipChangedMessage.builder()
				.userRoleId(userRoleId)
				.build()))
			.build();
	}

	@Test
	@DisplayName("marks the affected userRole dirty in both AD and KSP-CICS")
	void marksDirtyInBothSystems() {
		UserRole userRole = new UserRole();
		userRole.setId(42L);
		given(userRoleService.getById(42L)).willReturn(userRole);

		boolean result = handler.handleMessage(messageFor(42L));

		assertThat(result).isTrue();
		verify(pendingADUpdateService).addUserRoleToQueue(userRole);
		verify(kspCicsService).addUserRoleToQueue(userRole);
	}

	@Test
	@DisplayName("skips gracefully when the userRole no longer exists")
	void skipsWhenRoleDeleted() {
		given(userRoleService.getById(99L)).willReturn(null);

		boolean result = handler.handleMessage(messageFor(99L));

		assertThat(result).isTrue();
		verifyNoInteractions(pendingADUpdateService, kspCicsService);
	}

	@Test
	@DisplayName("skips when the message has no userRoleId")
	void skipsWhenUserRoleIdMissing() {
		boolean result = handler.handleMessage(messageFor(null));

		assertThat(result).isTrue();
		verifyNoInteractions(userRoleService, pendingADUpdateService, kspCicsService);
	}

	@Test
	@DisplayName("batch path loads roles in one query and marks each dirty in both systems")
	void batchMarksEachRole() {
		UserRole role1 = new UserRole();
		role1.setId(1L);
		UserRole role2 = new UserRole();
		role2.setId(2L);
		given(userRoleService.findAllByIdIn(anyCollection())).willReturn(Set.of(role1, role2));

		boolean result = handler.handleMessages(List.of(messageFor(1L), messageFor(2L)));

		assertThat(result).isTrue();
		verify(userRoleService).findAllByIdIn(Set.of(1L, 2L));
		verify(pendingADUpdateService).addUserRoleToQueue(role1);
		verify(pendingADUpdateService).addUserRoleToQueue(role2);
		verify(kspCicsService).addUserRoleToQueue(role1);
		verify(kspCicsService).addUserRoleToQueue(role2);
	}
}
