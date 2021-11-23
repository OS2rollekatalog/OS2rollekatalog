package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrgUnitDTO {
	private String uuid;
	private String name;
	private OrgUnitDTO parent;
	private List<UserRoleDTO> roles;
	private List<RoleGroupDTO> roleGroups;
}
