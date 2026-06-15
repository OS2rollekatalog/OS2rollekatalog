package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationMailDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationMail;
import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManagerDelegate;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.ManagerDelegateService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createEmailTemplate;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createItSystemRolesAttestation;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AttestationEmailNotificationService — delegate email targeting")
class AttestationEmailNotificationServiceTest {

    @Mock private AttestationDao attestationDao;
    @Mock private AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;
    @Mock private AttestationEmailNotificationService self;
    @Mock private EmailTemplateService emailTemplateService;
    @Mock private EmailQueueService emailQueueService;
    @Mock private AttestationMailDao attestationMailDao;
    @Mock private SettingsService settingsService;
    @Mock private UserDao userDao;
    @Mock private OrgUnitDao orgUnitDao;
    @Mock private AttestationRunService attestationRunService;
    @Mock private ItSystemDao itSystemDao;
    @Mock private ItSystemService itSystemService;
    @Mock private OrgUnitService orgUnitService;
    @Mock private ManagerDelegateService managerDelegateService;

    @InjectMocks
    private AttestationEmailNotificationService service;

    // ---- Helpers ---- //

    private static User makeUser(String uuid) {
        User user = new User();
        user.setUuid(uuid);
        user.setName("User " + uuid);
        return user;
    }

    private static ManagerDelegate makeManagerDelegate(User manager, User delegate) {
        return ManagerDelegate.builder()
                .manager(manager)
                .delegate(delegate)
                .fromDate(LocalDate.now())
                .indefinitely(true)
                .build();
    }

    private static Attestation delegatedAttestation(Long responsibleCollectionId, String responsibleOuUuid) {
        return Attestation.builder()
                .attestationType(Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION)
                .responsibleCollectionId(responsibleCollectionId)
                .responsibleOuUuid(responsibleOuUuid)
                .createdAt(LocalDate.now())
                .deadline(LocalDate.now().plusDays(7))
                .build();
    }

    @Nested
    @DisplayName("findTargetUsers — MANAGER_DELEGATED_ATTESTATION")
    class FindTargetUsersDelegated {

        @Test
        @DisplayName("targets delegates of the responsible manager, not the manager themselves")
        void whenResponsibleUserUuidIsSet_returnsManagersDelegates() {
            // ---- Given ---- //
            User manager = makeUser("manager-uuid");
            User delegate = makeUser("delegate-uuid");
            ManagerDelegate md = makeManagerDelegate(manager, delegate);
            Attestation attestation = delegatedAttestation(1L, "ou-uuid");

            when(attestationResponsibleCollectionDao.findById(1L))
                    .thenReturn(Optional.of(new AttestationResponsibleCollection(1L, null, List.of("manager-uuid"))));
            when(userDao.findByUuidInAndDeletedFalse(Set.of("manager-uuid"))).thenReturn(List.of(manager));
            when(managerDelegateService.getByManager(manager)).thenReturn(List.of(md));

            // ---- When ---- //
            List<User> result = service.findTargetUsers(attestation, false);

            // ---- Then ---- //
            assertThat(result).containsExactly(delegate);
            assertThat(result).doesNotContain(manager);
        }

        @Test
        @DisplayName("resolves the manager via OrgUnit when no responsibleCollectionId is set")
        void whenResponsibleUserUuidIsNull_resolvesManagerFromOuAndReturnsDelegates() {
            // ---- Given ---- //
            User manager = makeUser("manager-uuid");
            User delegate = makeUser("delegate-uuid");
            ManagerDelegate md = makeManagerDelegate(manager, delegate);
            OrgUnit orgUnit = new OrgUnit();
            orgUnit.setManager(manager);
            Attestation attestation = delegatedAttestation(null, "ou-uuid");

            when(orgUnitDao.findById("ou-uuid")).thenReturn(Optional.of(orgUnit));
            when(managerDelegateService.getByManager(manager)).thenReturn(List.of(md));

            // ---- When ---- //
            List<User> result = service.findTargetUsers(attestation, false);

            // ---- Then ---- //
            assertThat(result).containsExactly(delegate);
            verify(orgUnitDao).findById("ou-uuid");
        }

        @Test
        @DisplayName("targets all delegates when a manager has more than one")
        void whenManagerHasMultipleDelegates_allDelegatesAreReturned() {
            // ---- Given ---- //
            User manager = makeUser("manager-uuid");
            User delegate1 = makeUser("delegate-uuid-1");
            User delegate2 = makeUser("delegate-uuid-2");
            ManagerDelegate md1 = makeManagerDelegate(manager, delegate1);
            ManagerDelegate md2 = makeManagerDelegate(manager, delegate2);
            Attestation attestation = delegatedAttestation(1L, "ou-uuid");

            when(attestationResponsibleCollectionDao.findById(1L))
                    .thenReturn(Optional.of(new AttestationResponsibleCollection(1L, null, List.of("manager-uuid"))));
            when(userDao.findByUuidInAndDeletedFalse(Set.of("manager-uuid"))).thenReturn(List.of(manager));
            when(managerDelegateService.getByManager(manager)).thenReturn(List.of(md1, md2));

            // ---- When ---- //
            List<User> result = service.findTargetUsers(attestation, false);

            // ---- Then ---- //
            assertThat(result).hasSize(2).containsExactlyInAnyOrder(delegate1, delegate2);
            assertThat(result).doesNotContain(manager);
        }

        @Test
        @DisplayName("returns null without querying delegates when no manager can be resolved")
        void whenNoManagerResolved_returnsNullAndDoesNotCallDelegateService() {
            // ---- Given ---- //
            // Both responsible UUIDs are null — responsibleUser stays null
            Attestation attestation = delegatedAttestation(null, null);

            // ---- When ---- //
            List<User> result = service.findTargetUsers(attestation, false);

            // ---- Then ---- //
            assertThat(result).isNull();
            verify(managerDelegateService, never()).getByManager(any());
        }
    }

    @Nested
    @DisplayName("sendEmail — system owner CC list with multiple owners")
    class SendEmailSystemOwnerCc {

        private static final long ATTESTATION_ID = 42L;
        private static final long IT_SYSTEM_ID = 7L;

        private static User makeUserWithEmail(String uuid, String email) {
            return createUser(uuid, uuid, "User " + uuid, email);
        }

        private static EmailTemplate enabledTemplate() {
            return createEmailTemplate(EmailTemplateType.ATTESTATION_IT_SYSTEM_NOTIFICATION, "Test", "Hello {modtager}", true);
        }

        private static Attestation itSystemRolesAttestation(Long responsibleCollectionId) {
            Attestation a = createItSystemRolesAttestation(ATTESTATION_ID, "att-uuid", IT_SYSTEM_ID, "Test System", responsibleCollectionId);
            a.setMails(new HashSet<>());
            return a;
        }

        @Test
        @DisplayName("sends no CC when system has no owners")
        void noSystemOwners_ccIsNull() {
            User responsible = makeUserWithEmail("uuid-resp", "resp@example.com");
            Attestation attestation = itSystemRolesAttestation(1L);

            ItSystem itSystem = createItSystem(IT_SYSTEM_ID, "Test System");

            EmailTemplate template = enabledTemplate();
            AttestationMail savedMail = AttestationMail.builder()
                    .attestation(attestation).emailTemplateType(template.getTemplateType())
                    .emailType(AttestationMail.MailType.INFORMATION).build();

            when(attestationDao.findById(ATTESTATION_ID)).thenReturn(Optional.of(attestation));
            when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_NOTIFICATION)).thenReturn(template);
            when(attestationResponsibleCollectionDao.findById(1L))
                    .thenReturn(Optional.of(new AttestationResponsibleCollection(1L, IT_SYSTEM_ID, List.of("uuid-resp"))));
            when(userDao.findByUuidInAndDeletedFalse(Set.of("uuid-resp"))).thenReturn(List.of(responsible));
            when(itSystemDao.findById(IT_SYSTEM_ID)).thenReturn(Optional.of(itSystem));
            when(itSystemService.getSystemOwners(itSystem)).thenReturn(List.of());
            when(attestationMailDao.save(any())).thenReturn(savedMail);

            service.sendEmail(ATTESTATION_ID, Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, AttestationMail.MailType.INFORMATION);

            ArgumentCaptor<String> ccCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailQueueService).queueEmail(eq("resp@example.com"), any(), any(), any(), isNull(), ccCaptor.capture());
            assertThat(ccCaptor.getValue()).isNull();
        }

        @Test
        @DisplayName("sends single owner as CC when owner email differs from responsible")
        void singleOwnerDifferentEmail_sentAsCC() {
            User responsible = makeUserWithEmail("uuid-resp", "resp@example.com");
            User owner = makeUserWithEmail("uuid-owner", "owner@example.com");
            Attestation attestation = itSystemRolesAttestation(1L);

            ItSystem itSystem = createItSystem(IT_SYSTEM_ID, "Test System");

            EmailTemplate template = enabledTemplate();
            AttestationMail savedMail = AttestationMail.builder()
                    .attestation(attestation).emailTemplateType(template.getTemplateType())
                    .emailType(AttestationMail.MailType.INFORMATION).build();

            when(attestationDao.findById(ATTESTATION_ID)).thenReturn(Optional.of(attestation));
            when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_NOTIFICATION)).thenReturn(template);
            when(attestationResponsibleCollectionDao.findById(1L))
                    .thenReturn(Optional.of(new AttestationResponsibleCollection(1L, IT_SYSTEM_ID, List.of("uuid-resp"))));
            when(userDao.findByUuidInAndDeletedFalse(Set.of("uuid-resp"))).thenReturn(List.of(responsible));
            when(itSystemDao.findById(IT_SYSTEM_ID)).thenReturn(Optional.of(itSystem));
            when(itSystemService.getSystemOwners(itSystem)).thenReturn(List.of(owner));
            when(attestationMailDao.save(any())).thenReturn(savedMail);

            service.sendEmail(ATTESTATION_ID, Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, AttestationMail.MailType.INFORMATION);

            ArgumentCaptor<String> ccCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailQueueService).queueEmail(eq("resp@example.com"), any(), any(), any(), isNull(), ccCaptor.capture());
            assertThat(ccCaptor.getValue()).isEqualTo("owner@example.com");
        }

        @Test
        @DisplayName("sends multiple owners as comma-separated CC when all emails differ from responsible")
        void multipleOwnersDifferentEmails_allSentAsCC() {
            User responsible = makeUserWithEmail("uuid-resp", "resp@example.com");
            User owner1 = makeUserWithEmail("uuid-o1", "owner1@example.com");
            User owner2 = makeUserWithEmail("uuid-o2", "owner2@example.com");
            Attestation attestation = itSystemRolesAttestation(1L);

            ItSystem itSystem = createItSystem(IT_SYSTEM_ID, "Test System");

            EmailTemplate template = enabledTemplate();
            AttestationMail savedMail = AttestationMail.builder()
                    .attestation(attestation).emailTemplateType(template.getTemplateType())
                    .emailType(AttestationMail.MailType.INFORMATION).build();

            when(attestationDao.findById(ATTESTATION_ID)).thenReturn(Optional.of(attestation));
            when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_NOTIFICATION)).thenReturn(template);
            when(attestationResponsibleCollectionDao.findById(1L))
                    .thenReturn(Optional.of(new AttestationResponsibleCollection(1L, IT_SYSTEM_ID, List.of("uuid-resp"))));
            when(userDao.findByUuidInAndDeletedFalse(Set.of("uuid-resp"))).thenReturn(List.of(responsible));
            when(itSystemDao.findById(IT_SYSTEM_ID)).thenReturn(Optional.of(itSystem));
            when(itSystemService.getSystemOwners(itSystem)).thenReturn(List.of(owner1, owner2));
            when(attestationMailDao.save(any())).thenReturn(savedMail);

            service.sendEmail(ATTESTATION_ID, Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, AttestationMail.MailType.INFORMATION);

            ArgumentCaptor<String> ccCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailQueueService).queueEmail(eq("resp@example.com"), any(), any(), any(), isNull(), ccCaptor.capture());
            assertThat(ccCaptor.getValue().split(","))
                    .containsExactlyInAnyOrder("owner1@example.com", "owner2@example.com");
        }

        @Test
        @DisplayName("excludes owner from CC when owner email equals the responsible email")
        void ownerEmailSameAsResponsible_excludedFromCC() {
            User responsible = makeUserWithEmail("uuid-resp", "shared@example.com");
            User owner = makeUserWithEmail("uuid-owner", "shared@example.com");
            Attestation attestation = itSystemRolesAttestation(1L);

            ItSystem itSystem = createItSystem(IT_SYSTEM_ID, "Test System");

            EmailTemplate template = enabledTemplate();
            AttestationMail savedMail = AttestationMail.builder()
                    .attestation(attestation).emailTemplateType(template.getTemplateType())
                    .emailType(AttestationMail.MailType.INFORMATION).build();

            when(attestationDao.findById(ATTESTATION_ID)).thenReturn(Optional.of(attestation));
            when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_IT_SYSTEM_NOTIFICATION)).thenReturn(template);
            when(attestationResponsibleCollectionDao.findById(1L))
                    .thenReturn(Optional.of(new AttestationResponsibleCollection(1L, IT_SYSTEM_ID, List.of("uuid-resp"))));
            when(userDao.findByUuidInAndDeletedFalse(Set.of("uuid-resp"))).thenReturn(List.of(responsible));
            when(itSystemDao.findById(IT_SYSTEM_ID)).thenReturn(Optional.of(itSystem));
            when(itSystemService.getSystemOwners(itSystem)).thenReturn(List.of(owner));
            when(attestationMailDao.save(any())).thenReturn(savedMail);

            service.sendEmail(ATTESTATION_ID, Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, AttestationMail.MailType.INFORMATION);

            ArgumentCaptor<String> ccCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailQueueService).queueEmail(eq("shared@example.com"), any(), any(), any(), isNull(), ccCaptor.capture());
            assertThat(ccCaptor.getValue()).isNull();
        }
    }
}
