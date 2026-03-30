package dk.digitalidentity.rc.dao.model.enums;

import dk.digitalidentity.rc.dao.model.enums.dto.NotificationTypeDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@AllArgsConstructor
@Getter
public enum NotificationType {
	ORGUNIT_WITHOUT_AUTHORIZATION_MANAGER(
			"html.enum.notificationtype.orgunit_without_authorization_manager",
			"html.help.settings.notification.noAuthResponsibleOu.title",
			"html.help.settings.notification.noAuthResponsibleOu.body"
	),
	EDIT_REQUEST_APPROVE_EMAIL_TEMPLATE(
			"html.enum.notificationtype.edit_request_approve_email_template",
			"html.help.settings.notification.requestEmailShouldChange.title",
			"html.help.settings.notification.requestEmailShouldChange.body"
	),
	EDIT_ATTESTATION_EMAIL_TEMPLATE(
			"html.enum.notificationtype.edit_attestation_email_template",
			"html.help.settings.notification.attestationEmailShouldChange.title",
			"html.help.settings.notification.attestationEmailShouldChange.body"
	),
	NEW_ORG_UNIT(
			"html.enum.notificationtype.new_org_unit",
			"html.help.settings.notification.newOrgUnit.title",
			"html.help.settings.notification.newOrgUnit.body"
	),
	ORG_UNIT_NAME_CHANGED(
			"html.enum.notificationtype.org_unit_name_changed",
			"html.help.settings.notification.ouChangedName.title",
			"html.help.settings.notification.ouChangedName.body"
	),
	ORG_UNIT_NEW_PARENT(
			"html.enum.notificationtype.org_unit_new_parent",
			"html.help.settings.notification.ouChangedParent.title",
			"html.help.settings.notification.ouChangedParent.body"
	),
	NEW_TITLE_IN_ORG_UNIT(
			"html.enum.notificationtype.new_title_in_org_unit",
			"html.help.settings.notification.newPositionInOu.title",
			"html.help.settings.notification.newPositionInOu.body"
	),
	USER_MOVED_POSITIONS(
			"html.enum.notificationtype.user_moved_positions",
			"html.help.settings.notification.userWithRightsChangedOu.title",
			"html.help.settings.notification.userWithRightsChangedOu.body"
	),
	SYSTEM_OWNER_OR_RESPONSIBLE_MOVED_POSITIONS(
			"html.enum.notificationtype.system_owner_or_responsible_moved_positions",
			"html.help.settings.notification.responsibleChangedOu.title",
			"html.help.settings.notification.responsibleChangedOu.body"
	),
	UPDATE_KSP_CICS_USER_FAILED(
			"html.enum.notificationtype.update_ksp_cics_user_failed",
			"html.help.settings.notification.kSPSyncError.title",
			"html.help.settings.notification.kSPSyncError.body"
	);

	private final String message;
	private final String helpTitle;
	private final String helpBody;

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
