package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;


import dk.digitalidentity.rc.config.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "view_datatables_users")
public class UserView {

	@Id
	@Column
	private String uuid;

	@Column
	private String name;

	@Column
	private String userId;

	@Column
	private String domain;

	@Column
	private String title;

	@Column
	@Convert(converter = StringListConverter.class)
	private List<String> orgunitUuids;

	@Column
	private boolean disabled;

}
