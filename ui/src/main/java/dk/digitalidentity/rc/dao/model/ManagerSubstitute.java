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
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.hibernate.annotations.BatchSize;

import lombok.Getter;
import lombok.Setter;

@Entity(name = "users_manager_substitute")
@Getter
@Setter
public class ManagerSubstitute {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "manager_uuid")
	private User manager;

	@BatchSize(size = 50)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "substitute_uuid")
	private User substitute;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ou_uuid")
	private OrgUnit orgUnit;

	@Column(name = "assigned_by")
	private String assignedBy;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "assigned_tts")
	private Date assignedTts;

}
