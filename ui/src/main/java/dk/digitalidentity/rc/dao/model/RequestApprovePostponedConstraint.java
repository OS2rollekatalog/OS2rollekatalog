package dk.digitalidentity.rc.dao.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

@Entity(name = "request_approve_postponed_constraints")
@Getter
@Setter
public class RequestApprovePostponedConstraint {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@Column(name = "constraint_value")
	@NotNull
	private String value;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_approve_id")
	private RequestApprove requestApprove;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "constraint_type_id")
	private ConstraintType constraintType;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "system_role_id")
	private SystemRole systemRole;
}
