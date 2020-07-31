package dk.digitalidentity.rc.dao.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

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
}
