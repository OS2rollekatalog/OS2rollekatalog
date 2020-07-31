package dk.digitalidentity.rc.controller.api.dto;

import dk.digitalidentity.rc.dao.model.ConstraintType;
import lombok.Data;

@Data
public class SystemRoleAssignmentConstraintValueDTO {
	private ConstraintType constraintType;
	private String constraintValue;
}
