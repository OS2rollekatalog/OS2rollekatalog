package dk.digitalidentity.rc.controller.v2.api.model;

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
