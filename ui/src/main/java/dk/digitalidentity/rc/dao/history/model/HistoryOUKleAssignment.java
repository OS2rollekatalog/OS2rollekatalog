package dk.digitalidentity.rc.dao.history.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;

@Entity
@Table(name = "history_ou_kle_assignments")
@Getter
public class HistoryOUKleAssignment {

	@Id
	private long id;
	
	@Column
	private LocalDate dato;

	@Column
	private String ouUuid;

	@Column
	private String assignmentType;
	
	@Column
	private String kleValues;
}
