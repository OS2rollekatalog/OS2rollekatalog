package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;
import lombok.Data;

@Data
public class UserDTO {
	private String uuid;
	private String userId;
	private String name;
	private List<OrgUnitDTO> ous;
	private List<UserRoleDTO> roles;
	private List<RoleGroupDTO> rolegroups;
	private List<PositionDTO> positions;
}
