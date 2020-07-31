package dk.digitalidentity.rc.dao.history.model;

import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import lombok.Getter;

@Entity
@Table(name = "history_user_roles_system_roles")
@Getter
public class HistorySystemRoleAssignment {

	@Id
	private long id;
	
	@Column
	private String systemRoleName;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "history_user_roles_id")
	private HistoryUserRole historyUserRole;

	@OneToMany(mappedBy = "historySystemRoleAssignment", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<HistorySystemRoleAssignmentConstraint> historyConstraints;
}
