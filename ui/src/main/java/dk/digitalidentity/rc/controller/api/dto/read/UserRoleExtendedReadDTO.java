package dk.digitalidentity.rc.controller.api.dto.read;

import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserRoleExtendedReadDTO {
	private long id;
	private String name;
	private String identifier;
	private String description;
	private String itSystemName;

	public UserRoleExtendedReadDTO(UserRole userRole) {
		this.id = userRole.getId();
		this.name = userRole.getName();
		this.identifier = userRole.getIdentifier();
		this.description = userRole.getDescription();
		this.itSystemName = userRole.getItSystem().getName();
	}
}
