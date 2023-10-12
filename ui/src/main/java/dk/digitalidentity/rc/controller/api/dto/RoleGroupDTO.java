package dk.digitalidentity.rc.controller.api.dto;

import dk.digitalidentity.rc.attestation.model.dto.UserRoleDTO;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RoleGroupDTO {
	private long id;
	private String name;
   	private String description;
   	private List<UserRoleDTO> roles;
}
