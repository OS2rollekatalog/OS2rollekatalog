package dk.digitalidentity.rc.controller.api.dto.read;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserReadWrapperDTO {
	private long roleId;
	private String roleIdentifier;
	private String roleName;
	private List<UserReadRoleSystemRole> systemRoles;
	private List<UserReadDTO> assignments;
}
