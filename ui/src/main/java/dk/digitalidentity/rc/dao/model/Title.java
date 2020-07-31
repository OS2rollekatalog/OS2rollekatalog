package dk.digitalidentity.rc.dao.model;

import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.rc.log.AuditLoggable;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "titles")
@Getter
@Setter
public class Title implements AuditLoggable {

	@Id
	@Column
	private String uuid;

	@Column
	private String name;

	@JsonIgnore
	@UpdateTimestamp
	@Temporal(TemporalType.TIMESTAMP)
	@Column
	private Date lastUpdated;

	@JsonIgnore
	@Column
	private boolean active;
	
	@JsonIgnore
	@OneToMany(mappedBy = "title", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<TitleUserRoleAssignment> userRoleAssignments;

	@JsonIgnore
	@OneToMany(mappedBy = "title", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private List<TitleRoleGroupAssignment> roleGroupAssignments;

	@JsonIgnore
	@Override
	public String getEntityName() {
		return name;
	}

	@JsonIgnore
	@Override
	public String getEntityId() {
		return uuid;
	}
}
