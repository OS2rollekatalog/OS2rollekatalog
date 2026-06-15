package dk.digitalidentity.rc.dao.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.rc.config.ApprovableByListConverter;
import dk.digitalidentity.rc.config.RequestableByListConverter;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.log.AuditLoggable;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import lombok.Data;
import lombok.ToString;

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

	// can be more than one email separated with ;
	@Column(name = "advis_email")
	private String advisEmail;

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

	@OneToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "ous_itsystems", joinColumns = { @JoinColumn(name = "itsystem_id") }, inverseJoinColumns = { @JoinColumn(name = "ou_uuid") })
	private List<OrgUnit> orgUnitFilterOrgUnits;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "domain_id")
	private Domain domain;

	@ToString.Exclude
	@OneToMany(mappedBy = "itSystem", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ItSystemAttestationResponsible> attestationResponsibles = new ArrayList<>();

	@ToString.Exclude
	@OneToMany(mappedBy = "itSystem", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ItSystemSystemOwner> systemOwners = new ArrayList<>();

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "kitos_it_system_id")
	private KitosITSystem kitosITSystem;

	@Column
	private boolean attestationExempt;

	@Column
	@Convert(converter = RequestableByListConverter.class)
	private List<RequestableBy> requesterPermission = new ArrayList<>();

	@Column
	@Convert(converter = ApprovableByListConverter.class)
	private List<ApprovableBy> approverPermission = new ArrayList<>();

	@JsonIgnore
	@Override
	public String getEntityId() {
		return Long.toString(id);
	}

	@Override
	public String getEntityName() {
		return name;
	}

	public void addAttestationResponsible(User user) {
		attestationResponsibles.add(ItSystemAttestationResponsible.builder().itSystem(this).user(user).build());
	}

	public void addSystemOwner(User user) {
		systemOwners.add(ItSystemSystemOwner.builder().itSystem(this).user(user).build());
	}
}
