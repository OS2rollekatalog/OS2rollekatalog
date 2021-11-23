package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SystemRoleAssignmentDTO {
	private SystemRoleDTO systemRole;
	private List<SystemRoleAssignmentConstraintValueDTO> constraintValues;
}
