package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import lombok.Data;

@Data
public class SystemRoleAssignmentDTO {
	private SystemRoleDTO systemRole;
	private List<SystemRoleAssignmentConstraintValueDTO> constraintValues;
}
