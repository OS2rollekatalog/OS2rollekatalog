package dk.digitalidentity.rc.controller.api.dto.read;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserReadRoleSystemRole {	
	private String roleName;
	private String roleIdentifier;
	private int weight;
	private List<UserReadSystemRoleConstraintValue> roleConstraintValues;
}
