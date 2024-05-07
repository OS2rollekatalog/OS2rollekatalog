package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

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
	@Column(name = "deleted")
	private boolean deleted;

	@Column(name = "disabled")
	private boolean disabled;

	@Column
	private String nemloginUuid;

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade= CascadeType.ALL, orphanRemoval = true)
	private List<AltAccount> altAccounts;
	
	@OneToMany(mappedBy = "user", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Position> positions;

	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade= CascadeType.ALL, orphanRemoval = true)
	private List<UserKLEMapping> kles;

	@BatchSize(size = 50)
	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<UserUserRoleAssignment> userRoleAssignments;
	
	@BatchSize(size = 50)
	@OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<UserRoleGroupAssignment> roleGroupAssignments;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "domain_id")
	private Domain domain;
	
	@BatchSize(size = 50)
	@OneToMany(mappedBy = "manager", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ManagerSubstitute> managerSubstitutes;

	@BatchSize(size = 50)
	@OneToMany(mappedBy = "substitute", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ManagerSubstitute> substituteFor;

	@JsonIgnore
	@Override
	public String getEntityId() {
		return uuid;
	}

	@Override
	public String getEntityName() {
		return name + " (" + userId + ")";
	}

	@JsonIgnore
	public String getFirstname() {
		if (!StringUtils.hasLength(name)) {
			return "Ukendt";
		}
		
		int idx = name.lastIndexOf(" ");
		if (idx <= 0) {
			return name;
		}
		
		return name.substring(0, idx);
	}
	
	@JsonIgnore
	public String getLastname() {
		if (!StringUtils.hasLength(name)) {
			return "Ukendtsen";
		}

		int idx = name.lastIndexOf(" ");
		if (idx <= 0 || idx == name.length()) {
			return name;
		}

		return name.substring(idx + 1);
	}
}
