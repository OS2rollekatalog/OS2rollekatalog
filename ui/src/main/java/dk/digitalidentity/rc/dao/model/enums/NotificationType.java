package dk.digitalidentity.rc.dao.model.enums;

public enum NotificationType {
	ORGUNIT_WITHOUT_AUTHORIZATION_MANAGER("html.enum.notificationtype.orgunit_without_authorization_manager"),
	EDIT_REQUEST_APPROVE_EMAIL_TEMPLATE("html.enum.notificationtype.edit_request_approve_email_template"),
	EDIT_ATTESTATION_EMAIL_TEMPLATE("html.enum.notificationtype.edit_attestation_email_template"),
	NEW_ORG_UNIT("html.enum.notificationtype.new_org_unit"),
	ORG_UNIT_NEW_PARENT("html.enum.notificationtype.org_unit_new_parent"),
	NEW_TITLE_IN_ORG_UNIT("html.enum.notificationtype.new_title_in_org_unit");

	private String message;

	private NotificationType(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
}
