package dk.digitalidentity.rc.dao.history.model;

import java.time.LocalDate;
import java.util.List;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
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
	
	@ElementCollection(targetClass = String.class)
	@CollectionTable(name = "history_ous_users", joinColumns = @JoinColumn(name = "history_ous_id"))
	@Column(name = "user_uuid")
	private List<String> userUuids;
}
