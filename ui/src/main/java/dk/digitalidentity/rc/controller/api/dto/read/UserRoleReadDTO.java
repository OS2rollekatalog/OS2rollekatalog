package dk.digitalidentity.rc.controller.api.dto.read;

import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleReadDTO {
	private long id;
	private String name;
	private String itSystemName;
	
	public UserRoleReadDTO(UserRole userRole) {
		this.id = userRole.getId();
		this.name = userRole.getName();
		this.itSystemName = userRole.getItSystem().getName();
	}
}
