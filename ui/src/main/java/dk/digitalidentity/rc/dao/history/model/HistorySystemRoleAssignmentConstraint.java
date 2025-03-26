package dk.digitalidentity.rc.dao.history.model;

import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "history_user_roles_system_role_constraints")
@Getter
@Setter
public class HistorySystemRoleAssignmentConstraint {

	@Id
	private long id;
	
	@Column
	private String constraintName;
	
	@Enumerated(EnumType.STRING)
	@Column
	private ConstraintValueType constraintValueType;
	
	@Column
	private String constraintValue;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "history_user_roles_system_roles_id")
	private HistorySystemRoleAssignment historySystemRoleAssignment;

}
