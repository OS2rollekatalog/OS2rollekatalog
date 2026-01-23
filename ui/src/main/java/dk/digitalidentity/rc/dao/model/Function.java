package dk.digitalidentity.rc.dao.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Getter;
import lombok.Setter;

@Entity(name = "functions")
@Getter
@Setter
public class Function implements AuditLoggable {

	@Id
	@Column
	private String uuid;

	@Column
	private String name;

	@JsonIgnore
	@UpdateTimestamp
	@Temporal(TemporalType.TIMESTAMP)
	@Column
	private LocalDateTime lastUpdated;

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
