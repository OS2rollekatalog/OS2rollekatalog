package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserWithDuplicateRoleAssignmentDTO {
	private String uuid;
	private String name;
	private String userId;
	private UserRole userRole;
	private RoleGroup roleGroup;
	private String message;
}
