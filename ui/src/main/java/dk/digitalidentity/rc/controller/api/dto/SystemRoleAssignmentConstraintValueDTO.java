package dk.digitalidentity.rc.controller.api.dto;

import dk.digitalidentity.rc.dao.model.ConstraintType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SystemRoleAssignmentConstraintValueDTO {
	private ConstraintType constraintType;
	private String constraintValue;
}
