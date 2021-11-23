package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.util.List;

import dk.digitalidentity.rc.dao.model.SystemRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SystemRoleAssignmentDTO {
	private SystemRole systemRole;
	private List<SystemRoleAssignmentConstraintValueDTO> postponedConstraints;
	private String systemRoleName;
}
