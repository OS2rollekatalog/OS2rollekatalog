package dk.digitalidentity.rc.dao.history.model;

import java.time.LocalDate;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import lombok.Getter;

@Entity
@Table(name = "history_it_systems")
@Getter
public class HistoryItSystem {

	@Id
	private long id;
	
	@Column
	private LocalDate dato;

	@Column
	private long itSystemId;
	
	@Column
	private String itSystemName;

	@Column
	private boolean itSystemHidden;
	
	// TODO: find a way to make these SQL JOIN, to improve performance ;)
	
	@OneToMany(mappedBy = "historyItSystem", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<HistorySystemRole> historySystemRoles;
	
	@OneToMany(mappedBy = "historyItSystem", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<HistoryUserRole> historyUserRoles;
}
