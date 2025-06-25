package dk.digitalidentity.rc.dao.history.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;

@Entity
@Table(name = "history_attestation_manager_delegate")
@Getter
public class HistoryAttestationManagerDelegate {

	@Id
	private long id;

	@Column
	private LocalDate date;

	@Column
	private String delegateUuid;

	@Column
	private String delegateName;

	@Column
	private String managerUuid;

	@Column
	private String managerName;
}