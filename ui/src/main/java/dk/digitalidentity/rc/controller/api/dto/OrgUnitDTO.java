package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import lombok.Data;

@Data
public class OrgUnitDTO {
	private String uuid;
	private String name;
	private OrgUnitDTO parent;
	private List<UserRoleDTO> roles;
	private List<RoleGroupDTO> roleGroups;
}
