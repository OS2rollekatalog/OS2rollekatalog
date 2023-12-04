package dk.digitalidentity.rc.dao.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@Entity
@Table(name = "front_page_links")
public class FrontPageLink {
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
}
