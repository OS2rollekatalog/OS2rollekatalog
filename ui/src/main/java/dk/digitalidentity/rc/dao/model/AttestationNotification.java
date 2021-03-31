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

import lombok.Getter;
import lombok.Setter;

@Entity(name = "attestation_notifications")
@Getter
@Setter
public class AttestationNotification {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(name = "tts")
	private Date timestamp;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ou_uuid")
	private OrgUnit orgUnit;

}
