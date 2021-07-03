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

import org.hibernate.annotations.BatchSize;

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
	
	@BatchSize(size = 50)
	@OneToMany(mappedBy = "historyItSystem", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<HistorySystemRole> historySystemRoles;
	
	@BatchSize(size = 50)
	@OneToMany(mappedBy = "historyItSystem", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<HistoryUserRole> historyUserRoles;
}
