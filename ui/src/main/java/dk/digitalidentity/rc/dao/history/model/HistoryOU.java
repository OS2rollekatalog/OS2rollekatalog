package dk.digitalidentity.rc.dao.history.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "history_ous")
@Getter
@Setter
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
	
	@BatchSize(size = 100)
	@OneToMany(mappedBy = "historyOU", fetch = FetchType.LAZY)
	private List<HistoryOUUser> users;
}
