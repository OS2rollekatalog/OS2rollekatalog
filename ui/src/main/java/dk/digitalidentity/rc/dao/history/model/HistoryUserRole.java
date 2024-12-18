package dk.digitalidentity.rc.dao.history.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.Set;

@Entity
@Table(name = "history_user_roles")
@Getter
@Setter
public class HistoryUserRole {

	@Id
	private long id;

	@Column
	private long userRoleId;
	
	@Column
	private String userRoleName;

	@Column
	private String userRoleDescription;
	
	@Column
	private String userRoleDelegatedFromCvr;

	@Column
	private boolean roleAssignmentAttestationByAttestationResponsible;

	@Column(name = "`sensitive_role`")
	private Boolean sensitiveRole;

	@Column(name = "`extra_sensitive_role`")
	private Boolean extraSensitiveRole;
	
	@BatchSize(size = 50)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "history_it_systems_id")
	private HistoryItSystem historyItSystem;

	@BatchSize(size = 50)
	@OneToMany(mappedBy = "historyUserRole", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<HistorySystemRoleAssignment> historySystemRoleAssignments;

}
