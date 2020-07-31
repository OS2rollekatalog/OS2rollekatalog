package dk.digitalidentity.rc.dao.model;

import lombok.Data;

import javax.persistence.*;

import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.EventType;

import java.util.Date;

@Data
@Entity
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	/// begin "metadata" section

	@Column(nullable = false)
	private Date timestamp;

	@Column
	private String ipAddress;

	@Column(nullable = false)
	private String username;
	
	/// end "metadata" section

	/// begin "audit data" section

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EntityType entityType;

	@Column(nullable = false)
	private String entityId;
	
	@Column(nullable = true)
	private String entityName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = true)
	private EntityType secondaryEntityType;

	@Column(nullable = true)
	private String secondaryEntityId;

	@Column(nullable = true)
	private String secondaryEntityName;
	
	@Column(nullable = true)
	private String description;
	/// end "audit data" section
}
