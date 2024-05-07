package dk.digitalidentity.rc.dao.model;

import dk.digitalidentity.rc.dao.model.enums.ItSystemChangeEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

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

	@Column(length = 64)
	private String SystemRoleName;

	@Column(length = 128)
	private String SystemRoleIdentifier;

	@Column
	private String systemRoleDescription;

	@Column
	private boolean systemRoleConstraintChanged;
}
