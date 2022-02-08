package dk.digitalidentity.rc.controller.v2.api.model;

import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SimpleUserRoleDTO {
	private long id;
	private String name;
	private String description;

	public SimpleUserRoleDTO(UserRole userRole) {
		this.id = userRole.getId();
		this.name = userRole.getName();
		this.description = userRole.getDescription();
	}
}
