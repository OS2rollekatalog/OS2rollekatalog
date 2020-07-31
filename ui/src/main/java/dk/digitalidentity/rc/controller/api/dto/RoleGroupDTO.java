package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import lombok.Data;

@Data
public class RoleGroupDTO {
	private long id;
	private String name;
   	private String description;
   	private List<UserRoleDTO> roles;
}
