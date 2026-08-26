package dk.digitalidentity.rc.dao.model.assignment;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "future_assignment")
@Getter
@Setter
public class FutureAssignment {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column
	private LocalDate startDate;

	@ManyToOne
	@JoinColumn(name = "historic_assignment_id")
	private HistoricAssignment historicAssignment;

}
