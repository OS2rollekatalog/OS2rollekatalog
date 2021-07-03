package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RoleGroupDTO {
	private String name;
	private final String description;
	private final List<User> exceptedUsers;

	public RoleGroupDTO(RoleGroup roleGroup, List<User> exceptedUsers) {
		this.name = roleGroup.getName();
		this.description = roleGroup.getDescription();
		this.exceptedUsers = exceptedUsers;
	}
}
