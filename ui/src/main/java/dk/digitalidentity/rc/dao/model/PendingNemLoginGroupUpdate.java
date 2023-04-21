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

import dk.digitalidentity.rc.dao.model.enums.NemLoginGroupEventType;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pending_nemlogin_updates")
@Getter
@Setter
public class PendingNemLoginGroupUpdate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(name = "user_role_uuid")
	private String userRoleUuid;

	@Column(name = "user_role_id")
	private long userRoleId;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private NemLoginGroupEventType eventType;
	
	@CreationTimestamp
	@Column
	private Date created;
	
	@Column
	private boolean failed;
}
