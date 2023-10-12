package dk.digitalidentity.rc.controller.api.dto.read;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserReadSystemRoleConstraintValue {
	private String constraintType;
	private String constraintValue;
	private List<UserReadConstraintTypeValueSetDTO> constraintTypeValueSet;
}
