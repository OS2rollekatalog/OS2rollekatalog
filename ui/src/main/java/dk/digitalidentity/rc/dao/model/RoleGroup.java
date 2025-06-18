package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dk.digitalidentity.rc.log.AuditLoggable;
import dk.digitalidentity.rc.rolerequest.model.enums.ApproverOption;
import dk.digitalidentity.rc.rolerequest.model.enums.RequesterOption;
import jakarta.persistence.*;
import lombok.Data;

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
	@Enumerated(EnumType.STRING)
	private RequesterOption requesterPermission = RequesterOption.NONE;

	@Column
	@Enumerated(EnumType.STRING)
	private ApproverOption approverPermission = ApproverOption.ADMINONLY;

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
