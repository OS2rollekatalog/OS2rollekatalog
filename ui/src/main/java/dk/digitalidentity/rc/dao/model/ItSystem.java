package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Data;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import java.util.Date;
import java.util.List;

@Entity
@Table(name = "it_systems")
@Data
public class ItSystem implements AuditLoggable {

	@Id
	@JsonIgnore
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private String uuid;

	@Column
	private Date lastUpdated;

	@Column(nullable = false)
	private String name;

	@JsonIgnore
	@Column(nullable = false, length = 64)
	private String identifier;

	// can be more than one email separated with ;
	@Column(name = "email")
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(name = "system_type")
	private ItSystemType systemType;

	@Column
	private String notes;
	
	@Column
	private boolean paused;

	@Column
	private boolean hidden;

	@Column
	private boolean readonly;

	@Column
	private String subscribedTo;

	@Column
	private boolean canEditThroughApi;
	
	@Column
	private String notificationEmail;

	@Temporal(TemporalType.TIMESTAMP)
	@Column
	private Date deletedTimestamp;

	@Column
	private boolean deleted;

	@Column
	private boolean accessBlocked;

	// TODO: this is the new field for the v2 API's, we should merge this with "canEditThroughApi" above, once 
	//       we figure out the full API configuration.
	@Column
	private boolean apiManagedRoleAssignments;

	@Column
	private boolean ouFilterEnabled;

	@OneToMany
	@JoinTable(name = "ous_itsystems", joinColumns = { @JoinColumn(name = "itsystem_id") }, inverseJoinColumns = { @JoinColumn(name = "ou_uuid") })
	@LazyCollection(LazyCollectionOption.TRUE)
	private List<OrgUnit> orgUnitFilterOrgUnits;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "domain_id")
	private Domain domain;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "attestation_responsible_uuid")
	private User attestationResponsible;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "system_owner_uuid")
	private User systemOwner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "kitos_it_system_id")
	private KitosITSystem kitosITSystem;

	@Column
	private boolean attestationExempt;

	@JsonIgnore
	@Override
	public String getEntityId() {
		return Long.toString(id);
	}

	@Override
	public String getEntityName() {
		return name;
	}
}
