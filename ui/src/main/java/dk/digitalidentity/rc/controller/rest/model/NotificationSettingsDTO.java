package dk.digitalidentity.rc.controller.rest.model;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;

import dk.digitalidentity.rc.dao.model.enums.NotificationType;

@Getter
@Setter
public class NotificationSettingsDTO {
	private boolean deleteAlreadyCreated;
	private HashMap<NotificationType, Boolean> notificationTypes;
}
