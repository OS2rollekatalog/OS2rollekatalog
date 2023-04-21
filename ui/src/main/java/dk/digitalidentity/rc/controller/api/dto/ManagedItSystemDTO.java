package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManagedItSystemDTO {
	private long itSystemId;
	private String itSystemName;
	private List<SimpleUserRoleDTO> roles;
}
