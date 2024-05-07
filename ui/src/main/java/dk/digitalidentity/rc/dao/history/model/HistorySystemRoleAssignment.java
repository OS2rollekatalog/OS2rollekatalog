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
@Table(name = "history_user_roles_system_roles")
@Getter
@Setter
public class HistorySystemRoleAssignment {

	@Id
	private long id;
	
	@Column
	private String systemRoleName;

	@Column
	private Long systemRoleId;

	@Column
	private String systemRoleDescription;

	@BatchSize(size = 50)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "history_user_roles_id")
	private HistoryUserRole historyUserRole;

	@BatchSize(size = 100)
	@OneToMany(mappedBy = "historySystemRoleAssignment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<HistorySystemRoleAssignmentConstraint> historyConstraints;
}
