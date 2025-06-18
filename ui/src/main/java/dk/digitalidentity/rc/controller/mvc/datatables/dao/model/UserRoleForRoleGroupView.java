package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "vw_all_userroles_with_rolegroups")
public class UserRoleForRoleGroupView {

	@EmbeddedId
	private UserRoleForRoleGroupId compositeKey;

	private String name;
	private String description;
	private String itSystemName;
	private Boolean selected;
	private Boolean readOnly;
}

