package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "system_role_assignment_constraint_values")
@Setter
@Getter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@ToString(exclude = { "systemRoleAssignment" })
public class SystemRoleAssignmentConstraintValue {

	@Id
	@JsonIgnore
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@JsonBackReference
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name="system_role_assignment_id")
	private SystemRoleAssignment systemRoleAssignment;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "constraint_type_id")
	private ConstraintType constraintType;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ConstraintValueType constraintValueType;
	
	@Column(nullable = true, length = 4096)
	private String constraintValue;
	
	@Column(nullable = true, length = 128)
	private String constraintIdentifier;
	
	@Column(nullable = false)
	private boolean postponed;
}
