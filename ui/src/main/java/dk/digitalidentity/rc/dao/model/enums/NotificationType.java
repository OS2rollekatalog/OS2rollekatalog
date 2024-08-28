package dk.digitalidentity.rc.dao.model.enums;

import dk.digitalidentity.rc.dao.model.enums.dto.NotificationTypeDTO;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum NotificationType {
	ORGUNIT_WITHOUT_AUTHORIZATION_MANAGER("html.enum.notificationtype.orgunit_without_authorization_manager"),
	EDIT_REQUEST_APPROVE_EMAIL_TEMPLATE("html.enum.notificationtype.edit_request_approve_email_template"),
	EDIT_ATTESTATION_EMAIL_TEMPLATE("html.enum.notificationtype.edit_attestation_email_template"),
	NEW_ORG_UNIT("html.enum.notificationtype.new_org_unit"),
	ORG_UNIT_NEW_PARENT("html.enum.notificationtype.org_unit_new_parent"),
	NEW_TITLE_IN_ORG_UNIT("html.enum.notificationtype.new_title_in_org_unit"),
	USER_MOVED_POSITIONS("html.enum.notificationtype.user_moved_positions"),
	SYSTEM_OWNER_OR_RESPONSIBLE_MOVED_POSITIONS("html.enum.notificationtype.system_owner_or_responsible_moved_positions"),
	UPDATE_KSP_CICS_USER_FAILED("html.enum.notificationtype.update_ksp_cics_user_failed");

	private String message;

	private NotificationType(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public static List<NotificationTypeDTO> getSorted(ResourceBundleMessageSource resourceBundle, Locale locale) {
		List<NotificationTypeDTO> dtos = new ArrayList<>();

		for (NotificationType notificationType : NotificationType.values()) {
			String newMessage = resourceBundle.getMessage(notificationType.getMessage(), null, locale);
			NotificationTypeDTO dto = new NotificationTypeDTO();
			dto.setNotificationType(notificationType);
			dto.setMessage(newMessage);

			dtos.add(dto);
		}

		dtos.sort((a, b) -> a.getMessage().compareToIgnoreCase(b.getMessage()));

		return dtos;
	}
}
