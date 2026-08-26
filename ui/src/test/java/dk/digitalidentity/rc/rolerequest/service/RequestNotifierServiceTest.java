package dk.digitalidentity.rc.rolerequest.service;

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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
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
    @Mock private RequestApproverResolver requestApproverResolver;
    @Mock private ManagerSubstituteService managerSubstituteService;

    @InjectMocks
    private RequestNotifierService service;

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
                .userRole(userRole)
                .approverOption(List.of(ApprovableBy.SYSTEMRESPONSIBLE))
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

            when(roleRequestDao.findByStatusInAndEmailSent(anyCollection(), anyBoolean())).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(requestApproverResolver.resolveEffectiveOptions(request)).thenReturn(List.of(ApprovableBy.SYSTEMRESPONSIBLE));
            when(itSystemService.getAttestationResponsibles(itSystem)).thenReturn(List.of(owner1, owner2));
            when(requestApproverResolver.canApprove(request, owner1)).thenReturn(true);
            when(requestApproverResolver.canApprove(request, owner2)).thenReturn(true);
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

            when(roleRequestDao.findByStatusInAndEmailSent(anyCollection(), anyBoolean())).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(requestApproverResolver.resolveEffectiveOptions(request)).thenReturn(List.of(ApprovableBy.SYSTEMRESPONSIBLE));
            when(itSystemService.getAttestationResponsibles(itSystem)).thenReturn(List.of(owner));
            when(requestApproverResolver.canApprove(request, owner)).thenReturn(true);
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

            when(roleRequestDao.findByStatusInAndEmailSent(anyCollection(), anyBoolean())).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(requestApproverResolver.resolveEffectiveOptions(request)).thenReturn(List.of(ApprovableBy.SYSTEMRESPONSIBLE));
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

            when(roleRequestDao.findByStatusInAndEmailSent(anyCollection(), anyBoolean())).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(requestApproverResolver.resolveEffectiveOptions(request)).thenReturn(List.of(ApprovableBy.SYSTEMRESPONSIBLE));
            when(itSystemService.getAttestationResponsibles(itSystem)).thenReturn(List.of(ownerWithEmail, ownerNoEmail));
            when(requestApproverResolver.canApprove(request, ownerWithEmail)).thenReturn(true);
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
                    .userRole(userRole)
                    .orgUnit(orgUnit)
                    .receiver(receiver)
                    .requester(requesterAuthManager)
                    .approverOption(List.of(ApprovableBy.AUTHRESPONSIBLE))
                    .status(RequestApproveStatus.REQUESTED)
                    .emailSent(false)
                    .build();

            when(roleRequestDao.findByStatusInAndEmailSent(anyCollection(), anyBoolean())).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(requestApproverResolver.resolveEffectiveOptions(request)).thenReturn(List.of(ApprovableBy.AUTHRESPONSIBLE));
            when(requestApproverResolver.canApprove(request, requesterAuthManager)).thenReturn(false);
            when(requestApproverResolver.canApprove(request, otherAuthManager)).thenReturn(true);
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
                    .userRole(userRole)
                    .orgUnit(orgUnit)
                    .receiver(receiver)
                    .approverOption(List.of(ApprovableBy.AUTHRESPONSIBLE))
                    .status(RequestApproveStatus.REQUESTED)
                    .emailSent(false)
                    .build();

            when(roleRequestDao.findByStatusInAndEmailSent(anyCollection(), anyBoolean())).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(requestApproverResolver.resolveEffectiveOptions(request)).thenReturn(List.of(ApprovableBy.AUTHRESPONSIBLE));
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
                    .userRole(userRole)
                    .orgUnit(requestOrgUnit)
                    .receiver(receiver)
                    .approverOption(List.of(ApprovableBy.AUTHRESPONSIBLE))
                    .status(RequestApproveStatus.REQUESTED)
                    .emailSent(false)
                    .build();

            when(roleRequestDao.findByStatusInAndEmailSent(anyCollection(), anyBoolean())).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(requestApproverResolver.resolveEffectiveOptions(request)).thenReturn(List.of(ApprovableBy.AUTHRESPONSIBLE));
            when(requestApproverResolver.canApprove(request, realApprover)).thenReturn(true);
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
                    .userRole(userRole)
                    .orgUnit(childOu)
                    .approverOption(List.of(ApprovableBy.MANAGERORSUBSTITUTE))
                    .status(RequestApproveStatus.REQUESTED)
                    .emailSent(false)
                    .build();

            when(roleRequestDao.findByStatusInAndEmailSent(anyCollection(), anyBoolean())).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(requestApproverResolver.resolveEffectiveOptions(request)).thenReturn(List.of(ApprovableBy.MANAGERORSUBSTITUTE));
            when(orgUnitService.getEffectiveApprover(childOu, null)).thenReturn(new EffectiveApprover(parentManager, parentOu));
            when(managerSubstituteService.getSubstitutesForOrgUnit(parentOu)).thenReturn(List.of());
            when(requestApproverResolver.canApprove(request, parentManager)).thenReturn(true);
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
                    .userRole(userRole)
                    .orgUnit(orgUnit)
                    .receiver(receiverManager)
                    .approverOption(List.of(ApprovableBy.MANAGERORSUBSTITUTE))
                    .status(RequestApproveStatus.REQUESTED)
                    .emailSent(false)
                    .build();

            when(roleRequestDao.findByStatusInAndEmailSent(anyCollection(), anyBoolean())).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(requestApproverResolver.resolveEffectiveOptions(request)).thenReturn(List.of(ApprovableBy.MANAGERORSUBSTITUTE));
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
                    .userRole(userRole)
                    .approverOption(List.of(ApprovableBy.AUTHORIZED))
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

            when(roleRequestDao.findByStatusInAndEmailSent(anyCollection(), anyBoolean())).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(requestApproverResolver.resolveEffectiveOptions(request)).thenReturn(List.of(ApprovableBy.AUTHORIZED));
            when(requestApproverResolver.isItSystemAccessAllowed(any(), any())).thenReturn(true);
            when(requestApproverResolver.isOuAccessAllowed(any(), any())).thenReturn(true);

            ItSystem roleCatalogueSystem = createItSystem("rc-uuid", List.of());
            roleCatalogueSystem.setId(99L);
            SystemRole requestAuthorizedRole = new SystemRole();
            stubAuthorizedRoleLookup(roleCatalogueSystem, requestAuthorizedRole, List.of(requesterAuthorized, otherAuthorized));

            when(requestApproverResolver.canApprove(request, requesterAuthorized)).thenReturn(false);
            when(requestApproverResolver.canApprove(request, otherAuthorized)).thenReturn(true);
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

            when(roleRequestDao.findByStatusInAndEmailSent(anyCollection(), anyBoolean())).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(requestApproverResolver.resolveEffectiveOptions(request)).thenReturn(List.of(ApprovableBy.AUTHORIZED));
            when(requestApproverResolver.isItSystemAccessAllowed(any(), any())).thenReturn(true);
            when(requestApproverResolver.isOuAccessAllowed(any(), any())).thenReturn(true);

            ItSystem roleCatalogueSystem = createItSystem("rc-uuid", List.of());
            roleCatalogueSystem.setId(99L);
            SystemRole requestAuthorizedRole = new SystemRole();
            stubAuthorizedRoleLookup(roleCatalogueSystem, requestAuthorizedRole, List.of(receiverAuthorized));

            when(requestApproverResolver.canApprove(request, receiverAuthorized)).thenReturn(false);
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
                    .userRole(userRole)
                    .approverOption(List.of(ApprovableBy.SYSTEMRESPONSIBLE))
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

            when(roleRequestDao.findByStatusInAndEmailSent(anyCollection(), anyBoolean())).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails()).thenReturn(java.util.Collections.emptyMap());
            when(requestApproverResolver.resolveEffectiveOptions(request)).thenReturn(List.of(ApprovableBy.SYSTEMRESPONSIBLE));
            when(itSystemService.getAttestationResponsibles(itSystem)).thenReturn(owners);
            for (User owner : owners) {
                when(requestApproverResolver.canApprove(request, owner)).thenReturn(true);
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
                    .userRole(userRole)
                    .approverOption(List.of(ApprovableBy.AUTHORIZED, ApprovableBy.SYSTEMRESPONSIBLE))
                    .status(RequestApproveStatus.REQUESTED)
                    .emailSent(false)
                    .build();

            List<User> owners = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                String suffix = String.format("%02d", i);
                owners.add(makeUser("uuid-" + suffix, "aaaa" + suffix + "@example.com", "Owner " + suffix));
            }

            when(roleRequestDao.findByStatusInAndEmailSent(anyCollection(), anyBoolean())).thenReturn(List.of(request));
            when(settingsService.getRoleRequestApproverEmails())
                    .thenReturn(java.util.Map.of(ApprovableBy.AUTHORIZED, "servicedesk@example.com"));
            when(requestApproverResolver.resolveEffectiveOptions(request))
                    .thenReturn(List.of(ApprovableBy.AUTHORIZED, ApprovableBy.SYSTEMRESPONSIBLE));
            when(itSystemService.getAttestationResponsibles(itSystem)).thenReturn(owners);
            for (User owner : owners) {
                when(requestApproverResolver.canApprove(request, owner)).thenReturn(true);
            }
            when(emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS)).thenReturn(enabledTemplate());
            when(roleRequestDao.saveAll(any())).thenReturn(List.of(request));

            service.sendMailToRoleAssignerOnce();

            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            verify(emailQueueService, times(10)).queueEmail(emailCaptor.capture(), any(), any(), any(), any(), any());
            assertThat(emailCaptor.getAllValues()).contains("servicedesk@example.com");
        }
    }
}
