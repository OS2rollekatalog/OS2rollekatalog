package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserRoleDTO {
	private String name;
	private final ItSystem itSystem;
	private final String description;
	private final List<User> exceptedUsers;

	public UserRoleDTO(UserRole userRole, List<User> exceptedUsers) {
		this.name = userRole.getName();
		this.itSystem = userRole.getItSystem();
		this.description = userRole.getDescription();
		this.exceptedUsers = exceptedUsers;
	}
}
