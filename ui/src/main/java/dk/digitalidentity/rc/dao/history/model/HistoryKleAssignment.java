package dk.digitalidentity.rc.dao.history.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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
	
	// used by reporting module, when merging with inherited from OUs. This data is not stored in the database
	@Transient
	private transient String inheritedFrom;
	
	public HistoryKleAssignment(String userUuid, String assignmentType, String kleValues, String inheritedFrom) {
		this.userUuid = userUuid;
		this.assignmentType = assignmentType;
		this.kleValues = kleValues;
		this.inheritedFrom = inheritedFrom;
	}

	public HistoryKleAssignment(String userUuid, String assignmentType, String kleValues) {
		this.userUuid = userUuid;
		this.assignmentType = assignmentType;
		this.kleValues = kleValues;
	}
	
	public void setKleValues(String kleValues) {
		this.kleValues = kleValues;
	}
}
