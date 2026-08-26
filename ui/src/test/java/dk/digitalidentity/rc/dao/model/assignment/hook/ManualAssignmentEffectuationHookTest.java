package dk.digitalidentity.rc.dao.model.assignment.hook;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createUserRole;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationOperation;
import dk.digitalidentity.rc.service.ManualAssignmentEffectuationService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManualAssignmentEffectuationHook")
class ManualAssignmentEffectuationHookTest {

	@Mock private ManualAssignmentEffectuationService manualAssignmentEffectuationService;
	@Mock private UserRoleService userRoleService;
	@Mock private UserService userService;

	private ManualAssignmentEffectuationHook hook;

	/** validTo == null means the assignment is still active, i.e. HookEvent maps it to HookAction.ADD. */
	private HookEvent hookEvent(long userRoleId, String userUuid, LocalDateTime validTo) {
		HistoricAssignment historicAssignment = HistoricAssignment.builder()
			.userUuid(userUuid)
			.userRoleId(userRoleId)
			.itSystemId(1L)
			.validFrom(LocalDateTime.now())
			.validTo(validTo)
			.build();
		return new HookEvent(historicAssignment);
	}

	/** The hook only acts on MANUAL it-systems that have manual effectuation turned on. */
	private ItSystem manualItSystem(boolean manualEffectuationEnabled) {
		ItSystem itSystem = new ItSystem();
		itSystem.setSystemType(ItSystemType.MANUAL);
		itSystem.setManualEffectuationEnabled(manualEffectuationEnabled);
		return itSystem;
	}

	@Test
	@DisplayName("registers an ASSIGN effectuation when the underlying assignment is still active")
	void registersAssignOnAdd() {
		hook = new ManualAssignmentEffectuationHook(manualAssignmentEffectuationService, userRoleService, userService);

		UserRole userRole = createUserRole("role-uuid", manualItSystem(true), List.of());
		User user = createUser("user-uuid", "userId", "Test User");

		when(userRoleService.getById(userRole.getId())).thenReturn(userRole);
		when(userService.getByUuid("user-uuid")).thenReturn(user);

		hook.handleEvent(hookEvent(userRole.getId(), "user-uuid", null));

		verify(manualAssignmentEffectuationService).registerPendingEffectuation(user, userRole, ManualAssignmentEffectuationOperation.ASSIGN);
	}

	@Test
	@DisplayName("registers a REMOVE effectuation when the underlying assignment has ended")
	void registersRemoveOnRemove() {
		hook = new ManualAssignmentEffectuationHook(manualAssignmentEffectuationService, userRoleService, userService);

		UserRole userRole = createUserRole("role-uuid", manualItSystem(true), List.of());
		User user = createUser("user-uuid", "userId", "Test User");

		when(userRoleService.getById(userRole.getId())).thenReturn(userRole);
		when(userService.getByUuid("user-uuid")).thenReturn(user);

		hook.handleEvent(hookEvent(userRole.getId(), "user-uuid", LocalDateTime.now()));

		verify(manualAssignmentEffectuationService).registerPendingEffectuation(user, userRole, ManualAssignmentEffectuationOperation.REMOVE);
	}

	@Test
	@DisplayName("is a no-op when the user-role cannot be found")
	void noOpWhenUserRoleNotFound() {
		hook = new ManualAssignmentEffectuationHook(manualAssignmentEffectuationService, userRoleService, userService);

		when(userRoleService.getById(999L)).thenReturn(null);

		hook.handleEvent(hookEvent(999L, "user-uuid", null));

		verifyNoInteractions(manualAssignmentEffectuationService);
	}

	@Test
	@DisplayName("is a no-op when the user cannot be found")
	void noOpWhenUserNotFound() {
		hook = new ManualAssignmentEffectuationHook(manualAssignmentEffectuationService, userRoleService, userService);

		UserRole userRole = createUserRole("role-uuid", manualItSystem(true), List.of());

		when(userRoleService.getById(userRole.getId())).thenReturn(userRole);
		when(userService.getByUuid("missing-user-uuid")).thenReturn(null);

		hook.handleEvent(hookEvent(userRole.getId(), "missing-user-uuid", null));

		verifyNoInteractions(manualAssignmentEffectuationService);
	}

	@Test
	@DisplayName("is a no-op when the it-system has manual effectuation turned off")
	void noOpWhenManualEffectuationDisabled() {
		hook = new ManualAssignmentEffectuationHook(manualAssignmentEffectuationService, userRoleService, userService);

		UserRole userRole = createUserRole("role-uuid", manualItSystem(false), List.of());

		when(userRoleService.getById(userRole.getId())).thenReturn(userRole);

		hook.handleEvent(hookEvent(userRole.getId(), "user-uuid", null));

		verifyNoInteractions(manualAssignmentEffectuationService, userService);
	}
}
