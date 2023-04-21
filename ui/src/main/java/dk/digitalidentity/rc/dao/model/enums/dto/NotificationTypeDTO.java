package dk.digitalidentity.rc.dao.model.enums.dto;

import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationTypeDTO {
	private NotificationType notificationType;
	private String message;
}
