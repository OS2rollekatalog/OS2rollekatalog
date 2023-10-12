package dk.digitalidentity.rc.dao.history.model;

import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

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
	
	@BatchSize(size = 50)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "history_user_roles_system_roles_id")
	private HistorySystemRoleAssignment historySystemRoleAssignment;

}
