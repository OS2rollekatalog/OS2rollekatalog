package dk.digitalidentity.rc.dao.model;

import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "kitos_it_system")
public class KitosITSystem  implements AuditLoggable {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	@NotNull
	private UUID kitosUuid;

	@Column
	@NotNull
	private String name;

	@OneToMany(mappedBy = "kitosITSystem", fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
	private List<KitosITSystemUser> kitosUsers = new ArrayList<>();

	@Override
	public String getEntityName() {
		return name;
	}

	@Override
	public String getEntityId() {
		return Long.toString(id);
	}
}
