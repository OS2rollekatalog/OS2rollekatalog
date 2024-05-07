package dk.digitalidentity.rc.dao.history.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;

@Entity
@Table(name = "history_managers")
@Getter
public class HistoryManager {

	@Id
	private long id;
	
	@Column
	private LocalDate dato;

	@Column
	private String userUuid;
	
	@Column
	private String userName;
}
