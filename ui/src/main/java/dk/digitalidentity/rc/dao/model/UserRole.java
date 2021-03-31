package dk.digitalidentity.rc.dao.model;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.rc.log.AuditLoggable;
import lombok.Data;
import lombok.ToString;

@Entity
@Table(name = "user_roles")
@ToString(exclude = { "itSystem" })
@Data // TODO: we are depending on Equals() from this, so if/when we refactor to Getter/Setter, then make sure to implement a sane @Equals
public class UserRole implements AuditLoggable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private String uuid;

	@Column(nullable = false, length = 64)
	private String name;

	@Column(nullable = false, length = 128)
	private String identifier;

	@Column(nullable = true)
	private String description;
	
	@Column(nullable = true)
	private String delegatedFromCvr;

	@Column(nullable = false)
	private boolean userOnly;
	
	@Column(nullable = false)
	private boolean canRequest;
	
	@Column
	private boolean sensitiveRole;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "it_system_id")
	private ItSystem itSystem;

	@OneToMany(mappedBy = "userRole", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	private List<SystemRoleAssignment> systemRoleAssignments;

	@JsonIgnore
	@Override
	public String getEntityId() {
		return Long.toString(id);
	}

	@Override
	public String getEntityName() {
		return name + " (" + itSystem.getName() + ")";
	}
}
