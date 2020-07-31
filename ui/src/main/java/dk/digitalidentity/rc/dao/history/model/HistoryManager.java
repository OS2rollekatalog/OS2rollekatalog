package dk.digitalidentity.rc.dao.history.model;

import java.time.LocalDate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;

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
