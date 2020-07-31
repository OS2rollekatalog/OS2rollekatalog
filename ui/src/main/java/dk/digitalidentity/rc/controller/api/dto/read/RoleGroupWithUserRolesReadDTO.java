package dk.digitalidentity.rc.controller.api.dto.read;

import lombok.Data;

import java.util.List;

@Data
public class RoleGroupWithUserRolesReadDTO {
	private long id;
	private String name;
	private List<UserRoleReadDTO> roles;
}
