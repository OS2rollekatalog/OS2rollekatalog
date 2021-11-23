package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PositionDTO {
	private String name;
	private OrgUnitDTO orgUnit;
	private List<UserRoleDTO> roles;
	private List<RoleGroupDTO> roleGroups;
}
