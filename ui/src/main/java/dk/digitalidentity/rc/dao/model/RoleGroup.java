package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dk.digitalidentity.rc.config.ApprovableByListConverter;
import dk.digitalidentity.rc.config.RequestableByListConverter;
import dk.digitalidentity.rc.log.AuditLoggable;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data // TODO: we are depending on Equals() from this, so if/when we refactor to Getter/Setter, then make sure to implement a sane @Equals
@Entity(name = "rolegroup")
public class RoleGroup implements AuditLoggable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(nullable = false, length = 128)
	private String name;

	@Column(nullable = true)
	private String description;

	@Column(nullable = false)
	private boolean userOnly;

	@Column
	@Convert(converter = RequestableByListConverter.class)
	private List<RequestableBy> requesterPermission = new ArrayList<>();

	@Column
	@Convert(converter = ApprovableByListConverter.class)
	private List<ApprovableBy> approverPermission = new ArrayList<>();

	@OneToMany(mappedBy = "roleGroup", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<RoleGroupUserRoleAssignment> userRoleAssignments;

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
