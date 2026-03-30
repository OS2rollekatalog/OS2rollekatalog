package dk.digitalidentity.rc.dao.model;

import dk.digitalidentity.rc.dao.model.enums.LinkType;
import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "front_page_links")
public class FrontPageLink implements AuditLoggable{
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column
	@NotNull
	private String icon;

	@Column
	@NotNull
	private String title;

	@Column
	@NotNull
	private String description;

	@Column
	@NotNull
	private String link;

	@Column
	@NotNull
	private boolean active;

	@Column
	@NotNull
	private boolean editable;

	@Column
	@NotNull
	private boolean deletable;

	@Column
	@Enumerated(EnumType.STRING)
	private LinkType linkType;

	@Column
	@NotNull
	private int sortOrder;

	@Column(name = "last_changed", nullable = false)
	private LocalDateTime lastChanged;

	@PrePersist
	@PreUpdate
	private void updateLastChanged() {
		this.lastChanged = LocalDateTime.now();
	}

	@Override
	public String getEntityName() {
		return title;
	}

	@Override
	public String getEntityId() {
		return Long.toString(id);
	}
}
