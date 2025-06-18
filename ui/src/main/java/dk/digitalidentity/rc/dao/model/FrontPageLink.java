package dk.digitalidentity.rc.dao.model;

import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

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

	@Override
	public String getEntityName() {
		return title;
	}

	@Override
	public String getEntityId() {
		return Long.toString(id);
	}
}
