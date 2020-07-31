package dk.digitalidentity.rc.dao.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.hibernate.annotations.CreationTimestamp;

import dk.digitalidentity.rc.dao.model.enums.PendingOrganisationEventType;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pending_organisation_updates")
@Getter
@Setter
public class PendingOrganisationUpdate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	private String userUuid;
	
	@Column(name = "orgunit_uuid")
	private String orgUnitUuid;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private PendingOrganisationEventType eventType;
	
	@CreationTimestamp
	@Temporal(TemporalType.TIMESTAMP)
	@Column
	private Date timestamp;
}
