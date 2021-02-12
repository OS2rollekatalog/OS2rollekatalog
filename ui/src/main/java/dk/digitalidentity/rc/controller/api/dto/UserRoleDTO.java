package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserRoleDTO {
	private long id;
	private String name;
	private String identifier;
	private List<SystemRoleAssignmentDTO> systemRoleAssignments;
	
	public UserRoleDTO(UserRole userRole) {
		this.id = userRole.getId();
		this.name = userRole.getName();
		this.identifier = userRole.getIdentifier();
	}
}
