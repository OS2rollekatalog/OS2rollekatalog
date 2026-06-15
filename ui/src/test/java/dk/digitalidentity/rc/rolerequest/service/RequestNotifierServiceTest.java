package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.rolerequest.dao.RoleRequestDao;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.ItSystemService;
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

import java.util.List;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createEmailTemplate;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createItSystem;
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
}
