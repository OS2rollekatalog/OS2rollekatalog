package dk.digitalidentity.rc.dao.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonFormat;

import dk.digitalidentity.rc.dao.model.enums.NotificationEntityType;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "notifications")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
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

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
	@Temporal(TemporalType.TIMESTAMP)
	@Column
	private Date created;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
	@UpdateTimestamp
	@Temporal(TemporalType.TIMESTAMP)
	@Column
	private Date lastUpdated;
	
	@Column
	private String message;

	@Column
	private String adminUuid;
	
	@Column
	private String adminName;

}
