package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ItSystemSystemOwner;
import dk.digitalidentity.rc.dao.model.ManualAssignmentEffectuation;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.saml.config.SamlConfiguration;
import dk.digitalidentity.saml.config.settings.SPConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createEmailTemplate;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createItSystem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManualAssignmentEffectuationNotifier")
class ManualAssignmentEffectuationNotifierTest {

	@Mock private EmailTemplateService emailTemplateService;
	@Mock private EmailTemplateRenderer emailTemplateRenderer;
	@Mock private EmailQueueService emailQueueService;
	@Mock private SamlConfiguration samlConfiguration;

	@InjectMocks
	private ManualAssignmentEffectuationNotifier notifier;

	@BeforeEach
	void setUp() {
		SPConfiguration sp = new SPConfiguration();
		sp.setBaseUrl("https://rollekatalog.example.com");
		lenient().when(samlConfiguration.getSp()).thenReturn(sp);
		lenient().when(emailTemplateRenderer.renderTitle(any(), any())).thenReturn("rendered title");
		lenient().when(emailTemplateRenderer.render(anyString(), any())).thenReturn("rendered message");
	}

	private ItSystemSystemOwner ownerOf(ItSystem itSystem, User user) {
		return ItSystemSystemOwner.builder().itSystem(itSystem).user(user).build();
	}

	private ManualAssignmentEffectuation pendingFor(ItSystem itSystem, long id) {
		ManualAssignmentEffectuation e = new ManualAssignmentEffectuation();
		e.setId(id);
		e.setItSystem(itSystem);
		return e;
	}

	@Test
	@DisplayName("does nothing when there are no pending effectuations")
	void noOpWhenEmpty() {
		ItSystem itSystem = createItSystem("it-system-uuid", List.of());

		notifier.notifySystemOwnersOfPendingEffectuations(itSystem, List.of());

		verifyNoInteractions(emailTemplateService, emailQueueService);
	}

	@Test
	@DisplayName("does nothing when the it-system has no registered system owners")
	void noOpWhenNoSystemOwners() {
		ItSystem itSystem = createItSystem("it-system-uuid", List.of());
		itSystem.setSystemOwners(new ArrayList<>());

		notifier.notifySystemOwnersOfPendingEffectuations(itSystem, List.of(pendingFor(itSystem, 1L)));

		verifyNoInteractions(emailTemplateService, emailQueueService);
	}

	@Test
	@DisplayName("does not send an email when the template is disabled")
	void noOpWhenTemplateDisabled() {
		ItSystem itSystem = createItSystem("it-system-uuid", List.of());
		User owner = createUser("owner-uuid", "ownerId", "Owner", "owner@example.com");
		itSystem.setSystemOwners(List.of(ownerOf(itSystem, owner)));

		when(emailTemplateService.findByTemplateType(EmailTemplateType.MANUAL_ITSYSTEM_ASSIGNMENT_PENDING))
			.thenReturn(createEmailTemplate(EmailTemplateType.MANUAL_ITSYSTEM_ASSIGNMENT_PENDING, "title", "message", false));

		notifier.notifySystemOwnersOfPendingEffectuations(itSystem, List.of(pendingFor(itSystem, 1L)));

		verifyNoInteractions(emailQueueService);
	}

	@Test
	@DisplayName("skips system owners without an email address")
	void skipsOwnersWithoutEmail() {
		ItSystem itSystem = createItSystem("it-system-uuid", List.of());
		User ownerWithoutEmail = createUser("owner-uuid", "ownerId", "Owner");
		itSystem.setSystemOwners(List.of(ownerOf(itSystem, ownerWithoutEmail)));

		when(emailTemplateService.findByTemplateType(EmailTemplateType.MANUAL_ITSYSTEM_ASSIGNMENT_PENDING))
			.thenReturn(createEmailTemplate(EmailTemplateType.MANUAL_ITSYSTEM_ASSIGNMENT_PENDING, "title", "message", true));

		notifier.notifySystemOwnersOfPendingEffectuations(itSystem, List.of(pendingFor(itSystem, 1L)));

		verifyNoInteractions(emailQueueService);
	}

	@Test
	@DisplayName("sends one email per system owner, with the correct count placeholder")
	void sendsOneEmailPerOwner() {
		ItSystem itSystem = createItSystem("it-system-uuid", List.of());
		User owner1 = createUser("owner-1-uuid", "owner1Id", "Owner One", "owner1@example.com");
		User owner2 = createUser("owner-2-uuid", "owner2Id", "Owner Two", "owner2@example.com");
		itSystem.setSystemOwners(List.of(ownerOf(itSystem, owner1), ownerOf(itSystem, owner2)));

		when(emailTemplateService.findByTemplateType(EmailTemplateType.MANUAL_ITSYSTEM_ASSIGNMENT_PENDING))
			.thenReturn(createEmailTemplate(EmailTemplateType.MANUAL_ITSYSTEM_ASSIGNMENT_PENDING, "title", "message", true));

		List<ManualAssignmentEffectuation> pending = List.of(pendingFor(itSystem, 1L), pendingFor(itSystem, 2L), pendingFor(itSystem, 3L));

		notifier.notifySystemOwnersOfPendingEffectuations(itSystem, pending);

		verify(emailQueueService, times(2)).queueEmail(anyString(), anyString(), anyString(), any(), any(), any());
		verify(emailQueueService).queueEmail(eq("owner1@example.com"), anyString(), anyString(), any(), any(), any());
		verify(emailQueueService).queueEmail(eq("owner2@example.com"), anyString(), anyString(), any(), any(), any());
	}

	@Test
	@DisplayName("the digest link carries the exact task ids it covers")
	void digestLinkCarriesTaskIds() {
		ItSystem itSystem = createItSystem("it-system-uuid", List.of());
		User owner = createUser("owner-uuid", "ownerId", "Owner", "owner@example.com");
		itSystem.setSystemOwners(List.of(ownerOf(itSystem, owner)));

		when(emailTemplateService.findByTemplateType(EmailTemplateType.MANUAL_ITSYSTEM_ASSIGNMENT_PENDING))
			.thenReturn(createEmailTemplate(EmailTemplateType.MANUAL_ITSYSTEM_ASSIGNMENT_PENDING, "title", "message", true));

		List<ManualAssignmentEffectuation> pending = List.of(pendingFor(itSystem, 12L), pendingFor(itSystem, 13L), pendingFor(itSystem, 14L));

		notifier.notifySystemOwnersOfPendingEffectuations(itSystem, pending);

		ArgumentCaptor<Map<EmailTemplatePlaceholder, String>> captor = ArgumentCaptor.forClass(Map.class);
		verify(emailTemplateRenderer).render(anyString(), captor.capture());

		String link = captor.getValue().get(EmailTemplatePlaceholder.TASK_LINK_PLACEHOLDER);
		assertThat(link).isEqualTo("https://rollekatalog.example.com/ui/manualeffectuation?ids=12,13,14");

		String count = captor.getValue().get(EmailTemplatePlaceholder.COUNT_PLACEHOLDER);
		assertThat(count).isEqualTo("3");
	}

	@Test
	@DisplayName("groups pending effectuations by it-system and sends one digest per group")
	void digestGroupsByItSystem() {
		ItSystem itSystemA = createItSystem("it-system-a-uuid", List.of());
		itSystemA.setId(1L);
		ItSystem itSystemB = createItSystem("it-system-b-uuid", List.of());
		itSystemB.setId(2L);
		User ownerA = createUser("owner-a-uuid", "ownerAId", "Owner A", "ownerA@example.com");
		User ownerB = createUser("owner-b-uuid", "ownerBId", "Owner B", "ownerB@example.com");
		itSystemA.setSystemOwners(List.of(ownerOf(itSystemA, ownerA)));
		itSystemB.setSystemOwners(List.of(ownerOf(itSystemB, ownerB)));

		when(emailTemplateService.findByTemplateType(EmailTemplateType.MANUAL_ITSYSTEM_ASSIGNMENT_PENDING))
			.thenReturn(createEmailTemplate(EmailTemplateType.MANUAL_ITSYSTEM_ASSIGNMENT_PENDING, "title", "message", true));

		List<ManualAssignmentEffectuation> pending = List.of(
			pendingFor(itSystemA, 1L), pendingFor(itSystemA, 2L), pendingFor(itSystemB, 3L));

		notifier.notifySystemOwnersOfPendingEffectuationDigest(pending);

		verify(emailQueueService, times(2)).queueEmail(anyString(), anyString(), anyString(), any(), any(), any());
		verify(emailQueueService).queueEmail(eq("ownerA@example.com"), anyString(), anyString(), any(), any(), any());
		verify(emailQueueService).queueEmail(eq("ownerB@example.com"), anyString(), anyString(), any(), any(), any());
	}
}
