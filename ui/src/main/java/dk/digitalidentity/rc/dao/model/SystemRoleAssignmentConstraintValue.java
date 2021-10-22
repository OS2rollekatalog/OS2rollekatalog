package dk.digitalidentity.rc.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
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
	
	public void setConstraintIdentifier(String identifier) {
		this.constraintIdentifier = identifier;
	}
}
