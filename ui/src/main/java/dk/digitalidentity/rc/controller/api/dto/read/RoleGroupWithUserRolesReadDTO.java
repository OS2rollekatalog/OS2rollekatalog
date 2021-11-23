package dk.digitalidentity.rc.controller.api.dto.read;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RoleGroupWithUserRolesReadDTO {
	private long id;
	private String name;
	private List<UserRoleReadDTO> roles;
}
