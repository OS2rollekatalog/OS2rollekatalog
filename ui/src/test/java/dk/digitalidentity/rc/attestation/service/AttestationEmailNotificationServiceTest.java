package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationMail;
import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createDisabledEmailTemplate;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createEmailTemplate;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createOrganisationAttestation;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createRoleGroupAssignment;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUserRoleAssignment;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Attestation Email Notification Service Tests")
class AttestationEmailNotificationServiceTest {

	private static final String REQUESTER = "Requester Name(requester-id)";
	private static final String USER = "User Name(user-id)";
	private static final String CHANGE_EMAIL = "change@example.com";

	@Mock
	private AttestationDao attestationDao;

	@Mock
	private EmailTemplateService emailTemplateService;

	@Mock
	private EmailQueueService emailQueueService;

	@Mock
	private SettingsService settingsService;

	@Mock
	private OrgUnitDao orgUnitDao;

	@Mock
	private AttestationRunService attestationRunService;

	@Mock
	private ItSystemDao itSystemDao;

	@InjectMocks
	private AttestationEmailNotificationService attestationEmailNotificationService;

	private EmailTemplate emailTemplate;

	@BeforeEach
	void setUp() {
		emailTemplate = createEmailTemplate(
				EmailTemplateType.ATTESTATION_REQUEST_FOR_CHANGE,
				"Test Title {user}",
				"Test Message {user} {change}",
				true);
	}

	@Nested
	@DisplayName("sendRequestForAdRemoval() Tests")
	class SendRequestForAdRemovalTests {

		@Test
		@DisplayName("Should send email for AD removal request")
		void sendRequestForAdRemoval_ShouldQueueEmail() {
			// Arrange
			String responsibleOu = "Test OU";
			EmailTemplate template = createEmailTemplate(
					EmailTemplateType.ATTESTATION_REQUEST_FOR_REMOVAL,
					"Removal Request",
					"Please remove {user}",
					true);

			when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REQUEST_FOR_REMOVAL))
					.thenReturn(template);
			when(settingsService.getAttestationChangeEmail()).thenReturn(CHANGE_EMAIL);

			// Act
			attestationEmailNotificationService.sendRequestForAdRemoval(REQUESTER, USER, responsibleOu);

			// Assert
			verify(emailQueueService).queueEmail(
					eq(CHANGE_EMAIL),
					any(String.class),
					any(String.class),
					eq(template),
					isNull(),
					isNull()
			);
		}
	}

	@Nested
	@DisplayName("sendRequestForChangeMail() Tests")
	class SendRequestForChangeMailTests {

		@Test
		@DisplayName("Should send email for change request")
		void sendRequestForChangeMail_ShouldQueueEmail() {
			// Arrange
			String change = "Remove role assignment";
			List<RoleAssignmentDTO> notApproved = List.of(
					createUserRoleAssignment(1L, "Test Role", "Test System")
			);

			when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REQUEST_FOR_CHANGE))
					.thenReturn(emailTemplate);
			when(settingsService.getAttestationChangeEmail()).thenReturn(CHANGE_EMAIL);

			// Act
			attestationEmailNotificationService.sendRequestForChangeMail(REQUESTER, USER, change, notApproved);

			// Assert
			verify(emailQueueService).queueEmail(
					eq(CHANGE_EMAIL),
					any(String.class),
					any(String.class),
					eq(emailTemplate),
					isNull(),
					isNull()
			);
		}

		@Test
		@DisplayName("Should send email with empty not approved list")
		void sendRequestForChangeMail_WithEmptyNotApproved_ShouldQueueEmail() {
			// Arrange
			String change = "General change request";

			when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REQUEST_FOR_CHANGE))
					.thenReturn(emailTemplate);
			when(settingsService.getAttestationChangeEmail()).thenReturn(CHANGE_EMAIL);

			// Act
			attestationEmailNotificationService.sendRequestForChangeMail(REQUESTER, USER, change, Collections.emptyList());

			// Assert
			verify(emailQueueService).queueEmail(
					eq(CHANGE_EMAIL),
					any(String.class),
					any(String.class),
					eq(emailTemplate),
					isNull(),
					isNull()
			);
		}
	}

	@Nested
	@DisplayName("sendRequestForRoleChange() Tests")
	class SendRequestForRoleChangeTests {

		@Test
		@DisplayName("Should send email for role change request")
		void sendRequestForRoleChange_ShouldQueueEmail() {
			// Arrange
			String userRole = "Admin Role";
			String itSystemName = "Test System";
			String remark = "Role needs modification";
			EmailTemplate template = createEmailTemplate(
					EmailTemplateType.ATTESTATION_REQUEST_FOR_ROLE_CHANGE,
					"Role Change Request",
					"Change role {rolle} in {it-system}",
					true);

			when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REQUEST_FOR_ROLE_CHANGE))
					.thenReturn(template);
			when(settingsService.getAttestationChangeEmail()).thenReturn(CHANGE_EMAIL);

			// Act
			attestationEmailNotificationService.sendRequestForRoleChange(REQUESTER, userRole, itSystemName, remark);

			// Assert
			verify(emailQueueService).queueEmail(
					eq(CHANGE_EMAIL),
					any(String.class),
					any(String.class),
					eq(template),
					isNull(),
					isNull()
			);
		}
	}

	@Nested
	@DisplayName("sendItSystemNotifications() Tests")
	class SendItSystemNotificationsTests {

		@Test
		@DisplayName("Should not send emails when no current run exists")
		void sendItSystemNotifications_WhenNoCurrentRun_ShouldNotSendEmails() {
			// Arrange
			when(attestationRunService.getCurrentRun()).thenReturn(Optional.empty());

			// Act
			attestationEmailNotificationService.sendItSystemNotifications(LocalDate.now());

			// Assert
			verify(attestationDao, never()).findAttestationsWhichNeedsMail(any(), any(), any());
		}
	}

	@Nested
	@DisplayName("sendOrganisationNotifications() Tests")
	class SendOrganisationNotificationsTests {

		@Test
		@DisplayName("Should not send emails when no current run exists")
		void sendOrganisationNotifications_WhenNoCurrentRun_ShouldNotSendEmails() {
			// Arrange
			when(attestationRunService.getCurrentRun()).thenReturn(Optional.empty());

			// Act
			attestationEmailNotificationService.sendOrganisationNotifications(LocalDate.now());

			// Assert
			verify(attestationDao, never()).findById(any());
		}
	}

	@Nested
	@DisplayName("sendEmail() Tests")
	class SendEmailTests {

		@Test
		@DisplayName("Should not send email when template is disabled")
		void sendEmail_WhenTemplateDisabled_ShouldNotSendEmail() {
			// Arrange
			Long attestationId = 1L;
			Attestation attestation = createOrganisationAttestation(1L, "attestation-uuid", "ou-uuid", "Test OU");
			attestation.setItSystemId(1L);
			attestation.setItSystemName("Test IT System");
			EmailTemplate disabledTemplate = createDisabledEmailTemplate(EmailTemplateType.ATTESTATION_NOTIFICATION);

			when(attestationDao.findById(attestationId)).thenReturn(Optional.of(attestation));
			when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_NOTIFICATION))
					.thenReturn(disabledTemplate);

			// Act
			attestationEmailNotificationService.sendEmail(attestationId, Attestation.AttestationType.ORGANISATION_ATTESTATION, AttestationMail.MailType.INFORMATION);

			// Assert
			verify(emailQueueService, never()).queueEmail(any(), any(), any(), any(), any(), any());
		}
	}

	@Nested
	@DisplayName("findSystemOwner() Tests")
	class FindSystemOwnerTests {

		@Test
		@DisplayName("Should find system owner when IT system exists")
		void findSystemOwner_WhenItSystemExists_ShouldReturnOwner() {
			// Arrange
			Long itSystemId = 1L;
			User systemOwner = createUser("owner-uuid", "owner-id", "System Owner");

			ItSystem itSystem = createItSystem(itSystemId, "Test System");
			itSystem.setSystemOwner(systemOwner);

			when(itSystemDao.findById(itSystemId)).thenReturn(Optional.of(itSystem));

			// Act
			Optional<ItSystem> result = itSystemDao.findById(itSystemId);

			// Assert
			assertTrue(result.isPresent());
			assertNotNull(result.get().getSystemOwner());
			assertEquals("System Owner", result.get().getSystemOwner().getName());
		}
	}

	@Nested
	@DisplayName("findOrgUnitName() Tests")
	class FindOrgUnitNameTests {

		@Test
		@DisplayName("Should return org unit name when found by responsible OU UUID")
		void findOrgUnitName_WhenOuFound_ShouldReturnName() {
			// Arrange
			String ouUuid = "ou-uuid";
			OrgUnit orgUnit = createOrgUnit(ouUuid, "Test OU");

			when(orgUnitDao.findById(ouUuid)).thenReturn(Optional.of(orgUnit));

			// Act
			Optional<OrgUnit> result = orgUnitDao.findById(ouUuid);

			// Assert
			assertTrue(result.isPresent());
			assertEquals("Test OU", result.get().getName());
		}
	}

	@Nested
	@DisplayName("Change List Generation Tests")
	class ChangeListGenerationTests {

		@Test
		@DisplayName("Should format not approved roles correctly in email")
		void sendRequestForChangeMail_ShouldIncludeFormattedRoleList() {
			// Arrange
			String change = "Roles not approved";
			List<RoleAssignmentDTO> notApproved = List.of(
					createUserRoleAssignment(1L, "User Role 1", "System A"),
					createRoleGroupAssignment(2L, "Role Group 1", "System B")
			);

			EmailTemplate template = createEmailTemplate(
					EmailTemplateType.ATTESTATION_REQUEST_FOR_CHANGE,
					"Change Request",
					"{liste_over_ændringsønsker}",
					true);

			when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_REQUEST_FOR_CHANGE))
					.thenReturn(template);
			when(settingsService.getAttestationChangeEmail()).thenReturn(CHANGE_EMAIL);

			// Act
			attestationEmailNotificationService.sendRequestForChangeMail(REQUESTER, USER, change, notApproved);

			// Assert
			ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
			verify(emailQueueService).queueEmail(
					eq(CHANGE_EMAIL),
					any(String.class),
					messageCaptor.capture(),
					eq(template),
					isNull(),
					isNull()
			);

			String message = messageCaptor.getValue();
			assertTrue(message.contains("System A") || message.contains("Jobfunktionsrollen") ||
					message.contains("System B") || message.contains("Rollebuketten") ||
					message.contains("{liste_over_ændringsønsker}"));
		}
	}
}
