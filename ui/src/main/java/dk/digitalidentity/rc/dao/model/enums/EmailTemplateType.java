package dk.digitalidentity.rc.dao.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum EmailTemplateType {
	ATTESTATION_NOTIFICATION("html.enum.email.message.type.attestation_notification", true, false, true, Category.NORMAL_ATTESTATION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNITS_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_REMINDER1("html.enum.email.message.type.attestation_reminder1", true, false, true, Category.NORMAL_ATTESTATION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNITS_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_REMINDER2("html.enum.email.message.type.attestation_reminder2", true, false, true, Category.NORMAL_ATTESTATION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNITS_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_REMINDER3("html.enum.email.message.type.attestation_reminder3", true, false, true, Category.NORMAL_ATTESTATION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNITS_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_REMINDER_THIRDPARTY("html.enum.email.message.type.attestation_reminder_thirdparty", true, false, true, Category.NORMAL_ATTESTATION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_SENSITIVE_NOTIFICATION("html.enum.email.message.type.attestation_sensitive_notification", true, false, true, Category.SENSITIVE_ATTESTATION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNITS_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_SENSITIVE_REMINDER1("html.enum.email.message.type.attestation_sensitive_reminder1", true, false, true, Category.SENSITIVE_ATTESTATION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNITS_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_SENSITIVE_REMINDER2("html.enum.email.message.type.attestation_sensitive_reminder2", true, false, true, Category.SENSITIVE_ATTESTATION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNITS_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_SENSITIVE_REMINDER3("html.enum.email.message.type.attestation_sensitive_reminder3", true, false, true, Category.SENSITIVE_ATTESTATION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNITS_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_SENSITIVE_REMINDER_THIRDPARTY("html.enum.email.message.type.attestation_sensitive_reminder_thirdparty", true, false, true, Category.SENSITIVE_ATTESTATION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_SENSITIVE_IT_SYSTEM_ASSIGNMENT_NOTIFICATION("html.enum.email.message.type.attestation_sensitive_it_system_assignment_notification", true, false, true, Category.SENSITIVE_ATTESTATION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_SENSITIVE_IT_SYSTEM_ASSIGNMENT_REMINDER1("html.enum.email.message.type.attestation_sensitive_it_system_assignment_reminder1", true, false, true, Category.SENSITIVE_ATTESTATION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_SENSITIVE_IT_SYSTEM_ASSIGNMENT_REMINDER2("html.enum.email.message.type.attestation_sensitive_it_system_assignment_reminder2", true, false, true, Category.SENSITIVE_ATTESTATION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_SENSITIVE_IT_SYSTEM_ASSIGNMENT_REMINDER3("html.enum.email.message.type.attestation_sensitive_it_system_assignment_reminder3", true, false, true, Category.SENSITIVE_ATTESTATION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_SENSITIVE_IT_SYSTEM_ASSIGNMENT_REMINDER_THIRDPARTY("html.enum.email.message.type.attestation_sensitive_it_system_assignment_reminder_thirdparty", true, false, true, Category.SENSITIVE_ATTESTATION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.SYSTEM_RESPONSIBLE_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_IT_SYSTEM_NOTIFICATION("html.enum.email.message.type.attestation_it_system_notification", true, false, true, Category.IT_SYSTEM_ROLE_COMPOSITION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_IT_SYSTEM_REMINDER1("html.enum.email.message.type.attestation_it_system_reminder1", true, false, true, Category.IT_SYSTEM_ROLE_COMPOSITION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_IT_SYSTEM_REMINDER2("html.enum.email.message.type.attestation_it_system_reminder2", true, false, true, Category.IT_SYSTEM_ROLE_COMPOSITION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_IT_SYSTEM_REMINDER3("html.enum.email.message.type.attestation_it_system_reminder3", true, false, true, Category.IT_SYSTEM_ROLE_COMPOSITION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_IT_SYSTEM_REMINDER_THIRDPARTY("html.enum.email.message.type.attestation_it_system_reminder_thirdparty", true, false, true, Category.IT_SYSTEM_ROLE_COMPOSITION, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.SYSTEM_RESPONSIBLE_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_IT_SYSTEM_ASSIGNMENT_NOTIFICATION("html.enum.email.message.type.attestation_it_system_assignment_notification", true, false, true, Category.IT_SYSTEM_ROLE_ASSIGNMENT, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER1("html.enum.email.message.type.attestation_it_system_assignment_reminder1", true, false, true, Category.IT_SYSTEM_ROLE_ASSIGNMENT, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER2("html.enum.email.message.type.attestation_it_system_assignment_reminder2", true, false, true, Category.IT_SYSTEM_ROLE_ASSIGNMENT, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER3("html.enum.email.message.type.attestation_it_system_assignment_reminder3", true, false, true, Category.IT_SYSTEM_ROLE_ASSIGNMENT, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_IT_SYSTEM_ASSIGNMENT_REMINDER_THIRDPARTY("html.enum.email.message.type.attestation_it_system_assignment_reminder_thirdparty", true, false, true, Category.IT_SYSTEM_ROLE_ASSIGNMENT, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.SYSTEM_RESPONSIBLE_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE)),
	ATTESTATION_MANAGERDELEGATE_NOTIFICATION("html.enum.email.message.type.managerdelegate.attestation_notification", true, false, true, Category.PERSONAL_APPROVER, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNITS_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE, EmailTemplatePlaceholder.MANAGERDELEGATE_PLACEHOLDER)),
	ATTESTATION_MANAGERDELEGATE_REMINDER1("html.enum.email.message.type.managerdelegate.attestation_reminder1", true, false, true, Category.PERSONAL_APPROVER, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNITS_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE, EmailTemplatePlaceholder.MANAGERDELEGATE_PLACEHOLDER)),
	ATTESTATION_MANAGERDELEGATE_REMINDER2("html.enum.email.message.type.managerdelegate.attestation_reminder2", true, false, true, Category.PERSONAL_APPROVER, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNITS_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE, EmailTemplatePlaceholder.MANAGERDELEGATE_PLACEHOLDER)),
	ATTESTATION_MANAGERDELEGATE_REMINDER3("html.enum.email.message.type.managerdelegate.attestation_reminder3", true, false, true, Category.PERSONAL_APPROVER, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNITS_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE, EmailTemplatePlaceholder.MANAGERDELEGATE_PLACEHOLDER)),
	ATTESTATION_MANAGERDELEGATE_REMINDER_THIRDPARTY("html.enum.email.message.type.managerdelegate.attestation_reminder_thirdparty", true, false, true, Category.PERSONAL_APPROVER, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_DEADLINE, EmailTemplatePlaceholder.MANAGERDELEGATE_PLACEHOLDER)),

	ATTESTATION_REQUEST_FOR_CHANGE("html.enum.email.message.type.attestation_request_for_change", true, false, false, Category.CHANGE_REQUEST, Arrays.asList(EmailTemplatePlaceholder.CHANGE_REQUESTED_PLACEHOLDER, EmailTemplatePlaceholder.LIST_OF_CHANGE_REQUESTS, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER, EmailTemplatePlaceholder.USER_PLACEHOLDER)),
	ATTESTATION_REQUEST_FOR_REMOVAL("html.enum.email.message.type.attestation_request_for_removal", true, false, false, Category.CHANGE_REQUEST, Arrays.asList(EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)),
	ATTESTATION_REQUEST_FOR_ROLE_CHANGE("html.enum.email.message.type.attestation_request_for_role_change", true, false, false, Category.CHANGE_REQUEST, Arrays.asList(EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.CHANGE_REQUESTED_PLACEHOLDER)),
	ATTESTATION_SUMMARY("html.enum.email.message.type.attestation_summary", true, false, false, Category.SUMMARY, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.ATTESTATION_CHANGES_OU, EmailTemplatePlaceholder.ATTESTATION_CHANGES_USERS)),
	SUBSTITUTE("html.enum.email.message.type.substitute", false, false, false, Category.GENERAL, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.MANAGER_PLACEHOLDER)),
	ROLE_EXPIRING("html.enum.email.message.type.role_expiring", false, false, false, Category.GENERAL, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)),
	APPROVED_ROLE_REQUEST_USER("html.enum.email.message.type.approved_role_request_user", false, true, false, Category.ROLE_REQUEST, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.REQUEST_REASON, EmailTemplatePlaceholder.START_DATE, EmailTemplatePlaceholder.STOP_DATE, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_TYPE_PLACEHOLDER)),
	APPROVED_ROLE_REQUEST_REMOVAL_USER("html.enum.email.message.type.approved_role_request_removal_user", false, true, false, Category.ROLE_REQUEST, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.REQUEST_REASON, EmailTemplatePlaceholder.REQUESTER_TYPE_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER)),
	APPROVED_ROLE_REQUEST_MANAGER("html.enum.email.message.type.approved_role_request_manager", false, true, false, Category.ROLE_REQUEST, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER, EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER, EmailTemplatePlaceholder.REQUEST_REASON, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_TYPE_PLACEHOLDER)),
	APPROVED_MANUAL_ROLE_REQUEST_USER("html.enum.email.message.type.approved_manual_role_request_user", false, true, false, Category.MANUAL_ROLE_REQUEST, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.REQUEST_REASON, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_TYPE_PLACEHOLDER)),
	APPROVED_MANUAL_ROLE_REQUEST_MANAGER("html.enum.email.message.type.approved_manual_role_request_manager", false, true, false, Category.MANUAL_ROLE_REQUEST, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER, EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER, EmailTemplatePlaceholder.REQUEST_REASON, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_TYPE_PLACEHOLDER)),
	REJECTED_ROLE_REQUEST_MANAGER("html.enum.email.message.type.rejected_role_request_manager", false, true, false, Category.ROLE_REQUEST, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER, EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER, EmailTemplatePlaceholder.REQUEST_REASON, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.REQUESTER_TYPE_PLACEHOLDER)),
	WAITING_REQUESTS_ROLE_ASSIGNERS("html.enum.email.message.type.waiting_requests_role_assigners", false, true, false, Category.PENDING_REQUESTS, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.COUNT_PLACEHOLDER)),
	WAITING_REQUESTS_SERVICEDESK("html.enum.email.message.type.waiting_requests_servicedesk", false, true, false, Category.PENDING_REQUESTS, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.COUNT_PLACEHOLDER)),
	WAITING_REQUESTS_ROLE_ASSIGNERS_DAILY("html.enum.email.message.type.waiting_requests_role_assigners_daily", false, true, false, Category.PENDING_REQUESTS, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.COUNT_PLACEHOLDER)),
	USER_WITH_MANUAL_ITSYSTEM_DELETED("html.enum.email.message.type.user_with_manual_itsystem_deleted", false, false, false, Category.GENERAL, Arrays.asList(EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER)),
	ORGUNIT_NEW_PARENT("html.enum.email.message.type.orgunit_new_parent", false, false, false, Category.GENERAL, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER)),
	USER_WITH_DIRECT_ROLES_CHANGED_ORGUNIT("html.enum.email.message.type.user_with_direct_roles_changed_orgunit", false, false, false, Category.GENERAL, Arrays.asList(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.NEW_POSITIONS_PLACEHOLDER, EmailTemplatePlaceholder.OLD_POSITIONS_PLACEHOLDER)),
	SYSTEM_ROLE_EXCEEDED_MAX_ASSIGNMENTS("html.enum.email.message.type.system_role_maximum_assignments", false, false, false, Category.GENERAL, Arrays.asList(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.COUNT_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.MAX_COUNT_PLACEHOLDER)),
	MANUAL_SYSTEM_CONTACT_PERFORMER("html.enum.email.message.type.manual_system_contact_performer", false, false, false, Category.CONTACT, ContactStructure.SYSTEM_PLACEHOLDERS, ContactStructure.SYSTEM_REPEATING_PART),
	MANUAL_SYSTEM_CONTACT_ADVIS("html.enum.email.message.type.manual_system_contact_advis", false, false, false, Category.CONTACT, ContactStructure.SYSTEM_PLACEHOLDERS, ContactStructure.SYSTEM_REPEATING_PART),
	MANUAL_ROLE_CONTACT_PERFORMER("html.enum.email.message.type.manual_role_contact_performer", false, false, false, Category.CONTACT, ContactStructure.ROLE_PLACEHOLDERS, ContactStructure.ROLE_REPEATING_PART),
	MANUAL_ROLE_CONTACT_ADVIS("html.enum.email.message.type.manual_role_contact_advis", false, false, false, Category.CONTACT, ContactStructure.ROLE_PLACEHOLDERS, ContactStructure.ROLE_REPEATING_PART);

	private final String message;
	private final boolean attestation;
	private final boolean request;
	private final boolean allowDaysBeforeDeadline;
	private final String category;
	private final List<EmailTemplatePlaceholder> emailTemplatePlaceholders;
	private final RepeatingPartDescriptor repeatingPart;

	EmailTemplateType(String message, boolean attestation, boolean request, boolean allowDaysBeforeDeadline, String category, List<EmailTemplatePlaceholder> placeholders) {
		this(message, attestation, request, allowDaysBeforeDeadline, category, placeholders, null);
	}

	EmailTemplateType(String message, boolean attestation, boolean request, boolean allowDaysBeforeDeadline, String category, List<EmailTemplatePlaceholder> placeholders, RepeatingPartDescriptor repeatingPart) {
		this.message = message;
		this.attestation = attestation;
		this.request = request;
		this.allowDaysBeforeDeadline = allowDaysBeforeDeadline;
		this.category = category;
		this.emailTemplatePlaceholders = placeholders;
		this.repeatingPart = repeatingPart;
	}

	public boolean hasRepeatingPart() {
		return repeatingPart != null;
	}

	public boolean hasNestedRepeatingPart() {
		return repeatingPart != null && repeatingPart.nested() != null;
	}

	// Category constants for grouping on the overview page
	public static final class Category {
		public static final String NORMAL_ATTESTATION = "Normalt rul";
		public static final String SENSITIVE_ATTESTATION = "Følsomt rul";
		public static final String IT_SYSTEM_ROLE_COMPOSITION = "IT-system rolleopbygning";
		public static final String IT_SYSTEM_ROLE_ASSIGNMENT = "IT-system rolletildeling";
		public static final String PERSONAL_APPROVER = "Personlig godkender";
		public static final String CHANGE_REQUEST = "Ændringsanmodning";
		public static final String SUMMARY = "Opsummering";
		public static final String ROLE_REQUEST = "Rolleanmodning";
		public static final String MANUAL_ROLE_REQUEST = "Manuel rolleanmodning";
		public static final String PENDING_REQUESTS = "Afventende anmodninger";
		public static final String GENERAL = "Generel";
		public static final String CONTACT = "Kontakt e-mails";

		private Category() {
		}
	}

	// shared structure for the contact templates - performer and advis variants of the same mail must
	// expose identical placeholder sets, so they reference the same constants
	private static final class ContactStructure {
		private static final List<EmailTemplatePlaceholder> SYSTEM_PLACEHOLDERS =
				List.of(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.USERS_BLOCK_PLACEHOLDER);

		// the per-user block is a free-form block (no list wrapper); its changes render as a <ul>/<li> list.
		// the renderer owns the <ul>/<li> tags so the editable parts stay plain content (see RepeatingPartDescriptor)
		private static final RepeatingPartDescriptor SYSTEM_REPEATING_PART = new RepeatingPartDescriptor(
				EmailTemplatePlaceholder.USERS_BLOCK_PLACEHOLDER,
				List.of(EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.USER_ID_PLACEHOLDER, EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, EmailTemplatePlaceholder.PERSON_UUID_PLACEHOLDER, EmailTemplatePlaceholder.CHANGES_PLACEHOLDER),
				null, null,
				new RepeatingPartDescriptor(
						EmailTemplatePlaceholder.CHANGES_PLACEHOLDER,
						List.of(EmailTemplatePlaceholder.ACTION_PLACEHOLDER, EmailTemplatePlaceholder.ACTION_PAST_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.ROLE_DESCRIPTION_PLACEHOLDER, EmailTemplatePlaceholder.ASSIGNED_BY_PLACEHOLDER),
						"ul", "li"));

		private static final List<EmailTemplatePlaceholder> ROLE_PLACEHOLDERS =
				List.of(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, EmailTemplatePlaceholder.ROLE_NAME, EmailTemplatePlaceholder.ROLE_DESCRIPTION_PLACEHOLDER, EmailTemplatePlaceholder.USERS_BLOCK_PLACEHOLDER);

		// each affected user renders as a <li> inside a <ul>; the renderer owns those tags
		private static final RepeatingPartDescriptor ROLE_REPEATING_PART = new RepeatingPartDescriptor(
				EmailTemplatePlaceholder.USERS_BLOCK_PLACEHOLDER,
				List.of(EmailTemplatePlaceholder.ACTION_PLACEHOLDER, EmailTemplatePlaceholder.USER_PLACEHOLDER, EmailTemplatePlaceholder.USER_ID_PLACEHOLDER, EmailTemplatePlaceholder.PERSON_UUID_PLACEHOLDER),
				"ul", "li");

		private ContactStructure() {
		}
	}
}
