package dk.digitalidentity.rc.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.VersionStatusEnum;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Client {

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
}
