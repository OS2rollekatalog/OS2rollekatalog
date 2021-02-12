package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleAssignmentWithContraints {
	private String roleIdentifier;
	private String roleName;
	
	private List<ConstraintValue> roleConstraintValues;
}
