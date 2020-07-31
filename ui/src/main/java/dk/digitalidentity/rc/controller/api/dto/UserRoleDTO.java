package dk.digitalidentity.rc.controller.api.dto;

import java.util.List;

import lombok.Data;

@Data
public class UserRoleDTO {
	private String name;
	private String identifier;
	private List<SystemRoleAssignmentDTO> systemRoleAssignments;
}
