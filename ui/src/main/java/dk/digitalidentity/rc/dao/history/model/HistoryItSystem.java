package dk.digitalidentity.rc.dao.history.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.time.LocalDate;
import java.util.Set;

@Entity
@Table(name = "history_it_systems")
@Getter
@Setter
public class HistoryItSystem {

	@Id
	private long id;

	@Column
	private LocalDate dato;

	@Column
	private Long itSystemId;
	
	@Column
	private String itSystemName;

	@Column
	private boolean itSystemHidden;

	@Column(name = "attestation_responsible_uuid")
	private String attestationResponsible;

	@Column(name = "attestation_exempt")
	private boolean attestationExempt;

	@BatchSize(size = 50)
	@OneToMany(mappedBy = "historyItSystem", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<HistorySystemRole> historySystemRoles;
	
	@BatchSize(size = 50)
	@OneToMany(mappedBy = "historyItSystem", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<HistoryUserRole> historyUserRoles;
}
