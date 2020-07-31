package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import lombok.Data;

@Data
public class PositionDTO {
	private String name;
	private OrgUnitDTO orgUnit;
	private List<UserRoleDTO> roles;
	private List<RoleGroupDTO> roleGroups;
}
