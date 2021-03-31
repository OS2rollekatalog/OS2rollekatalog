package dk.digitalidentity.rc.dao.model;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.rc.log.AuditLoggable;
import lombok.Data;

@Data // TODO: we are depending on Equals() from this, so if/when we refactor to Getter/Setter, then make sure to implement a sane @Equals
@Entity(name = "rolegroup")
public class RoleGroup implements AuditLoggable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(nullable = false, length = 64)
	private String name;

	@Column(nullable = true)
	private String description;
	
	@Column(nullable = false)
	private boolean userOnly;
	
	@Column(nullable = false)
	private boolean canRequest;

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
