package dk.digitalidentity.rc.dao.model;

import dk.digitalidentity.rc.dao.model.enums.KOMBITEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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
	private LocalDateTime timestamp;
	
	@Column
	private boolean failed;
}
