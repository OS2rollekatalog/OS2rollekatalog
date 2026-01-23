package dk.digitalidentity.rc.rolerequest.model.entity;

import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.SystemRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity(name = "req_request_postponed_constraint")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RequestPostponedConstraint {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(name = "constraint_value")
	@NotNull
	private String value;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "role_request_id")
	private RoleRequest roleRequest;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "constraint_type_id")
	private ConstraintType constraintType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "system_role_id")
	private SystemRole systemRole;
}
