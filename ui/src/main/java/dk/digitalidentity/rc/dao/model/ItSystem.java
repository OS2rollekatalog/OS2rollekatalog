package dk.digitalidentity.rc.dao.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.log.AuditLoggable;
import lombok.Data;

@Entity
@Table(name = "it_systems")
@Data
public class ItSystem implements AuditLoggable {

	@Id
	@JsonIgnore
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private String uuid;

	@Column
	private Date lastUpdated;

	@Column(nullable = false, length = 64)
	private String name;

	@JsonIgnore
	@Column(nullable = false, length = 64)
	private String identifier;

	@Column(name = "email")
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(name = "system_type")
	private ItSystemType systemType;

	@Column
	private String notes;
	
	@Column
	private boolean paused;

	@Column
	private boolean hidden;

	@Column
	private boolean readonly;

	@Column
	private String subscribedTo;

	@Column
	private boolean canEditThroughApi;
	
	@Column
	private String notificationEmail;

	@Temporal(TemporalType.TIMESTAMP)
	@Column
	private Date deletedTimestamp;

	@Column
	private boolean deleted;

	@Column
	private boolean accessBlocked;

	@JsonIgnore
	@Override
	public String getEntityId() {
		return Long.toString(id);
	}

	@Override
	public String getEntityName() {
		return name;
	}
}
