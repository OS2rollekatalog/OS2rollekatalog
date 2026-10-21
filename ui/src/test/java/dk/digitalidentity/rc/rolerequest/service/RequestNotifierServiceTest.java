package dk.digitalidentity.rc.rolerequest.service;

import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import dk.digitalidentity.rc.rolerequest.model.permission.ApproverPermission;
import dk.digitalidentity.rc.rolerequest.service.permission.ApprovableRequestService;
import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.AuthorizationManager;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.service.OrgUnitService.EffectiveApprover;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.rolerequest.dao.RoleRequestDao;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createEmailTemplate;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createUserRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestNotifierService — SYSTEMRESPONSIBLE email routing")
class RequestNotifierServiceTest {

    @Mock private EmailTemplateService emailTemplateService;
    @Mock private EmailQueueService emailQueueService;
    @Mock private SettingsService settingsService;
    @Mock private RoleRequestDao roleRequestDao;
    @Mock private UserRoleService userRoleService;
    @Mock private SystemRoleService systemRoleService;
    @Mock private OrgUnitService orgUnitService;
    @Mock private ItSystemService itSystemService;
    @Mock private RequestAuthorizedRoleService requestAuthorizedRoleService;
    @Mock private AssignmentService assignmentService;
    @Mock private ApprovableRequestService approvableRequestService;
    @Mock private ManagerSubstituteService managerSubstituteService;

    private static final long REQUEST_ID = 1L;

    @InjectMocks
    private RequestNotifierService service;

    @BeforeEach
    void wireCandidates() {
        ReflectionTestUtils.setField(service, "approverCandidates", new ApproverCandidates(
                settingsService, orgUnitService, managerSubstituteService, itSystemService, systemRoleService, userRoleService, assignmentService));
    }

    // ---- Helpers ---- //

    private static User makeUser(String uuid, String email, String name) {
        User user = createUser(uuid, uuid, name, email);
        return user;
    }

    private static EmailTemplate enabledTemplate() {
        return createEmailTemplate(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS, "{modtager}", "{modtager} ({antal})", true);
    }

    private static RoleRequest systemResponsibleRequest(ItSystem itSystem) {
        UserRole userRole = createUserRole(null, itSystem, List.of(ApprovableBy.SYSTEMRESPONSIBLE));
        return RoleRequest.builder()
                .id(REQUEST_ID)
                .userRole(userRole)
                .approverOption(ApproverPermission.of(List.of(ApprovableBy.SYSTEMRESPONSIBLE)))
                .status(RequestApproveStatus.REQUESTED)
                .emailSent(false)
                .build();
    }

    @Nested
    @DisplayName("sendMailToRoleAssignerOnce — SYSTEMRESPONSIBLE")
    class SendMailSystemResponsible {

        @Test
        @DisplayName("sends one email per attestation responsible when system has multiple")
        void multipleOwners_eachReceivesEmail() {
            User owner1 = makeUser("uuid-1", "owner1@example.com", "Owner One");
            User owner2 = makeUser("uuid-2", "owner2@example.com", "Owner Two");

            ItSystem itSystem = createItSystem("sys-uuid", List.of(ApprovableBy.SYSTEMRESPONSIBLE));

            RoleRequest request = systemResponsibleRequest(itSystem);

            when(roleRequestDao.findPendingForApprovalEmails(false)).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(itSystemService.getAttestationResponsibles(itSystem)).thenReturn(List.of(owner1, owner2));
            when(approvableRequestService.emailRecipientAmong(eq(owner1), anyCollection())).thenReturn(Set.of(REQUEST_ID));
            when(approvableRequestService.emailRecipientAmong(eq(owner2), anyCollection())).thenReturn(Set.of(REQUEST_ID));
            when(emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS)).thenReturn(enabledTemplate());
            when(roleRequestDao.saveAll(any())).thenReturn(List.of(request));

            service.sendMailToRoleAssignerOnce();

            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailQueueService, times(2)).queueEmail(emailCaptor.capture(), any(), any(), any(), any(), any());
            assertThat(emailCaptor.getAllValues())
                    .containsExactlyInAnyOrder("owner1@example.com", "owner2@example.com");
        }

        @Test
        @DisplayName("sends one email when system has a single attestation responsible")
        void singleOwner_receivesEmail() {
            User owner = makeUser("uuid-1", "owner@example.com", "Owner");

            ItSystem itSystem = createItSystem("sys-uuid", List.of(ApprovableBy.SYSTEMRESPONSIBLE));

            RoleRequest request = systemResponsibleRequest(itSystem);

            when(roleRequestDao.findPendingForApprovalEmails(false)).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(itSystemService.getAttestationResponsibles(itSystem)).thenReturn(List.of(owner));
            when(approvableRequestService.emailRecipientAmong(eq(owner), anyCollection())).thenReturn(Set.of(REQUEST_ID));
            when(emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS)).thenReturn(enabledTemplate());
            when(roleRequestDao.saveAll(any())).thenReturn(List.of(request));

            service.sendMailToRoleAssignerOnce();

            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailQueueService, times(1)).queueEmail(emailCaptor.capture(), any(), any(), any(), any(), any());
            assertThat(emailCaptor.getValue()).isEqualTo("owner@example.com");
        }

        @Test
        @DisplayName("sends no email and falls through to servicedesk when system has no responsibles")
        void noOwners_noEmailSentToOwners() {
            ItSystem itSystem = createItSystem("sys-uuid", List.of(ApprovableBy.SYSTEMRESPONSIBLE));

            RoleRequest request = systemResponsibleRequest(itSystem);

            when(roleRequestDao.findPendingForApprovalEmails(false)).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(settingsService.getRequestApproveServicedeskEmail()).thenReturn(null);

            service.sendMailToRoleAssignerOnce();

            verify(emailQueueService, times(0)).queueEmail(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("skips responsibles with null email")
        void ownerWithNullEmail_skipped() {
            User ownerWithEmail = makeUser("uuid-1", "owner@example.com", "Owner");
            User ownerNoEmail = makeUser("uuid-2", null, "No Email Owner");

            ItSystem itSystem = createItSystem("sys-uuid", List.of(ApprovableBy.SYSTEMRESPONSIBLE));

            RoleRequest request = systemResponsibleRequest(itSystem);

            when(roleRequestDao.findPendingForApprovalEmails(false)).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(itSystemService.getAttestationResponsibles(itSystem)).thenReturn(List.of(ownerWithEmail, ownerNoEmail));
            when(approvableRequestService.emailRecipientAmong(eq(ownerWithEmail), anyCollection())).thenReturn(Set.of(REQUEST_ID));
            when(emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS)).thenReturn(enabledTemplate());
            when(roleRequestDao.saveAll(any())).thenReturn(List.of(request));

            service.sendMailToRoleAssignerOnce();

            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailQueueService, times(1)).queueEmail(emailCaptor.capture(), any(), any(), any(), any(), any());
            assertThat(emailCaptor.getValue()).isEqualTo("owner@example.com");
        }
    }

    @Nested
    @DisplayName("sendMailToRoleAssignerOnce — AUTHRESPONSIBLE")
    class SendMailAuthResponsible {

        @Test
        @DisplayName("does not email an authorization manager who cannot approve their own request")
        void requesterIsAuthManager_excluded() {
            User requesterAuthManager = makeUser("uuid-1", "requester@example.com", "Requester");
            User otherAuthManager = makeUser("uuid-2", "other@example.com", "Other");
            User receiver = makeUser("uuid-receiver", "receiver@example.com", "Receiver");

            OrgUnit orgUnit = createOrgUnit("ou-1", null);
            AuthorizationManager am1 = new AuthorizationManager();
            am1.setUser(requesterAuthManager);
            AuthorizationManager am2 = new AuthorizationManager();
            am2.setUser(otherAuthManager);
            orgUnit.setAuthorizationManagers(List.of(am1, am2));

            Position receiverPosition = new Position();
            receiverPosition.setOrgUnit(orgUnit);
            receiver.setPositions(List.of(receiverPosition));

            UserRole userRole = createUserRole(null, createItSystem("sys-uuid", List.of(ApprovableBy.AUTHRESPONSIBLE)), List.of(ApprovableBy.AUTHRESPONSIBLE));
            RoleRequest request = RoleRequest.builder()
                    .id(REQUEST_ID)
                    .userRole(userRole)
                    .orgUnit(orgUnit)
                    .receiver(receiver)
                    .requester(requesterAuthManager)
                    .approverOption(ApproverPermission.of(List.of(ApprovableBy.AUTHRESPONSIBLE)))
                    .status(RequestApproveStatus.REQUESTED)
                    .emailSent(false)
                    .build();

            when(roleRequestDao.findPendingForApprovalEmails(false)).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(approvableRequestService.emailRecipientAmong(eq(requesterAuthManager), anyCollection())).thenReturn(Set.of());
            when(approvableRequestService.emailRecipientAmong(eq(otherAuthManager), anyCollection())).thenReturn(Set.of(REQUEST_ID));
            when(emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS)).thenReturn(enabledTemplate());
            when(roleRequestDao.saveAll(any())).thenReturn(List.of(request));

            service.sendMailToRoleAssignerOnce();

            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailQueueService, times(1)).queueEmail(emailCaptor.capture(), any(), any(), any(), any(), any());
            assertThat(emailCaptor.getValue()).isEqualTo("other@example.com");
        }

        @Test
        @DisplayName("does not email a deleted authorization manager")
        void deletedAuthManager_excluded() {
            User deletedAuthManager = makeUser("uuid-1", "deleted@example.com", "Deleted");
            deletedAuthManager.setDeleted(true);
            User receiver = makeUser("uuid-receiver", "receiver@example.com", "Receiver");

            OrgUnit orgUnit = createOrgUnit("ou-1", null);
            AuthorizationManager am = new AuthorizationManager();
            am.setUser(deletedAuthManager);
            orgUnit.setAuthorizationManagers(List.of(am));

            Position receiverPosition = new Position();
            receiverPosition.setOrgUnit(orgUnit);
            receiver.setPositions(List.of(receiverPosition));

            UserRole userRole = createUserRole(null, createItSystem("sys-uuid", List.of(ApprovableBy.AUTHRESPONSIBLE)), List.of(ApprovableBy.AUTHRESPONSIBLE));
            RoleRequest request = RoleRequest.builder()
                    .id(REQUEST_ID)
                    .userRole(userRole)
                    .orgUnit(orgUnit)
                    .receiver(receiver)
                    .approverOption(ApproverPermission.of(List.of(ApprovableBy.AUTHRESPONSIBLE)))
                    .status(RequestApproveStatus.REQUESTED)
                    .emailSent(false)
                    .build();

            when(roleRequestDao.findPendingForApprovalEmails(false)).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(settingsService.getRequestApproveServicedeskEmail()).thenReturn(null);

            service.sendMailToRoleAssignerOnce();

            verify(emailQueueService, times(0)).queueEmail(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("enumerates auth managers from receiver's position OU, not the request's OU")
        void receiverPositionOuDiffersFromRequestOu_enumeratesFromReceiverOu() {
            User realApprover = makeUser("uuid-real", "real@example.com", "Real Approver");
            User receiver = makeUser("uuid-receiver", "receiver@example.com", "Receiver");

            OrgUnit requestOrgUnit = createOrgUnit("ou-request", null);
            requestOrgUnit.setAuthorizationManagers(List.of());

            OrgUnit receiverPositionOrgUnit = createOrgUnit("ou-receiver-position", null);
            AuthorizationManager am = new AuthorizationManager();
            am.setUser(realApprover);
            receiverPositionOrgUnit.setAuthorizationManagers(List.of(am));

            Position receiverPosition = new Position();
            receiverPosition.setOrgUnit(receiverPositionOrgUnit);
            receiver.setPositions(List.of(receiverPosition));

            UserRole userRole = createUserRole(null, createItSystem("sys-uuid", List.of(ApprovableBy.AUTHRESPONSIBLE)), List.of(ApprovableBy.AUTHRESPONSIBLE));
            RoleRequest request = RoleRequest.builder()
                    .id(REQUEST_ID)
                    .userRole(userRole)
                    .orgUnit(requestOrgUnit)
                    .receiver(receiver)
                    .approverOption(ApproverPermission.of(List.of(ApprovableBy.AUTHRESPONSIBLE)))
                    .status(RequestApproveStatus.REQUESTED)
                    .emailSent(false)
                    .build();

            when(roleRequestDao.findPendingForApprovalEmails(false)).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(approvableRequestService.emailRecipientAmong(eq(realApprover), anyCollection())).thenReturn(Set.of(REQUEST_ID));
            when(emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS)).thenReturn(enabledTemplate());
            when(roleRequestDao.saveAll(any())).thenReturn(List.of(request));

            service.sendMailToRoleAssignerOnce();

            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailQueueService, times(1)).queueEmail(emailCaptor.capture(), any(), any(), any(), any(), any());
            assertThat(emailCaptor.getValue()).isEqualTo("real@example.com");
        }
    }

    @Nested
    @DisplayName("sendMailToRoleAssignerOnce — MANAGERORSUBSTITUTE")
    class SendMailManagerOrSubstitute {

        @Test
        @DisplayName("walks up to the parent OU manager when canApprove resolves there")
        void parentOuManager_receivesEmail() {
            User parentManager = makeUser("uuid-parent", "parent@example.com", "Parent Manager");
            OrgUnit parentOu = createOrgUnit("ou-parent", null, parentManager);
            OrgUnit childOu = createOrgUnit("ou-child", parentOu);

            UserRole userRole = createUserRole(null, createItSystem("sys-uuid", List.of(ApprovableBy.MANAGERORSUBSTITUTE)), List.of(ApprovableBy.MANAGERORSUBSTITUTE));
            RoleRequest request = RoleRequest.builder()
                    .id(REQUEST_ID)
                    .userRole(userRole)
                    .orgUnit(childOu)
                    .approverOption(ApproverPermission.of(List.of(ApprovableBy.MANAGERORSUBSTITUTE)))
                    .status(RequestApproveStatus.REQUESTED)
                    .emailSent(false)
                    .build();

            when(roleRequestDao.findPendingForApprovalEmails(false)).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(orgUnitService.getEffectiveApprover(childOu, null)).thenReturn(new EffectiveApprover(parentManager, parentOu));
            when(managerSubstituteService.getSubstitutesForOrgUnit(parentOu)).thenReturn(List.of());
            when(approvableRequestService.emailRecipientAmong(eq(parentManager), anyCollection())).thenReturn(Set.of(REQUEST_ID));
            when(emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS)).thenReturn(enabledTemplate());
            when(roleRequestDao.saveAll(any())).thenReturn(List.of(request));

            service.sendMailToRoleAssignerOnce();

            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailQueueService, times(1)).queueEmail(emailCaptor.capture(), any(), any(), any(), any(), any());
            assertThat(emailCaptor.getValue()).isEqualTo("parent@example.com");
        }

        @Test
        @DisplayName("does not email the receiver when they are the manager of their own OU")
        void receiverIsManager_excluded() {
            User receiverManager = makeUser("uuid-receiver", "receiver@example.com", "Receiver Manager");
            OrgUnit orgUnit = createOrgUnit("ou-1", null, receiverManager);

            UserRole userRole = createUserRole(null, createItSystem("sys-uuid", List.of(ApprovableBy.MANAGERORSUBSTITUTE)), List.of(ApprovableBy.MANAGERORSUBSTITUTE));
            RoleRequest request = RoleRequest.builder()
                    .id(REQUEST_ID)
                    .userRole(userRole)
                    .orgUnit(orgUnit)
                    .receiver(receiverManager)
                    .approverOption(ApproverPermission.of(List.of(ApprovableBy.MANAGERORSUBSTITUTE)))
                    .status(RequestApproveStatus.REQUESTED)
                    .emailSent(false)
                    .build();

            when(roleRequestDao.findPendingForApprovalEmails(false)).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(orgUnitService.getEffectiveApprover(orgUnit, receiverManager)).thenReturn(null);
            when(settingsService.getRequestApproveServicedeskEmail()).thenReturn(null);

            service.sendMailToRoleAssignerOnce();

            verify(emailQueueService, times(0)).queueEmail(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("sendMailToRoleAssignerOnce — AUTHORIZED")
    class SendMailAuthorized {

        private RoleRequest authorizedRequest(ItSystem itSystem, UserRole userRole) {
            return RoleRequest.builder()
                    .id(REQUEST_ID)
                    .userRole(userRole)
                    .approverOption(ApproverPermission.of(List.of(ApprovableBy.AUTHORIZED)))
                    .status(RequestApproveStatus.REQUESTED)
                    .emailSent(false)
                    .build();
        }

        private void stubAuthorizedRoleLookup(ItSystem roleCatalogueSystem, SystemRole requestAuthorizedRole, List<User> assignedUsers) {
            when(itSystemService.getFirstByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER)).thenReturn(roleCatalogueSystem);
            when(systemRoleService.getFirstByIdentifierAndItSystemId(Constants.ROLE_REQUESTAUTHORIZED, roleCatalogueSystem.getId())).thenReturn(requestAuthorizedRole);
            UserRole authorizedUserRole = new UserRole();
            when(userRoleService.findAllBySystemRole(requestAuthorizedRole)).thenReturn(Set.of(authorizedUserRole));

            Set<CurrentAssignment> assignments = new java.util.HashSet<>();
            for (User user : assignedUsers) {
                CurrentAssignment assignment = new CurrentAssignment();
                assignment.setUser(user);
                assignments.add(assignment);
            }
            when(assignmentService.getActiveByUserRole(authorizedUserRole)).thenReturn(assignments);
        }

        @Test
        @DisplayName("does not email a bemyndiget user who cannot approve their own request")
        void requesterIsAuthorized_excluded() {
            User requesterAuthorized = makeUser("uuid-1", "requester@example.com", "Requester");
            User otherAuthorized = makeUser("uuid-2", "other@example.com", "Other");

            ItSystem itSystem = createItSystem("sys-uuid", List.of(ApprovableBy.AUTHORIZED));
            itSystem.setId(1L);
            UserRole userRole = createUserRole(null, itSystem, List.of(ApprovableBy.AUTHORIZED));
            RoleRequest request = authorizedRequest(itSystem, userRole);
            request.setRequester(requesterAuthorized);

            when(roleRequestDao.findPendingForApprovalEmails(false)).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());

            ItSystem roleCatalogueSystem = createItSystem("rc-uuid", List.of());
            roleCatalogueSystem.setId(99L);
            SystemRole requestAuthorizedRole = new SystemRole();
            stubAuthorizedRoleLookup(roleCatalogueSystem, requestAuthorizedRole, List.of(requesterAuthorized, otherAuthorized));

            when(approvableRequestService.emailRecipientAmong(eq(requesterAuthorized), anyCollection())).thenReturn(Set.of());
            when(approvableRequestService.emailRecipientAmong(eq(otherAuthorized), anyCollection())).thenReturn(Set.of(REQUEST_ID));
            when(emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS)).thenReturn(enabledTemplate());
            when(roleRequestDao.saveAll(any())).thenReturn(List.of(request));

            service.sendMailToRoleAssignerOnce();

            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailQueueService, times(1)).queueEmail(emailCaptor.capture(), any(), any(), any(), any(), any());
            assertThat(emailCaptor.getValue()).isEqualTo("other@example.com");
        }

        @Test
        @DisplayName("does not email the receiver when they hold the AUTHORIZED role")
        void receiverIsAuthorized_excluded() {
            User receiverAuthorized = makeUser("uuid-1", "receiver@example.com", "Receiver");

            ItSystem itSystem = createItSystem("sys-uuid", List.of(ApprovableBy.AUTHORIZED));
            itSystem.setId(1L);
            UserRole userRole = createUserRole(null, itSystem, List.of(ApprovableBy.AUTHORIZED));
            RoleRequest request = authorizedRequest(itSystem, userRole);
            request.setReceiver(receiverAuthorized);

            when(roleRequestDao.findPendingForApprovalEmails(false)).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());

            ItSystem roleCatalogueSystem = createItSystem("rc-uuid", List.of());
            roleCatalogueSystem.setId(99L);
            SystemRole requestAuthorizedRole = new SystemRole();
            stubAuthorizedRoleLookup(roleCatalogueSystem, requestAuthorizedRole, List.of(receiverAuthorized));

            when(approvableRequestService.emailRecipientAmong(eq(receiverAuthorized), anyCollection())).thenReturn(Set.of());
            when(settingsService.getRequestApproveServicedeskEmail()).thenReturn(null);

            service.sendMailToRoleAssignerOnce();

            verify(emailQueueService, times(0)).queueEmail(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("sendMailToRoleAssignerOnce — 10 recipient cap")
    class RecipientCap {

        @Test
        @DisplayName("truncates to exactly 10 recipients, preserving insertion order")
        void capsAtTen_preservingInsertionOrder() {
            ItSystem itSystem = createItSystem("sys-uuid", List.of(ApprovableBy.SYSTEMRESPONSIBLE));
            UserRole userRole = createUserRole(null, itSystem, List.of(ApprovableBy.SYSTEMRESPONSIBLE));
            RoleRequest request = RoleRequest.builder()
                    .id(REQUEST_ID)
                    .userRole(userRole)
                    .approverOption(ApproverPermission.of(List.of(ApprovableBy.SYSTEMRESPONSIBLE)))
                    .status(RequestApproveStatus.REQUESTED)
                    .emailSent(false)
                    .build();

            // 12 candidates named in reverse-alphabetical order: owner11..owner00. If the cap sorted by
            // email before truncating (the old behavior), owner00..owner09 would survive; asserting the
            // FIRST 10 in insertion order (owner11..owner02) instead pins down that insertion order wins.
            List<User> owners = new ArrayList<>();
            for (int i = 11; i >= 0; i--) {
                String suffix = String.format("%02d", i);
                owners.add(makeUser("uuid-" + suffix, "owner" + suffix + "@example.com", "Owner " + suffix));
            }

            when(roleRequestDao.findPendingForApprovalEmails(false)).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(itSystemService.getAttestationResponsibles(itSystem)).thenReturn(owners);
            for (User owner : owners) {
                when(approvableRequestService.emailRecipientAmong(eq(owner), anyCollection())).thenReturn(Set.of(REQUEST_ID));
            }
            when(emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS)).thenReturn(enabledTemplate());
            when(roleRequestDao.saveAll(any())).thenReturn(List.of(request));

            service.sendMailToRoleAssignerOnce();

            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailQueueService, times(10)).queueEmail(emailCaptor.capture(), any(), any(), any(), any(), any());
            assertThat(emailCaptor.getAllValues())
                    .containsExactly(
                            "owner11@example.com", "owner10@example.com", "owner09@example.com", "owner08@example.com",
                            "owner07@example.com", "owner06@example.com", "owner05@example.com", "owner04@example.com",
                            "owner03@example.com", "owner02@example.com");
        }

        @Test
        @DisplayName("global settings email keeps precedence over the cap even when candidate emails sort earlier")
        void globalSettingsEmail_survivesCapRegardlessOfSortOrder() {
            // A separate ApprovableBy.AUTHORIZED option resolves to a settings-configured global email
            // ("servicedesk@..."), inserted FIRST. SYSTEMRESPONSIBLE then contributes 10 candidates whose
            // emails ("aaaa...") sort alphabetically before it. The old sort-then-limit(10) implementation
            // would drop the configured global email in favor of these alphabetically-earlier candidates.
            ItSystem itSystem = createItSystem("sys-uuid", List.of(ApprovableBy.SYSTEMRESPONSIBLE));
            UserRole userRole = createUserRole(null, itSystem, List.of(ApprovableBy.AUTHORIZED, ApprovableBy.SYSTEMRESPONSIBLE));
            RoleRequest request = RoleRequest.builder()
                    .id(REQUEST_ID)
                    .userRole(userRole)
                    .approverOption(ApproverPermission.of(List.of(ApprovableBy.AUTHORIZED, ApprovableBy.SYSTEMRESPONSIBLE)))
                    .status(RequestApproveStatus.REQUESTED)
                    .emailSent(false)
                    .build();

            List<User> owners = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                String suffix = String.format("%02d", i);
                owners.add(makeUser("uuid-" + suffix, "aaaa" + suffix + "@example.com", "Owner " + suffix));
            }

            when(roleRequestDao.findPendingForApprovalEmails(false)).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails())
                    .thenReturn(java.util.Map.of(ApprovableBy.AUTHORIZED, "servicedesk@example.com"));
            when(itSystemService.getAttestationResponsibles(itSystem)).thenReturn(owners);
            for (User owner : owners) {
                when(approvableRequestService.emailRecipientAmong(eq(owner), anyCollection())).thenReturn(Set.of(REQUEST_ID));
            }
            when(emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS)).thenReturn(enabledTemplate());
            when(roleRequestDao.saveAll(any())).thenReturn(List.of(request));

            service.sendMailToRoleAssignerOnce();

            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailQueueService, times(10)).queueEmail(emailCaptor.capture(), any(), any(), any(), any(), any());
            assertThat(emailCaptor.getAllValues()).contains("servicedesk@example.com");
        }
    }
    @Nested
    @DisplayName("sendMailToRoleAssignerOnce — batching")
    class Batching {

        private RoleRequest request(long id, ItSystem itSystem, ApprovableBy approverType) {
            return RoleRequest.builder()
                    .id(id)
                    .userRole(createUserRole(null, itSystem, List.of(approverType)))
                    .approverOption(ApproverPermission.of(List.of(approverType)))
                    .status(RequestApproveStatus.REQUESTED)
                    .emailSent(false)
                    .build();
        }

        @Test
        @DisplayName("asks each candidate once for the whole batch, and looks up the authorized users once")
        void oncePerCandidate() {
            // ---- Given ---- //
            User authorized1 = makeUser("uuid-1", "one@example.com", "One");
            User authorized2 = makeUser("uuid-2", "two@example.com", "Two");
            ItSystem itSystem = createItSystem("sys-uuid", List.of(ApprovableBy.AUTHORIZED));
            List<RoleRequest> requests = List.of(
                    request(1L, itSystem, ApprovableBy.AUTHORIZED),
                    request(2L, itSystem, ApprovableBy.AUTHORIZED),
                    request(3L, itSystem, ApprovableBy.AUTHORIZED));

            when(roleRequestDao.findPendingForApprovalEmails(false)).thenReturn(requests);
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            ItSystem roleCatalogueSystem = createItSystem("rc-uuid", List.of());
            roleCatalogueSystem.setId(99L);
            SystemRole requestAuthorizedRole = new SystemRole();
            when(itSystemService.getFirstByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER)).thenReturn(roleCatalogueSystem);
            when(systemRoleService.getFirstByIdentifierAndItSystemId(Constants.ROLE_REQUESTAUTHORIZED, 99L)).thenReturn(requestAuthorizedRole);
            UserRole authorizedUserRole = new UserRole();
            when(userRoleService.findAllBySystemRole(requestAuthorizedRole)).thenReturn(Set.of(authorizedUserRole));
            CurrentAssignment assignment1 = new CurrentAssignment();
            assignment1.setUser(authorized1);
            CurrentAssignment assignment2 = new CurrentAssignment();
            assignment2.setUser(authorized2);
            when(assignmentService.getActiveByUserRole(authorizedUserRole)).thenReturn(Set.of(assignment1, assignment2));
            when(approvableRequestService.emailRecipientAmong(eq(authorized1), anyCollection())).thenReturn(Set.of(1L, 2L, 3L));
            when(approvableRequestService.emailRecipientAmong(eq(authorized2), anyCollection())).thenReturn(Set.of(1L, 2L, 3L));
            when(emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS)).thenReturn(enabledTemplate());

            // ---- When ---- //
            service.sendMailToRoleAssignerOnce();

            // ---- Then ---- //
            verify(approvableRequestService, times(1)).emailRecipientAmong(eq(authorized1), anyCollection());
            verify(approvableRequestService, times(1)).emailRecipientAmong(eq(authorized2), anyCollection());
            verify(userRoleService, times(1)).findAllBySystemRole(requestAuthorizedRole);
        }

        @Test
        @DisplayName("does not email a candidate about a request they cannot approve, even if they can approve another one")
        void candidateOnlyForApprovableRequests() {
            // ---- Given ---- //
            User owner = makeUser("uuid-1", "owner@example.com", "Owner");
            ItSystem ownedSystem = createItSystem("owned-uuid", List.of(ApprovableBy.SYSTEMRESPONSIBLE));
            ItSystem otherSystem = createItSystem("other-uuid", List.of(ApprovableBy.SYSTEMRESPONSIBLE));
            RoleRequest ownedRequest = request(1L, ownedSystem, ApprovableBy.SYSTEMRESPONSIBLE);
            RoleRequest otherRequest = request(2L, otherSystem, ApprovableBy.SYSTEMRESPONSIBLE);

            when(roleRequestDao.findPendingForApprovalEmails(false)).thenReturn(List.of(ownedRequest, otherRequest));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(itSystemService.getAttestationResponsibles(ownedSystem)).thenReturn(List.of(owner));
            when(itSystemService.getAttestationResponsibles(otherSystem)).thenReturn(List.of());
            when(approvableRequestService.emailRecipientAmong(eq(owner), anyCollection())).thenReturn(Set.of(2L));
            when(emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS)).thenReturn(enabledTemplate());

            // ---- When ---- //
            service.sendMailToRoleAssignerOnce();

            // ---- Then ---- //
            verify(emailQueueService, times(0)).queueEmail(eq("owner@example.com"), any(), any(), any(), any(), any());
        }
    }
}
