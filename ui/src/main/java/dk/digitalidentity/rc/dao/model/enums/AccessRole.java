package dk.digitalidentity.rc.dao.model.enums;

public enum AccessRole {
	ORGANISATION("html.enum.accessrole.organisation"),
	READ_ACCESS("html.enum.accessrole.readAccess"),
	ROLE_MANAGEMENT("html.enum.accessrole.roleManagement"),
	VENDOR("html.enum.accessrole.vendor"),
	ITSYSTEM("html.enum.accessrole.itsystem"),
	ADMINISTRATOR("html.enum.accessrole.administrator"),
	AUDITLOG_ACCESS("html.enum.accessrole.auditlogAccess"),
	CICS_ADMIN("html.enum.accessrole.cicsAdmin");

	private String messageId;

	private AccessRole(String messageId) {
		this.messageId = messageId;
	}

	public String getMessageId() {
		return messageId;
	}
}
