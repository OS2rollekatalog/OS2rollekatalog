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

import dk.digitalidentity.rc.dao.model.enums.ItSystemChangeEventType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "it_system_updates")
@EqualsAndHashCode(of = { "systemRoleId", "itSystemId", "eventType", "SystemRoleIdentifier" })
@Getter
@Setter
public class ItSystemChange {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@CreationTimestamp
	@Column
	private Date timestamp;

	@Column
	private Long systemRoleId;

	@Column
	private Long itSystemId;

	@Column
	private String itSystemName;

	@Column
	@Enumerated(EnumType.STRING)
	private ItSystemChangeEventType eventType;

	@Column(nullable = false, length = 64)
	private String SystemRoleName;

	@Column(nullable = false, length = 128)
	private String SystemRoleIdentifier;

	@Column
	private String systemRoleDescription;

	@Column
	private boolean systemRoleConstraintChanged;
}
