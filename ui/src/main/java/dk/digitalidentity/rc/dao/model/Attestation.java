package dk.digitalidentity.rc.dao.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import org.hibernate.annotations.CreationTimestamp;

import lombok.Getter;
import lombok.Setter;

@Entity(name = "attestations")
@Getter
@Setter
public class Attestation {

    @Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "orgunit_uuid")
	private OrgUnit orgUnit;
	
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "user_uuid")
	private User user;
	
	@Column
	private boolean notified;

	@Column
	@CreationTimestamp
	private Date created;
}
