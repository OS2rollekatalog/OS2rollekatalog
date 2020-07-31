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

import org.hibernate.annotations.CreationTimestamp;

import dk.digitalidentity.rc.dao.model.enums.KOMBITEventType;
import lombok.Data;

@Entity
@Table(name = "pending_kombit_updates")
@Data
public class PendingKOMBITUpdate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(name = "user_role_uuid")
	private String userRoleUuid;

	@Column(name = "user_role_id")
	private long userRoleId;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private KOMBITEventType eventType;
	
	@CreationTimestamp
	@Column
	private Date timestamp;
}
