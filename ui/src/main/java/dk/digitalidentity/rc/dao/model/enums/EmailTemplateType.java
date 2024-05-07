package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum EmailTemplateType {
	ATTESTATION_NOTIFICATION("html.enum.email.message.type.attestation_notification", true, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_REMINDER1("html.enum.email.message.type.attestation_reminder1", true,  Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_REMINDER2("html.enum.email.message.type.attestation_reminder2", true,  Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_REMINDER3("html.enum.email.message.type.attestation_reminder3", true,  Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_REMINDER_THIRDPARTY("html.enum.email.message.type.attestation_reminder_thirdparty", true, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_SENSITIVE_NOTIFICATION("html.enum.email.message.type.attestation_sensitive_notification", true, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_SENSITIVE_REMINDER1("html.enum.email.message.type.attestation_sensitive_reminder1", true, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_SENSITIVE_REMINDER2("html.enum.email.message.type.attestation_sensitive_reminder2", true, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_SENSITIVE_REMINDER3("html.enum.email.message.type.attestation_sensitive_reminder3", true, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_SENSITIVE_REMINDER_THIRDPARTY("html.enum.email.message.type.attestation_sensitive_reminder_thirdparty", true, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_IT_SYSTEM_NOTIFICATION("html.enum.email.message.type.attestation_it_system_notification", true, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_IT_SYSTEM_REMINDER1("html.enum.email.message.type.attestation_it_system_reminder1", true, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_IT_SYSTEM_REMINDER2("html.enum.email.message.type.attestation_it_system_reminder2", true, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_IT_SYSTEM_REMINDER3("html.enum.email.message.type.attestation_it_system_reminder3", true, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_IT_SYSTEM_REMINDER_THIRDPARTY("html.enum.email.message.type.attestation_it_system_reminder_thirdparty", true, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.SYSTEM_RESPONSIBLE_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_REQUEST_FOR_CHANGE("html.enum.email.message.type.attestation_request_for_change", true, Arrays.asList(EmailTemplatePlaceholder.CHANGE_REQUESTED_PLACEHOLDER, EmailTemplatePlaceholder.LIST_OF_CHANGE_REQUESTS, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER, EmailTemplatePlaceholder.USER_PLACEHOLDER)),
	ATTESTATION_REQUEST_FOR_REMOVAL("html.enum.email.message.type.attestation_request_for_removal", true, Arrays.asList(EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)),
	ATTESTATION_REQUEST_FOR_ROLE_CHANGE("html.enum.email.message.type.attestation_request_for_role_change", true, Arrays.asList(EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER)),
	SUBSTITUTE("html.enum.email.message.type.substitute", false, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.MANAGER_PLACEHOLDER)),
	ROLE_EXPIRING("html.enum.email.message.type.role_expiring", false, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)),
	APPROVED_ROLE_REQUEST_USER("html.enum.email.message.type.approved_role_request_user", false, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME)),
	APPROVED_ROLE_REQUEST_REMOVAL_USER("html.enum.email.message.type.approved_role_request_removal_user", false, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME)),
	APPROVED_ROLE_REQUEST_MANAGER("html.enum.email.message.type.approved_role_request_manager", false, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER, EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER)),
	APPROVED_MANUAL_ROLE_REQUEST_USER("html.enum.email.message.type.approved_manual_role_request_user", false, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME)),
	APPROVED_MANUAL_ROLE_REQUEST_MANAGER("html.enum.email.message.type.approved_manual_role_request_manager", false, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER, EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER)),
	REJECTED_ROLE_REQUEST_MANAGER("html.enum.email.message.type.rejected_role_request_manager", false, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER, EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER)),
	WAITING_REQUESTS_ROLE_ASSIGNERS("html.enum.email.message.type.waiting_requests_role_assigners", false, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.COUNT_PLACEHOLDER)),
	USER_WITH_MANUAL_ITSYSTEM_DELETED("html.enum.email.message.type.user_with_manual_itsystem_deleted", false, Arrays.asList(EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER));
	
	private String message;
	private boolean attestation;
	private List<EmailTemplatePlaceholder> emailTemplatePlaceholders;

	EmailTemplateType(String message, boolean attestation, List<EmailTemplatePlaceholder> placeholders) {
		this.message = message;
		this.attestation = attestation;
		this.emailTemplatePlaceholders = placeholders;
	}
}