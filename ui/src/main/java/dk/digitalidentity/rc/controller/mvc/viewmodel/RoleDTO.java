package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleDTO {
	private long id;
	private String name;
	private String type;
	private String description;
	private String itSystemName;

	public RoleDTO(UserRole userRole) {
		this.id = userRole.getId();
		this.name = userRole.getName();
		this.type = "userRole";
		this.description = userRole.getDescription();
		this.itSystemName = userRole.getItSystem().getName();
	}

	public RoleDTO(RoleGroup roleGroup) {
		this.id = roleGroup.getId();
		this.name = roleGroup.getName();
		this.type = "roleGroup";
		this.description = roleGroup.getDescription();
		this.itSystemName = "";
	}
}

