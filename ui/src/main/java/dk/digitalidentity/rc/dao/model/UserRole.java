package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.ToString;

import java.util.List;

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

	@Column(nullable = false, length = 128)
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

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "linked_system_role")
	private SystemRole linkedSystemRole;

	@Column(nullable = true)
	private String linkedSystemRolePrefix;

	@Column(nullable = false)
	private boolean allowPostponing;

	@Column(nullable = false)
	private boolean requireManagerAction;

	@Column(nullable = false)
	private boolean sendToSubstitutes;

	@Column(nullable = false)
	private boolean sendToAuthorizationManagers;

	@Column
	private boolean roleAssignmentAttestationByAttestationResponsible;

	@OneToOne(mappedBy = "userRole", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private UserRoleEmailTemplate userRoleEmailTemplate;

	@JsonIgnore
	@Override
	public String getEntityId() {
		return Long.toString(id);
	}

	@Override
	public String getEntityName() {
		return name + " (" + itSystem.getName() + ")";
	}

	public void setName(String name) {
		if (name != null && name.length() > 128) {
			this.name = name.substring(0, 128);
		}
		else {
			this.name = name;
		}
	}
}
