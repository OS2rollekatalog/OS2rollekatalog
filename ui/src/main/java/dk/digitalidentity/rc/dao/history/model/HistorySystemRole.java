package dk.digitalidentity.rc.dao.history.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "history_system_roles")
@Getter
public class HistorySystemRole {

	@Id
	private long id;

	@Column
	private long systemRoleId;
	
	@Column
	private String systemRoleName;

	@Column
	private String systemRoleDescription;

	@Column
	private long weight;
	
	@BatchSize(size = 50)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "history_it_systems_id")
	private HistoryItSystem historyItSystem;
}
