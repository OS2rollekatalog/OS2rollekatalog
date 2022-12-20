package dk.digitalidentity.rc.dao.model;

import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import dk.digitalidentity.rc.log.AuditLoggable;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity(name = "ous")
public class OrgUnit implements AuditLoggable {

	@Id
	@Column(name = "uuid")
	private String uuid;

	@Column(name = "name")
	private String name;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_uuid")
	private OrgUnit parent;
	
	@UpdateTimestamp
	@Temporal(TemporalType.TIMESTAMP)
	@Column
	private Date lastUpdated;

	@JsonIgnore
	@Column
	private boolean active;
	
	@Column
	private boolean inheritKle;

	@Enumerated(EnumType.STRING)
	@Column(nullable = true)
	private OrgUnitLevel level;

	// TODO: do we REALLY want to cascade everything to the children?
	//       on next major refactor, see what the effects of removing this is,
	//       as it will fix a lot of issues on importing org data
	@OneToMany(fetch = FetchType.LAZY, mappedBy = "parent", cascade = CascadeType.ALL)
	private List<OrgUnit> children;
	
	@BatchSize(size = 50)
	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "orgUnit")
	private List<KLEMapping> kles;

	@BatchSize(size = 50)
	@OneToMany(mappedBy = "orgUnit", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrgUnitUserRoleAssignment> userRoleAssignments;
	
	@BatchSize(size = 50)
	@OneToMany(mappedBy = "orgUnit", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrgUnitRoleGroupAssignment> roleGroupAssignments;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager", nullable = true)
    private User manager;

    // attestation information
    @Column
    private String lastAttestedBy;

    @Column
	@Temporal(TemporalType.TIMESTAMP)
    private Date lastAttested;

    @Column
	@Temporal(TemporalType.TIMESTAMP)
    private Date nextAttestation;    
    
	// lazy does not work, due to some inane proxy stuff, so a ManyToOne is required
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "attestation_pdf")
	private OrgUnitAttestationPdf attestationPdf;
		
	@OneToMany(mappedBy = "orgUnit", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<AuthorizationManager> authorizationManagers;

	@ManyToMany(cascade = CascadeType.ALL)
	@JoinTable(
	  name = "ou_title_mapping", 
	  joinColumns = @JoinColumn(name = "orgunit_uuid"), 
	  inverseJoinColumns = @JoinColumn(name = "title_uuid"))
	private List<Title> titles;
	
	@JsonIgnore
	@Override
	public String getEntityId() {
		return uuid;
	}

	@Override
	public String getEntityName() {
		return name;
	}	
}
