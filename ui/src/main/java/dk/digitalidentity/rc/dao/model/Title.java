package dk.digitalidentity.rc.dao.model;

import java.util.Date;

import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
	@Column
	private Date lastUpdated;

	@JsonIgnore
	@Column
	private boolean active;

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
