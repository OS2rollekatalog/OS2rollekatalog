package dk.digitalidentity.rc.dao.model.enums;

public enum NotificationType {
	ORGUNIT_WITHOUT_AUTHORIZATION_MANAGER("html.enum.notificationtype.orgunit_without_authorization_manager");

	private String message;

	private NotificationType(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
}
