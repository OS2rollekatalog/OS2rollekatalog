package dk.digitalidentity.rc.service.model;

import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRoleAssignedToUser {
	private UserRole userRole;
	private AssignedThrough assignedThrough;
}
