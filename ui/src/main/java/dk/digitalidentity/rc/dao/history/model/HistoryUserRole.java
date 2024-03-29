package dk.digitalidentity.rc.dao.history.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
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
	
	@BatchSize(size = 50)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "history_it_systems_id")
	private HistoryItSystem historyItSystem;

	@BatchSize(size = 50)
	@OneToMany(mappedBy = "historyUserRole", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<HistorySystemRoleAssignment> historySystemRoleAssignments;

}
