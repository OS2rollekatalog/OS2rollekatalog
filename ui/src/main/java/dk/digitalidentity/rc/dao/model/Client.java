package dk.digitalidentity.rc.dao.model;

import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.ClientIntegrationType;
import dk.digitalidentity.rc.dao.model.enums.VersionStatusEnum;
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
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Client implements AuditLoggable{

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private String name;

	@Column
	private String apiKey;

	@Column
	@Enumerated(EnumType.STRING)
	private AccessRole accessRole;

	@Column
	private String version;

	@Column
	private String tlsVersion;

	@Column
	private String applicationIdentifier;

	@Column
	private String newestVersion;

	@Column
	private String minimumVersion;

	@Column
	@Enumerated(EnumType.STRING)
	private VersionStatusEnum versionStatus;

	@Column
	@Enumerated(EnumType.STRING)
	private ClientIntegrationType clientIntegrationType = ClientIntegrationType.GENERIC;

	// only used if ClientIntegrationType is AD_SYNC_SERVICE
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "domain_id")
	private Domain domain;


	@Override
	public String getEntityName() {
		return name;
	}

	@Override
	public String getEntityId() {
		return Long.toString(id);
	}
}
