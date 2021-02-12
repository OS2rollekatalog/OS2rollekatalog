package dk.digitalidentity.rc.controller.api.dto;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ItSystemWithSystemRolesDTO {
	private long id;
	private String identifier;
	private List<SystemRoleDTO> systemRoles = new ArrayList<SystemRoleDTO>();
	private boolean convertRolesEnabled = false;
	
	// read only
	private List<UserRoleDTO> userRoles = new ArrayList<UserRoleDTO>();

	@NotBlank
	private String name;

	public ItSystemWithSystemRolesDTO(long id, String name, String identifier) {
		this.id = id;
		this.name = name;
		this.identifier = identifier;
	}
}
