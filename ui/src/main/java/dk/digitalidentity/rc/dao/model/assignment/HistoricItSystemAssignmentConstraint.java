package dk.digitalidentity.rc.dao.model.assignment;

import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Entity
@Table(name = "historic_it_system_assignment_constraint")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistoricItSystemAssignmentConstraint {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "historic_it_system_assignment_id", nullable = false,
		foreignKey = @ForeignKey(name = "fk_historic_it_system_assignment_constraint_assignment_id"))
	private HistoricItSystemAssignment historicItSystemAssignment;

	private String constraintName;

	@Enumerated(EnumType.STRING)
	private ConstraintValueType constraintValueType;

	@Column(columnDefinition = "TEXT")
	private String constraintValue;
}
