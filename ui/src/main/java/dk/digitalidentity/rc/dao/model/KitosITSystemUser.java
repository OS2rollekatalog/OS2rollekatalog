package dk.digitalidentity.rc.dao.model;

import dk.digitalidentity.rc.dao.model.enums.KitosRole;
import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "kitos_it_system_user")
public class KitosITSystemUser implements AuditLoggable {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(nullable = false)
	private UUID kitosUuid;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private KitosRole role;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "kitos_it_system_id", nullable = false)
	private KitosITSystem kitosITSystem;

	@Override
	public String getEntityName() {
		return name;
	}

	@Override
	public String getEntityId() {
		return Long.toString(id);
	}
}
