package dk.digitalidentity.rc.dao.history.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.BatchSize;

import lombok.Getter;

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
	
	@BatchSize(size = 50)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "history_it_systems_id")
	private HistoryItSystem historyItSystem;
}
