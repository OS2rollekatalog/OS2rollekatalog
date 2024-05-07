package dk.digitalidentity.rc.dao.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dk.digitalidentity.rc.dao.model.enums.ADGroupType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;

@Entity
@Table(name = "pending_ad_group_operations")
@Getter
@Setter
public class PendingADGroupOperation {

	@JsonIgnore
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@JsonIgnore
	@CreationTimestamp
	@Column
	private Date timestamp;

	@JsonIgnore
	@Column(nullable = true)
	private Long systemRoleId;
	
	@Column
	private String systemRoleIdentifier;
	
	@Column
	private String itSystemIdentifier;
	
	// active = 1 is CREATE
	// active = 0 is DELETE
	@Column
	private boolean active;

	@Column
	@Enumerated(EnumType.STRING)
	private ADGroupType adGroupType;
	
	@Column
	private boolean universal;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "domain_id")
	private Domain domain;
}
