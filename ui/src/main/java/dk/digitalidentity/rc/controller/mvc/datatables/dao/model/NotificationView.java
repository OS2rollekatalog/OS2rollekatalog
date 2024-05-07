package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;

import dk.digitalidentity.rc.dao.model.enums.NotificationEntityType;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "view_notifications")
public class NotificationView {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column
	private long id;

	@Column
	private boolean active;

	@Column
	private String affectedEntityUuid;

	@Column
	@Enumerated(EnumType.STRING)
	private NotificationEntityType affectedEntityType;

	@Column
	private String affectedEntityName;

	@Column
	@Enumerated(EnumType.STRING)
	private NotificationType notificationType;

	@Column
	private String created;

	@Column
	private String lastUpdated;
	
	@Column
	private String message;

	@Column
	private String adminUuid;
	
	@Column
	private String adminName;
}
