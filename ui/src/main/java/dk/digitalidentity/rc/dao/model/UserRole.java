package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dk.digitalidentity.rc.log.AuditLoggable;
import dk.digitalidentity.rc.rolerequest.model.enums.ApproverOption;
import dk.digitalidentity.rc.rolerequest.model.enums.RequesterOption;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import java.util.List;

@Entity
@Table(name = "user_roles")
@ToString(exclude = { "itSystem", "requesterPermission", "approverPermission" })
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

	@Column
	private boolean sensitiveRole;

	@Column
	private boolean extraSensitiveRole;

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

	@Column
	@Enumerated(EnumType.STRING)
	private RequesterOption requesterPermission = RequesterOption.NONE;

	@Column
	@Enumerated(EnumType.STRING)
	private ApproverOption approverPermission = ApproverOption.ADMINONLY;

	@OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "user_role_email_template_id")
	private UserRoleEmailTemplate userRoleEmailTemplate;

	@Column
	private boolean ouFilterEnabled;

	@OneToMany
	@JoinTable(name = "ous_user_roles", joinColumns = { @JoinColumn(name = "user_roles_id") }, inverseJoinColumns = { @JoinColumn(name = "ou_uuid") })
	@LazyCollection(LazyCollectionOption.TRUE)
	private List<OrgUnit> orgUnitFilterOrgUnits;

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
