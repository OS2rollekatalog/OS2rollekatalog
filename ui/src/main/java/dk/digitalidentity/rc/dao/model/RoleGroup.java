package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dk.digitalidentity.rc.config.ApproverPermissionConverter;
import dk.digitalidentity.rc.config.RequesterPermissionConverter;
import dk.digitalidentity.rc.log.AuditLoggable;
import dk.digitalidentity.rc.rolerequest.model.permission.ApproverPermission;
import dk.digitalidentity.rc.rolerequest.model.permission.RequesterPermission;
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
	@Convert(converter = RequesterPermissionConverter.class)
	private RequesterPermission requesterPermission = RequesterPermission.inherit();

	@Column
	@Convert(converter = ApproverPermissionConverter.class)
	private ApproverPermission approverPermission = ApproverPermission.inherit();

	@OneToMany(mappedBy = "roleGroup", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<RoleGroupUserRoleAssignment> userRoleAssignments;

	@Column
	private boolean ouFilterEnabled;

	@OneToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "ous_rolegroup", joinColumns = { @JoinColumn(name = "rolegroup_id") }, inverseJoinColumns = { @JoinColumn(name = "ou_uuid") })
	private List<OrgUnit> orgUnitFilterOrgUnits;

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
