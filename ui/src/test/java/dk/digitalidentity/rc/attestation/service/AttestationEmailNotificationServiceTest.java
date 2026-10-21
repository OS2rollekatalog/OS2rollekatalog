package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationMailDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.model.AttestationMailRecipient;
import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationMail;
import dk.digitalidentity.rc.attestation.model.entity.AttestationMailReceiver;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManagerDelegate;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.AttestationEscalationRecipients;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createAttestationRun;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createEmailTemplate;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createItSystemRolesAttestation;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createOrganisationAttestation;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @DisplayName("findAttesters — MANAGER_DELEGATED_ATTESTATION")
    class FindAttestersDelegated {

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
            List<User> result = service.findAttesters(attestation);

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
            List<User> result = service.findAttesters(attestation);

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
            List<User> result = service.findAttesters(attestation);

            // ---- Then ---- //
            assertThat(result).hasSize(2).containsExactlyInAnyOrder(delegate1, delegate2);
            assertThat(result).doesNotContain(manager);
        }

        @Test
        @DisplayName("returns no attesters without querying delegates when no manager can be resolved")
        void whenNoManagerResolved_returnsEmptyAndDoesNotCallDelegateService() {
            // ---- Given ---- //
            // Both responsible UUIDs are null — responsibleUser stays null
            Attestation attestation = delegatedAttestation(null, null);

            // ---- When ---- //
            List<User> result = service.findAttesters(attestation);

            // ---- Then ---- //
            assertThat(result).isEmpty();
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

    @Nested
    @DisplayName("sendEmailsOrganisation(type, mailType, now) — escalation mail grouping")
    class SendEmailsOrganisationEscalationGrouping {

        private static Position makePosition(User user, OrgUnit orgUnit) {
            Position position = new Position();
            position.setUser(user);
            position.setOrgUnit(orgUnit);
            return position;
        }

        private EmailTemplate stubEscalationTemplate() {
            EmailTemplate template = createEmailTemplate(EmailTemplateType.ATTESTATION_REMINDER_THIRDPARTY,
                    "Reminder", "{modtager}, leder {bruger} har ikke gennemført attestering for {enheder}", true);
            when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER_THIRDPARTY)).thenReturn(template);
            return template;
        }

        private void stubFixedAddressesOnly(String... emails) {
            when(settingsService.getAttestationEscalationRecipients()).thenReturn(AttestationEscalationRecipients.FIXED_ADDRESSES);
            when(settingsService.getAttestationEscalationEmails()).thenReturn(List.of(emails));
        }

        private List<Attestation> stubTwoOverdueUnitsUnderOneManager() {
            User areaManager = createUser("area-manager-uuid", "areamanager", "Area Manager", "areamanager@example.com");
            User managerA = createUser("manager-a-uuid", "managera", "Manager A", "managera@example.com");

            OrgUnit root = createOrgUnit("root-uuid", "Root");
            OrgUnit area = createOrgUnit("area-uuid", "Area", areaManager, root);
            OrgUnit unitA = createOrgUnit("unit-a-uuid", "Unit A", managerA, area);
            OrgUnit unitB = createOrgUnit("unit-b-uuid", "Unit B", managerA, area);

            managerA.setPositions(new ArrayList<>(List.of(makePosition(managerA, unitA))));
            managerA.setManagerSubstitutes(new ArrayList<>());

            Attestation attUnitA = createOrganisationAttestation(101L, "att-101", "unit-a-uuid", "Unit A");
            Attestation attUnitB = createOrganisationAttestation(102L, "att-102", "unit-b-uuid", "Unit B");
            LocalDate deadline = LocalDate.now().plusDays(7);
            attUnitA.setDeadline(deadline);
            attUnitB.setDeadline(deadline);

            AttestationRun run = createAttestationRun(1L, deadline);
            run.getAttestations().addAll(List.of(attUnitA, attUnitB));

            when(attestationRunService.getCurrentRun()).thenReturn(Optional.of(run));
            // Lenient: when every recipient is already mailed, no org unit lookup happens.
            lenient().when(orgUnitDao.findById("unit-a-uuid")).thenReturn(Optional.of(unitA));
            lenient().when(orgUnitDao.findById("unit-b-uuid")).thenReturn(Optional.of(unitB));
            return List.of(attUnitA, attUnitB);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("fixed addresses only: the address gets one combined mail and the manager gets none")
        void fixedAddressesOnly_addressReceivesSingleCombinedMail() {
            // ---- Given ---- //
            stubTwoOverdueUnitsUnderOneManager();
            EmailTemplate template = stubEscalationTemplate();
            stubFixedAddressesOnly("it-sikkerhed@kommune.dk");

            // ---- When ---- //
            service.sendEmailsOrganisation(Attestation.AttestationType.ORGANISATION_ATTESTATION,
                    AttestationMail.MailType.ESCALATION_REMINDER, LocalDate.now());

            // ---- Then ---- //
            ArgumentCaptor<AttestationMailRecipient> receiverCaptor = ArgumentCaptor.forClass(AttestationMailRecipient.class);
            ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
            verify(self, times(1)).sendEmailsOrganisation(receiverCaptor.capture(), idsCaptor.capture(), eq(AttestationMail.MailType.ESCALATION_REMINDER), eq(template));

            assertThat(receiverCaptor.getValue()).isEqualTo(AttestationMailRecipient.forAddress("it-sikkerhed@kommune.dk"));
            assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(101L, 102L);
        }

        @Test
        @DisplayName("fixed address already logged as receiver (any letter case) is not mailed again")
        void fixedAddressAlreadyMailed_isNotMailedAgain() {
            // ---- Given ---- //
            List<Attestation> attestations = stubTwoOverdueUnitsUnderOneManager();
            EmailTemplate template = stubEscalationTemplate();
            stubFixedAddressesOnly("it-sikkerhed@kommune.dk");
            attestations.forEach(attestation -> {
                AttestationMail mail = AttestationMail.builder()
                        .emailTemplateType(EmailTemplateType.ATTESTATION_REMINDER_THIRDPARTY)
                        .build();
                mail.getReceivers().add(AttestationMailReceiver.builder().email("IT-Sikkerhed@kommune.dk").build());
                attestation.getMails().add(mail);
            });

            // ---- When ---- //
            service.sendEmailsOrganisation(Attestation.AttestationType.ORGANISATION_ATTESTATION,
                    AttestationMail.MailType.ESCALATION_REMINDER, LocalDate.now());

            // ---- Then ---- //
            verify(self, never()).sendEmailsOrganisation(any(), any(), any(), eq(template));
        }

    	@SuppressWarnings("unchecked")
        @Test
        @DisplayName("receiver with two OUs under different managers gets one mail per manager, each with the correct manager name and only that manager's OU")
        void differentManagers_receivesOneMailPerManager() {
            // ---- Given ---- //
            User areaManager = createUser("area-manager-uuid", "areamanager", "Area Manager", "areamanager@example.com");
            User managerA = createUser("manager-a-uuid", "managera", "Manager A", "managera@example.com");
            User managerB = createUser("manager-b-uuid", "managerb", "Manager B", "managerb@example.com");

            OrgUnit root = createOrgUnit("root-uuid", "Root");
            OrgUnit area = createOrgUnit("area-uuid", "Area", areaManager, root);
            OrgUnit unitA = createOrgUnit("unit-a-uuid", "Unit A", managerA, area);
            OrgUnit unitB = createOrgUnit("unit-b-uuid", "Unit B", managerB, area);

            managerA.setPositions(new ArrayList<>(List.of(makePosition(managerA, unitA))));
            managerB.setPositions(new ArrayList<>(List.of(makePosition(managerB, unitB))));
            managerA.setManagerSubstitutes(new ArrayList<>());
            managerB.setManagerSubstitutes(new ArrayList<>());

            Attestation attUnitA = createOrganisationAttestation(101L, "att-101", "unit-a-uuid", "Unit A");
            Attestation attUnitB = createOrganisationAttestation(102L, "att-102", "unit-b-uuid", "Unit B");
            LocalDate deadline = LocalDate.now().plusDays(7);
            attUnitA.setDeadline(deadline);
            attUnitB.setDeadline(deadline);

            AttestationRun run = createAttestationRun(1L, deadline);
            run.getAttestations().addAll(List.of(attUnitA, attUnitB));

            EmailTemplate template = stubEscalationTemplate();
            when(settingsService.getAttestationEscalationRecipients()).thenReturn(AttestationEscalationRecipients.MANAGER);

            when(attestationRunService.getCurrentRun()).thenReturn(Optional.of(run));
            when(orgUnitDao.findById("unit-a-uuid")).thenReturn(Optional.of(unitA));
            when(orgUnitDao.findById("unit-b-uuid")).thenReturn(Optional.of(unitB));

            // ---- When ---- //
            service.sendEmailsOrganisation(Attestation.AttestationType.ORGANISATION_ATTESTATION,
                    AttestationMail.MailType.ESCALATION_REMINDER, LocalDate.now());

            // ---- Then ---- //
            ArgumentCaptor<AttestationMailRecipient> receiverCaptor = ArgumentCaptor.forClass(AttestationMailRecipient.class);
            ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
            verify(self, times(2)).sendEmailsOrganisation(receiverCaptor.capture(), idsCaptor.capture(), eq(AttestationMail.MailType.ESCALATION_REMINDER), eq(template));

            List<AttestationMailRecipient> receivers = receiverCaptor.getAllValues();
            List<List<Long>> idGroups = idsCaptor.getAllValues();

            assertThat(receivers).allSatisfy(r -> assertThat(r.userUuid()).isEqualTo("area-manager-uuid"));
            assertThat(idGroups).containsExactlyInAnyOrder(List.of(101L), List.of(102L));
        }

    	@SuppressWarnings("unchecked")
        @Test
        @DisplayName("receiver with two OUs under the same manager still gets a single mail listing both OUs")
        void sameManager_receivesSingleMailWithBothOus() {
            // ---- Given ---- //
            User areaManager = createUser("area-manager-uuid", "areamanager", "Area Manager", "areamanager@example.com");
            User managerA = createUser("manager-a-uuid", "managera", "Manager A", "managera@example.com");

            OrgUnit root = createOrgUnit("root-uuid", "Root");
            OrgUnit area = createOrgUnit("area-uuid", "Area", areaManager, root);
            OrgUnit unitA = createOrgUnit("unit-a-uuid", "Unit A", managerA, area);
            OrgUnit unitB = createOrgUnit("unit-b-uuid", "Unit B", managerA, area);

            managerA.setPositions(new ArrayList<>(List.of(makePosition(managerA, unitA))));
            managerA.setManagerSubstitutes(new ArrayList<>());

            Attestation attUnitA = createOrganisationAttestation(101L, "att-101", "unit-a-uuid", "Unit A");
            Attestation attUnitB = createOrganisationAttestation(102L, "att-102", "unit-b-uuid", "Unit B");
            LocalDate deadline = LocalDate.now().plusDays(7);
            attUnitA.setDeadline(deadline);
            attUnitB.setDeadline(deadline);

            AttestationRun run = createAttestationRun(1L, deadline);
            run.getAttestations().addAll(List.of(attUnitA, attUnitB));

            EmailTemplate template = stubEscalationTemplate();
            when(settingsService.getAttestationEscalationRecipients()).thenReturn(AttestationEscalationRecipients.MANAGER);

            when(attestationRunService.getCurrentRun()).thenReturn(Optional.of(run));
            when(orgUnitDao.findById("unit-a-uuid")).thenReturn(Optional.of(unitA));
            when(orgUnitDao.findById("unit-b-uuid")).thenReturn(Optional.of(unitB));

            // ---- When ---- //
            service.sendEmailsOrganisation(Attestation.AttestationType.ORGANISATION_ATTESTATION,
                    AttestationMail.MailType.ESCALATION_REMINDER, LocalDate.now());

            // ---- Then ---- //
            ArgumentCaptor<AttestationMailRecipient> receiverCaptor = ArgumentCaptor.forClass(AttestationMailRecipient.class);
            ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
            verify(self, times(1)).sendEmailsOrganisation(receiverCaptor.capture(), idsCaptor.capture(), eq(AttestationMail.MailType.ESCALATION_REMINDER), eq(template));

            assertThat(receiverCaptor.getValue().userUuid()).isEqualTo("area-manager-uuid");
            assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(101L, 102L);
        }
    }

    @Nested
    @DisplayName("sendEmailsOrganisation(type, mailType, now) — batch robustness")
    class SendEmailsOrganisationBatchRobustness {

        private OrgUnit unitWithManager(String ouUuid, String ouName, User manager) {
            OrgUnit orgUnit = createOrgUnit(ouUuid, ouName, manager, null);
            // Lenient: tests asserting nothing is sent need a resolvable manager, or they would pass for the wrong reason.
            lenient().when(orgUnitDao.findById(ouUuid)).thenReturn(Optional.of(orgUnit));
            return orgUnit;
        }

        private EmailTemplate stubNotificationTemplate() {
            EmailTemplate template = createEmailTemplate(EmailTemplateType.ATTESTATION_NOTIFICATION,
                    "Attestering", "{modtager}, du mangler at attestere {enheder}", true);
            when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_NOTIFICATION)).thenReturn(template);
            return template;
        }

        @Test
        @DisplayName("a receiver that fails does not stop the remaining receivers from being mailed")
        void failingReceiver_doesNotAbortTheBatch() {
            // ---- Given ---- //
            User managerA = createUser("manager-a-uuid", "managera", "Manager A", "managera@example.com");
            User managerB = createUser("manager-b-uuid", "managerb", "Manager B", "managerb@example.com");
            managerA.setManagerSubstitutes(new ArrayList<>());
            managerB.setManagerSubstitutes(new ArrayList<>());
            unitWithManager("unit-a-uuid", "Unit A", managerA);
            unitWithManager("unit-b-uuid", "Unit B", managerB);

            LocalDate deadline = LocalDate.now().plusDays(7);
            Attestation attUnitA = createOrganisationAttestation(101L, "att-101", "unit-a-uuid", "Unit A");
            Attestation attUnitB = createOrganisationAttestation(102L, "att-102", "unit-b-uuid", "Unit B");
            attUnitA.setDeadline(deadline);
            attUnitB.setDeadline(deadline);

            AttestationRun run = createAttestationRun(1L, deadline);
            run.getAttestations().addAll(List.of(attUnitA, attUnitB));

            EmailTemplate template = stubNotificationTemplate();
            when(attestationRunService.getCurrentRun()).thenReturn(Optional.of(run));
            doThrow(new RuntimeException("deadlock")).when(self)
                    .sendEmailsOrganisation(any(), any(), eq(AttestationMail.MailType.INFORMATION), eq(template));

            // ---- When ---- //
            service.sendEmailsOrganisation(Attestation.AttestationType.ORGANISATION_ATTESTATION,
                    AttestationMail.MailType.INFORMATION, LocalDate.now());

            // ---- Then ---- //
            ArgumentCaptor<AttestationMailRecipient> receiverCaptor = ArgumentCaptor.forClass(AttestationMailRecipient.class);
            verify(self, times(2)).sendEmailsOrganisation(receiverCaptor.capture(), any(),
                    eq(AttestationMail.MailType.INFORMATION), eq(template));
            assertThat(receiverCaptor.getAllValues()).extracting(AttestationMailRecipient::userUuid)
                    .containsExactlyInAnyOrder("manager-a-uuid", "manager-b-uuid");
        }

        /** Fixes the end of the notification window: reminder 1 opens 3 days before the deadline. */
        private void stubReminder1Template() {
            EmailTemplate reminder = createEmailTemplate(EmailTemplateType.ATTESTATION_REMINDER1,
                    "Rykker", "{modtager}", true);
            reminder.setDaysBeforeEvent(3);
            when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REMINDER1)).thenReturn(reminder);
        }

        private AttestationRun runWithOneAttestation(LocalDate deadline, LocalDate createdAt) {
            Attestation attestation = createOrganisationAttestation(101L, "att-101", "unit-a-uuid", "Unit A");
            attestation.setDeadline(deadline);
            attestation.setCreatedAt(createdAt);
            AttestationRun run = createAttestationRun(1L, deadline);
            run.getAttestations().add(attestation);
            return run;
        }

        @SuppressWarnings("unchecked")
		@Test
        @DisplayName("a missed send window is caught up until the next mail type opens")
        void missedSendWindow_isCaughtUpWhileTheWindowIsStillOpen() {
            // ---- Given ---- //
            User manager = createUser("manager-uuid", "manager", "Manager", "manager@example.com");
            manager.setManagerSubstitutes(new ArrayList<>());
            unitWithManager("unit-a-uuid", "Unit A", manager);
            // daysBeforeEvent is 7, so this deadline puts the intended send date two days ago
            AttestationRun run = runWithOneAttestation(LocalDate.now().plusDays(5), LocalDate.now().minusDays(9));

            EmailTemplate template = stubNotificationTemplate();
            stubReminder1Template();
            when(attestationRunService.getCurrentRun()).thenReturn(Optional.of(run));

            // ---- When ---- //
            service.sendEmailsOrganisation(Attestation.AttestationType.ORGANISATION_ATTESTATION,
                    AttestationMail.MailType.INFORMATION, LocalDate.now());

            // ---- Then ---- //
            ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
            verify(self, times(1)).sendEmailsOrganisation(any(), idsCaptor.capture(),
                    eq(AttestationMail.MailType.INFORMATION), eq(template));
            assertThat(idsCaptor.getValue()).containsExactly(101L);
        }

        @Test
        @DisplayName("a missed send window is not caught up once the next mail type has opened")
        void missedSendWindow_isNotCaughtUpAfterTheWindowClosed() {
            // ---- Given ---- //
            User manager = createUser("manager-uuid", "manager", "Manager", "manager@example.com");
            manager.setManagerSubstitutes(new ArrayList<>());
            unitWithManager("unit-a-uuid", "Unit A", manager);
            // reminder 1 opens 3 days before the deadline, so the notification window closed yesterday
            AttestationRun run = runWithOneAttestation(LocalDate.now().plusDays(2), LocalDate.now().minusDays(12));

            stubNotificationTemplate();
            stubReminder1Template();
            when(attestationRunService.getCurrentRun()).thenReturn(Optional.of(run));

            // ---- When ---- //
            service.sendEmailsOrganisation(Attestation.AttestationType.ORGANISATION_ATTESTATION,
                    AttestationMail.MailType.INFORMATION, LocalDate.now());

            // ---- Then ---- //
            verify(self, never()).sendEmailsOrganisation(any(), any(), any(), any());
        }

        @Test
        @DisplayName("an attestation added to the run after the send date is not caught up")
        void attestationCreatedAfterSendDate_isNotCaughtUp() {
            // ---- Given ---- //
            User manager = createUser("manager-uuid", "manager", "Manager", "manager@example.com");
            manager.setManagerSubstitutes(new ArrayList<>());
            unitWithManager("unit-a-uuid", "Unit A", manager);
            // the send date was two days ago, the attestation was created yesterday
            AttestationRun run = runWithOneAttestation(LocalDate.now().plusDays(5), LocalDate.now().minusDays(1));

            stubNotificationTemplate();
            stubReminder1Template();
            when(attestationRunService.getCurrentRun()).thenReturn(Optional.of(run));

            // ---- When ---- //
            service.sendEmailsOrganisation(Attestation.AttestationType.ORGANISATION_ATTESTATION,
                    AttestationMail.MailType.INFORMATION, LocalDate.now());

            // ---- Then ---- //
            verify(self, never()).sendEmailsOrganisation(any(), any(), any(), any());
        }

        @Test
        @DisplayName("a receiver that failed is caught up although a co-receiver on the same attestation was mailed")
        void failedReceiver_isCaughtUpWhenCoReceiverWasMailed() {
            // ---- Given ---- //
            User manager = createUser("manager-uuid", "manager", "Manager", "manager@example.com");
            User substitute = createUser("substitute-uuid", "substitute", "Substitute", "substitute@example.com");
            OrgUnit unit = unitWithManager("unit-a-uuid", "Unit A", manager);
            ManagerSubstitute managerSubstitute = new ManagerSubstitute();
            managerSubstitute.setSubstitute(substitute);
            managerSubstitute.setOrgUnit(unit);
            manager.setManagerSubstitutes(new ArrayList<>(List.of(managerSubstitute)));

            AttestationRun run = runWithOneAttestation(LocalDate.now().plusDays(7), LocalDate.now());
            Attestation attestation = run.getAttestations().iterator().next();
            AttestationMail mailToManager = AttestationMail.builder()
                    .attestation(attestation)
                    .emailTemplateType(EmailTemplateType.ATTESTATION_NOTIFICATION)
                    .emailType(AttestationMail.MailType.INFORMATION)
                    .build();
            mailToManager.getReceivers().add(AttestationMailReceiver.builder().userUuid("manager-uuid").build());
            attestation.getMails().add(mailToManager);

            EmailTemplate template = stubNotificationTemplate();
            when(attestationRunService.getCurrentRun()).thenReturn(Optional.of(run));

            // ---- When ---- //
            service.sendEmailsOrganisation(Attestation.AttestationType.ORGANISATION_ATTESTATION,
                    AttestationMail.MailType.INFORMATION, LocalDate.now());

            // ---- Then ---- //
            ArgumentCaptor<AttestationMailRecipient> receiverCaptor = ArgumentCaptor.forClass(AttestationMailRecipient.class);
            verify(self, times(1)).sendEmailsOrganisation(receiverCaptor.capture(), eq(List.of(101L)),
                    eq(AttestationMail.MailType.INFORMATION), eq(template));
            assertThat(receiverCaptor.getValue().userUuid()).isEqualTo("substitute-uuid");
        }
    }
}
