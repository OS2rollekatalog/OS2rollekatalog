package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum EmailTemplateType {
	ATTESTATION_NOTIFICATION("html.enum.email.message.type.attestation_notification", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)),
	ATTESTATION_REMINDER_10_DAYS("html.enum.email.message.type.attestation_reminder_10", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)),
	ATTESTATION_REMINDER_3_DAYS("html.enum.email.message.type.attestation_reminder_3", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)),
	ATTESTATION_REMINDER_THIRDPARTY("html.enum.email.message.type.attestation_reminder_thirdparty", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.USER_PLACEHOLDER)),
	ATTESTATION_SENSITIVE_NOTIFICATION("html.enum.email.message.type.attestation_sensitive_notification", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)),
	ATTESTATION_SENSITIVE_REMINDER_10_DAYS("html.enum.email.message.type.attestation_sensitive_reminder_10", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)),
	ATTESTATION_SENSITIVE_REMINDER_3_DAYS("html.enum.email.message.type.attestation_sensitive_reminder_3", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)),
	ATTESTATION_SENSITIVE_REMINDER_THIRDPARTY("html.enum.email.message.type.attestation_sensitive_reminder_thirdparty", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.USER_PLACEHOLDER)),
	ATTESTATION_IT_SYSTEM_NOTIFICATION("html.enum.email.message.type.attestation_it_system_notification", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER)),
	ATTESTATION_IT_SYSTEM_REMINDER_10_DAYS("html.enum.email.message.type.attestation_it_system_reminder_10", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER)),
	ATTESTATION_IT_SYSTEM_REMINDER_3_DAYS("html.enum.email.message.type.attestation_it_system_reminder_3", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER)),
	ATTESTATION_IT_SYSTEM_REMINDER_THIRDPARTY("html.enum.email.message.type.attestation_it_system_reminder_thirdparty", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.SYSTEM_RESPONSIBLE_PLACEHOLDER)),
	ATTESTATION_REQUEST_FOR_CHANGE("html.enum.email.message.type.attestation_request_for_change", Arrays.asList(EmailTemplatePlaceholder.CHANGE_REQUESTED_PLACEHOLDER, EmailTemplatePlaceholder.LIST_OF_CHANGE_REQUESTS, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER, EmailTemplatePlaceholder.USER_PLACEHOLDER)),
	ATTESTATION_REQUEST_FOR_REMOVAL("html.enum.email.message.type.attestation_request_for_removal", Arrays.asList(EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER)),
	ATTESTATION_REQUEST_FOR_ROLE_CHANGE("html.enum.email.message.type.attestation_request_for_role_change", Arrays.asList(EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER)),
	SUBSTITUTE("html.enum.email.message.type.substitute", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.MANAGER_PLACEHOLDER)),
	ROLE_EXPIRING("html.enum.email.message.type.role_expiring", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)),
	APPROVED_ROLE_REQUEST_USER("html.enum.email.message.type.approved_role_request_user", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME)),
	APPROVED_ROLE_REQUEST_MANAGER("html.enum.email.message.type.approved_role_request_manager", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER)),
	APPROVED_MANUAL_ROLE_REQUEST_USER("html.enum.email.message.type.approved_manual_role_request_user", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME)),
	APPROVED_MANUAL_ROLE_REQUEST_MANAGER("html.enum.email.message.type.approved_manual_role_request_manager", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER)),
	REJECTED_ROLE_REQUEST_MANAGER("html.enum.email.message.type.rejected_role_request_manager", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER)),
	WAITING_REQUESTS_ROLE_ASSIGNERS("html.enum.email.message.type.waiting_requests_role_assigners", Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.COUNT_PLACEHOLDER)),
	USER_WITH_MANUAL_ITSYSTEM_DELETED("html.enum.email.message.type.user_with_manual_itsystem_deleted", Arrays.asList(EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER));
	
	private String message;
	private List<EmailTemplatePlaceholder> emailTemplatePlaceholders;

	EmailTemplateType(String message, List<EmailTemplatePlaceholder> placeholders) {
		this.message = message;
		this.emailTemplatePlaceholders = placeholders;
	}
}