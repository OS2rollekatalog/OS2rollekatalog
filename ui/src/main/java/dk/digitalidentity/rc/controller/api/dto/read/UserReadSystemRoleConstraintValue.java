package dk.digitalidentity.rc.controller.api.dto.read;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserReadSystemRoleConstraintValue {
	private String constraintType;
	private String constraintValue;
}
