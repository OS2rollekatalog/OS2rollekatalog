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

import org.hibernate.annotations.BatchSize;

import lombok.Getter;

@Entity
@Table(name = "history_user_roles_system_roles")
@Getter
public class HistorySystemRoleAssignment {

	@Id
	private long id;
	
	@Column
	private String systemRoleName;
	
	@BatchSize(size = 50)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "history_user_roles_id")
	private HistoryUserRole historyUserRole;

	@BatchSize(size = 100)
	@OneToMany(mappedBy = "historySystemRoleAssignment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<HistorySystemRoleAssignmentConstraint> historyConstraints;
}
