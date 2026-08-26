package dk.digitalidentity.rc.service;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createUserRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dk.digitalidentity.rc.dao.ManualAssignmentEffectuationDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManualAssignmentEffectuation;
import dk.digitalidentity.rc.dao.model.ManualWelcomeEmailTemplate;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationOperation;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManualAssignmentEffectuationService")
class ManualAssignmentEffectuationServiceTest {

	@Mock private ManualAssignmentEffectuationDao manualAssignmentEffectuationDao;
	@Mock private ManualAssignmentEffectuationNotifier manualAssignmentEffectuationNotifier;
	@Mock private EmailQueueService emailQueueService;
	@Mock private EmailTemplateRenderer emailTemplateRenderer;
	@Mock private ManualWelcomeEmailTemplateService manualWelcomeEmailTemplateService;
	@Mock private dk.digitalidentity.rc.log.AuditLogger auditLogger;

	@InjectMocks
	private ManualAssignmentEffectuationService service;

	private ItSystem manualItSystem() {
		ItSystem itSystem = createItSystem("it-system-uuid", List.of());
		itSystem.setSystemType(ItSystemType.MANUAL);
		itSystem.setManualEffectuationEnabled(true);
		return itSystem;
	}

	@Nested
	@DisplayName("registerPendingEffectuation")
	class RegisterPendingEffectuation {

		@Test
		@DisplayName("is a no-op when the it-system is not MANUAL")
		void noOpWhenNotManual() {
			ItSystem itSystem = createItSystem("it-system-uuid", List.of());
			itSystem.setSystemType(ItSystemType.AD);
			UserRole userRole = createUserRole("role-uuid", itSystem, List.of());
			User user = createUser("user-uuid", "userId", "Test User");

			service.registerPendingEffectuation(user, userRole, ManualAssignmentEffectuationOperation.ASSIGN);

			verifyNoInteractions(manualAssignmentEffectuationDao);
		}

		@Test
		@DisplayName("is a no-op when the effectuation flow is disabled for the it-system")
		void noOpWhenFlowDisabled() {
			ItSystem itSystem = createItSystem("it-system-uuid", List.of());
			itSystem.setSystemType(ItSystemType.MANUAL);
			itSystem.setManualEffectuationEnabled(false);
			UserRole userRole = createUserRole("role-uuid", itSystem, List.of());
			User user = createUser("user-uuid", "userId", "Test User");

			service.registerPendingEffectuation(user, userRole, ManualAssignmentEffectuationOperation.ASSIGN);

			verifyNoInteractions(manualAssignmentEffectuationDao);
		}

		@Test
		@DisplayName("cancels (deletes) a pending opposite-operation row instead of creating a new one")
		void cancelsOppositeOperation() {
			ItSystem itSystem = manualItSystem();
			UserRole userRole = createUserRole("role-uuid", itSystem, List.of());
			User user = createUser("user-uuid", "userId", "Test User");

			ManualAssignmentEffectuation pendingRemoval = new ManualAssignmentEffectuation();
			when(manualAssignmentEffectuationDao.findByUser_UuidAndUserRole_IdAndOperationAndStatus(
				"user-uuid", userRole.getId(), ManualAssignmentEffectuationOperation.REMOVE, ManualAssignmentEffectuationStatus.PENDING))
				.thenReturn(Optional.of(pendingRemoval));

			service.registerPendingEffectuation(user, userRole, ManualAssignmentEffectuationOperation.ASSIGN);

			verify(manualAssignmentEffectuationDao).delete(pendingRemoval);
			verify(manualAssignmentEffectuationDao, never()).save(any());
		}

		@Test
		@DisplayName("does not create a duplicate row when an identical pending row already exists")
		void skipsDuplicate() {
			ItSystem itSystem = manualItSystem();
			UserRole userRole = createUserRole("role-uuid", itSystem, List.of());
			User user = createUser("user-uuid", "userId", "Test User");

			when(manualAssignmentEffectuationDao.findByUser_UuidAndUserRole_IdAndOperationAndStatus(
				"user-uuid", userRole.getId(), ManualAssignmentEffectuationOperation.REMOVE, ManualAssignmentEffectuationStatus.PENDING))
				.thenReturn(Optional.empty());
			when(manualAssignmentEffectuationDao.findByUser_UuidAndUserRole_IdAndOperationAndStatus(
				"user-uuid", userRole.getId(), ManualAssignmentEffectuationOperation.ASSIGN, ManualAssignmentEffectuationStatus.PENDING))
				.thenReturn(Optional.of(new ManualAssignmentEffectuation()));

			service.registerPendingEffectuation(user, userRole, ManualAssignmentEffectuationOperation.ASSIGN);

			verify(manualAssignmentEffectuationDao, never()).save(any());
			verify(manualAssignmentEffectuationDao, never()).delete(any());
		}

		@Test
		@DisplayName("creates a new pending row when neither a duplicate nor an opposite exists")
		void createsNewRow() {
			ItSystem itSystem = manualItSystem();
			UserRole userRole = createUserRole("role-uuid", itSystem, List.of());
			User user = createUser("user-uuid", "userId", "Test User");

			when(manualAssignmentEffectuationDao.findByUser_UuidAndUserRole_IdAndOperationAndStatus(
				any(), anyLong(), any(), any())).thenReturn(Optional.empty());

			service.registerPendingEffectuation(user, userRole, ManualAssignmentEffectuationOperation.ASSIGN);

			ArgumentCaptor<ManualAssignmentEffectuation> captor = ArgumentCaptor.forClass(ManualAssignmentEffectuation.class);
			verify(manualAssignmentEffectuationDao).save(captor.capture());

			ManualAssignmentEffectuation saved = captor.getValue();
			assertThat(saved.getUser()).isEqualTo(user);
			assertThat(saved.getUserRole()).isEqualTo(userRole);
			assertThat(saved.getItSystem()).isEqualTo(itSystem);
			assertThat(saved.getOperation()).isEqualTo(ManualAssignmentEffectuationOperation.ASSIGN);
			assertThat(saved.getStatus()).isEqualTo(ManualAssignmentEffectuationStatus.PENDING);
		}

		@Test
		@DisplayName("no longer triggers a notification directly - notification is batched via the digest")
		void doesNotNotifyDirectly() {
			ItSystem itSystem = manualItSystem();
			UserRole userRole = createUserRole("role-uuid", itSystem, List.of());
			User user = createUser("user-uuid", "userId", "Test User");

			when(manualAssignmentEffectuationDao.findByUser_UuidAndUserRole_IdAndOperationAndStatus(
				any(), anyLong(), any(), any())).thenReturn(Optional.empty());

			service.registerPendingEffectuation(user, userRole, ManualAssignmentEffectuationOperation.ASSIGN);

			verifyNoInteractions(manualAssignmentEffectuationNotifier);
		}
	}

	@Nested
	@DisplayName("markEffectuated")
	class MarkEffectuated {

		@Test
		@DisplayName("is idempotent - completing an already-effectuated task is a no-op")
		void idempotent() {
			ManualAssignmentEffectuation effectuation = new ManualAssignmentEffectuation();
			effectuation.setStatus(ManualAssignmentEffectuationStatus.COMPLETED);
			when(manualAssignmentEffectuationDao.findById(1L)).thenReturn(Optional.of(effectuation));

			service.markEffectuated(1L, "performer-uuid", "comment");

			verify(manualAssignmentEffectuationDao, never()).save(any());
			verifyNoInteractions(manualWelcomeEmailTemplateService);
			verifyNoInteractions(auditLogger);
		}

		@Test
		@DisplayName("throws when the task does not exist")
		void throwsWhenNotFound() {
			when(manualAssignmentEffectuationDao.findById(1L)).thenReturn(Optional.empty());

			org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> service.markEffectuated(1L, "performer-uuid", "comment"));
		}

		@Test
		@DisplayName("does not send a removal email when the REMOVE template is disabled")
		void skipsRemovalEmailWhenTemplateDisabled() {
			ItSystem itSystem = manualItSystem();
			User user = createUser("user-uuid", "userId", "Test User", "user@example.com");
			UserRole userRole = createUserRole("role-uuid", itSystem, List.of());

			ManualAssignmentEffectuation removal = new ManualAssignmentEffectuation();
			removal.setStatus(ManualAssignmentEffectuationStatus.PENDING);
			removal.setOperation(ManualAssignmentEffectuationOperation.REMOVE);
			removal.setItSystem(itSystem);
			removal.setUser(user);
			removal.setUserRole(userRole);
			when(manualAssignmentEffectuationDao.findById(1L)).thenReturn(Optional.of(removal));

			ManualWelcomeEmailTemplate template = new ManualWelcomeEmailTemplate();
			template.setEnabled(false);
			when(manualWelcomeEmailTemplateService.findByItSystem(itSystem, ManualAssignmentEffectuationOperation.REMOVE)).thenReturn(template);

			service.markEffectuated(1L, "performer-uuid", null);

			verifyNoInteractions(emailQueueService);
			verify(auditLogger).log(eq(removal), eq(dk.digitalidentity.rc.dao.model.enums.EventType.MANUAL_EFFECTUATION_COMPLETED), eq(null), eq("performer-uuid"));
		}

		@Test
		@DisplayName("does not send the welcome email when the template is disabled")
		void skipsWelcomeEmailWhenTemplateDisabled() {
			ItSystem itSystem = manualItSystem();
			User user = createUser("user-uuid", "userId", "Test User", "user@example.com");
			UserRole userRole = createUserRole("role-uuid", itSystem, List.of());

			ManualAssignmentEffectuation assignment = new ManualAssignmentEffectuation();
			assignment.setStatus(ManualAssignmentEffectuationStatus.PENDING);
			assignment.setOperation(ManualAssignmentEffectuationOperation.ASSIGN);
			assignment.setItSystem(itSystem);
			assignment.setUser(user);
			assignment.setUserRole(userRole);
			when(manualAssignmentEffectuationDao.findById(1L)).thenReturn(Optional.of(assignment));

			ManualWelcomeEmailTemplate template = new ManualWelcomeEmailTemplate();
			template.setEnabled(false);
			when(manualWelcomeEmailTemplateService.findByItSystem(itSystem, ManualAssignmentEffectuationOperation.ASSIGN)).thenReturn(template);

			service.markEffectuated(1L, "performer-uuid", null);

			verifyNoInteractions(emailQueueService);
		}

		@Test
		@DisplayName("does not send the welcome email when the user has no email address")
		void skipsWelcomeEmailWhenUserHasNoEmail() {
			ItSystem itSystem = manualItSystem();
			User user = createUser("user-uuid", "userId", "Test User");
			UserRole userRole = createUserRole("role-uuid", itSystem, List.of());

			ManualAssignmentEffectuation assignment = new ManualAssignmentEffectuation();
			assignment.setStatus(ManualAssignmentEffectuationStatus.PENDING);
			assignment.setOperation(ManualAssignmentEffectuationOperation.ASSIGN);
			assignment.setItSystem(itSystem);
			assignment.setUser(user);
			assignment.setUserRole(userRole);
			when(manualAssignmentEffectuationDao.findById(1L)).thenReturn(Optional.of(assignment));

			ManualWelcomeEmailTemplate template = new ManualWelcomeEmailTemplate();
			template.setEnabled(true);
			when(manualWelcomeEmailTemplateService.findByItSystem(itSystem, ManualAssignmentEffectuationOperation.ASSIGN)).thenReturn(template);

			service.markEffectuated(1L, "performer-uuid", null);

			verifyNoInteractions(emailQueueService);
		}

		@Test
		@DisplayName("sends the rendered welcome email when enabled and the user has an email")
		void sendsWelcomeEmail() {
			ItSystem itSystem = manualItSystem();
			User user = createUser("user-uuid", "userId", "Test User", "user@example.com");
			UserRole userRole = createUserRole("role-uuid", itSystem, List.of());
			userRole.setName("Some Role");

			ManualAssignmentEffectuation assignment = new ManualAssignmentEffectuation();
			assignment.setStatus(ManualAssignmentEffectuationStatus.PENDING);
			assignment.setOperation(ManualAssignmentEffectuationOperation.ASSIGN);
			assignment.setItSystem(itSystem);
			assignment.setUser(user);
			assignment.setUserRole(userRole);
			assignment.setComment("some comment");
			when(manualAssignmentEffectuationDao.findById(1L)).thenReturn(Optional.of(assignment));

			ManualWelcomeEmailTemplate template = new ManualWelcomeEmailTemplate();
			template.setEnabled(true);
			template.setTitle("Welcome {itsystem}");
			template.setMessage("Hi {modtager}, role {rolle}: {supplerende_kommentar}");
			when(manualWelcomeEmailTemplateService.findByItSystem(itSystem, ManualAssignmentEffectuationOperation.ASSIGN)).thenReturn(template);
			when(emailTemplateRenderer.render(eq("Welcome {itsystem}"), any())).thenReturn("Welcome rendered");
			when(emailTemplateRenderer.render(eq("Hi {modtager}, role {rolle}: {supplerende_kommentar}"), any())).thenReturn("Body rendered");

			service.markEffectuated(1L, "performer-uuid", "some comment");

			verify(emailQueueService).queueEmail(eq("user@example.com"), eq("Welcome rendered"), eq("Body rendered"), eq(null), eq(null), eq(null));
		}

		@Test
		@DisplayName("sends the rendered removal email when enabled and the user has an email")
		void sendsRemovalEmail() {
			ItSystem itSystem = manualItSystem();
			User user = createUser("user-uuid", "userId", "Test User", "user@example.com");
			UserRole userRole = createUserRole("role-uuid", itSystem, List.of());
			userRole.setName("Some Role");

			ManualAssignmentEffectuation assignment = new ManualAssignmentEffectuation();
			assignment.setStatus(ManualAssignmentEffectuationStatus.PENDING);
			assignment.setOperation(ManualAssignmentEffectuationOperation.REMOVE);
			assignment.setItSystem(itSystem);
			assignment.setUser(user);
			assignment.setUserRole(userRole);
			assignment.setComment("some comment");
			when(manualAssignmentEffectuationDao.findById(1L)).thenReturn(Optional.of(assignment));

			ManualWelcomeEmailTemplate template = new ManualWelcomeEmailTemplate();
			template.setEnabled(true);
			template.setTitle("Goodbye {itsystem}");
			template.setMessage("Hi {modtager}, role {rolle} removed: {supplerende_kommentar}");
			when(manualWelcomeEmailTemplateService.findByItSystem(itSystem, ManualAssignmentEffectuationOperation.REMOVE)).thenReturn(template);
			when(emailTemplateRenderer.render(eq("Goodbye {itsystem}"), any())).thenReturn("Goodbye rendered");
			when(emailTemplateRenderer.render(eq("Hi {modtager}, role {rolle} removed: {supplerende_kommentar}"), any())).thenReturn("Body rendered");

			service.markEffectuated(1L, "performer-uuid", "some comment");

			verify(emailQueueService).queueEmail(eq("user@example.com"), eq("Goodbye rendered"), eq("Body rendered"), eq(null), eq(null), eq(null));
		}

		@Test
		@DisplayName("stores the performer identifier and comment on completion")
		void storesPerformerAndComment() {
			ItSystem itSystem = manualItSystem();
			User user = createUser("user-uuid", "userId", "Test User");
			UserRole userRole = createUserRole("role-uuid", itSystem, List.of());

			ManualAssignmentEffectuation assignment = new ManualAssignmentEffectuation();
			assignment.setStatus(ManualAssignmentEffectuationStatus.PENDING);
			assignment.setOperation(ManualAssignmentEffectuationOperation.REMOVE);
			assignment.setItSystem(itSystem);
			assignment.setUser(user);
			assignment.setUserRole(userRole);
			when(manualAssignmentEffectuationDao.findById(1L)).thenReturn(Optional.of(assignment));

			ManualWelcomeEmailTemplate template = new ManualWelcomeEmailTemplate();
			template.setEnabled(false);
			when(manualWelcomeEmailTemplateService.findByItSystem(itSystem, ManualAssignmentEffectuationOperation.REMOVE)).thenReturn(template);

			service.markEffectuated(1L, "integration-robot", "done via script");

			assertThat(assignment.getStatus()).isEqualTo(ManualAssignmentEffectuationStatus.COMPLETED);
			assertThat(assignment.getCompletedByUserUuid()).isEqualTo("integration-robot");
			assertThat(assignment.getComment()).isEqualTo("done via script");
			verify(manualAssignmentEffectuationDao).save(assignment);
		}
	}

	@Nested
	@DisplayName("notifyPendingEffectuationsDigest")
	class NotifyPendingEffectuationsDigest {

		@Test
		@DisplayName("is a no-op when there is nothing to notify about")
		void noOpWhenEmpty() {
			when(manualAssignmentEffectuationDao.findByStatusAndEmailSent(ManualAssignmentEffectuationStatus.PENDING, false))
				.thenReturn(List.of());

			service.notifyPendingEffectuationsDigest();

			verifyNoInteractions(manualAssignmentEffectuationNotifier);
			verify(manualAssignmentEffectuationDao, never()).saveAll(any());
		}

		@Test
		@DisplayName("sends the digest and marks all covered rows as emailSent")
		void sendsDigestAndMarksSent() {
			ManualAssignmentEffectuation a = new ManualAssignmentEffectuation();
			ManualAssignmentEffectuation b = new ManualAssignmentEffectuation();
			List<ManualAssignmentEffectuation> pending = List.of(a, b);
			when(manualAssignmentEffectuationDao.findByStatusAndEmailSent(ManualAssignmentEffectuationStatus.PENDING, false))
				.thenReturn(pending);

			service.notifyPendingEffectuationsDigest();

			verify(manualAssignmentEffectuationNotifier).notifySystemOwnersOfPendingEffectuationDigest(pending);
			assertThat(a.isEmailSent()).isTrue();
			assertThat(b.isEmailSent()).isTrue();
			verify(manualAssignmentEffectuationDao).saveAll(pending);
		}
	}

	@Nested
	@DisplayName("deleteEffectuatedOlderThan")
	class DeleteEffectuatedOlderThan {

		@Test
		@DisplayName("delegates to the DAO with the given cutoff and returns the deleted count")
		void delegatesToDao() {
			java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusMonths(3);
			when(manualAssignmentEffectuationDao.deleteByStatusAndCompletedAtBefore(ManualAssignmentEffectuationStatus.COMPLETED, cutoff))
				.thenReturn(5L);

			long deleted = service.deleteEffectuatedOlderThan(cutoff);

			assertThat(deleted).isEqualTo(5L);
			verify(manualAssignmentEffectuationDao).deleteByStatusAndCompletedAtBefore(ManualAssignmentEffectuationStatus.COMPLETED, cutoff);
		}
	}
}
