package dk.digitalidentity.rc.dao.history.model;

import java.time.LocalDate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "history_kle_assignments")
@Getter
@NoArgsConstructor
public class HistoryKleAssignment {

	@Id
	private long id;
	
	@Column
	private LocalDate dato;

	@Column
	private String userUuid;

	@Column
	private String assignmentType;
	
	@Column
	private String kleValues;
	
	public HistoryKleAssignment(String userUuid, String assignmentType, String kleValues) {
		this.userUuid = userUuid;
		this.assignmentType = assignmentType;
		this.kleValues = kleValues;
	}
	
	public void setKleValues(String kleValues) {
		this.kleValues = kleValues;
	}
}
