package dk.digitalidentity.rc.service.model;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleGroupAssignedToUser {
	private RoleGroup roleGroup;
	private AssignedThrough assignedThrough;
}
