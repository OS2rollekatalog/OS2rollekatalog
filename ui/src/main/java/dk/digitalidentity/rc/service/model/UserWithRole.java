package dk.digitalidentity.rc.service.model;

import dk.digitalidentity.rc.dao.model.User;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserWithRole {
	private User user;
	private AssignedThrough assignedThrough;
	private RoleAssignedToUserDTO assignment;
}
