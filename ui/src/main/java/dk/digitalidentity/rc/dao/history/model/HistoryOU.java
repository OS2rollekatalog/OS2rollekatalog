package dk.digitalidentity.rc.dao.history.model;

import java.time.LocalDate;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import lombok.Getter;

@Entity
@Table(name = "history_ous")
@Getter
public class HistoryOU {

	@Id
	private long id;
	
	@Column
	private LocalDate dato;

	@Column
	private String ouUuid;
	
	@Column
	private String ouName;

	@Column
	private String ouParentUuid;
	
	@Column
	private String ouManagerUuid;
	
	@OneToMany(mappedBy = "historyOU", fetch = FetchType.EAGER)
	private List<HistoryOUUser> users;
}
