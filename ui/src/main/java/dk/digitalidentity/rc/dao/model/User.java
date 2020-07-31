package dk.digitalidentity.rc.dao.model;

import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.rc.log.AuditLoggable;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "users")
@Getter
@Setter
public class User implements AuditLoggable {

	@Id
	@Column(name = "uuid")
	private String uuid;
	
	@Column(name = "ext_uuid")
	private String extUuid;

	@Column(name = "user_id")
	private String userId;
	
	@Column(name = "cpr")
	private String cpr;

	@Column(name = "name")
	private String name;
	
	@Column(name = "email")
	private String email;
	
	@Column(name = "phone")
	private String phone;
	
	@UpdateTimestamp
	@Temporal(TemporalType.TIMESTAMP)
	@Column
	private Date lastUpdated;

	@JsonIgnore
	@Column(name = "active")
	private boolean active;

	@Column(name = "do_not_inherit")
	private boolean doNotInherit;

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade= CascadeType.ALL, orphanRemoval = true)
	private List<AltAccount> altAccounts;
	
	@OneToMany(mappedBy = "user", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Position> positions;

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade= CascadeType.ALL, orphanRemoval = true)
	private List<UserKLEMapping> kles;

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<UserUserRoleAssignment> userRoleAssignments;
	
	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<UserRoleGroupAssignment> roleGroupAssignments;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "manager_substitute", nullable = true)
	private User managerSubstitute;

	@JsonIgnore
	@Override
	public String getEntityId() {
		return uuid;
	}

	@Override
	public String getEntityName() {
		return name + " (" + userId + ")";
	}
}
