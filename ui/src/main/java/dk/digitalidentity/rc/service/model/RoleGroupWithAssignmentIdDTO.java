package dk.digitalidentity.rc.service.model;

import dk.digitalidentity.rc.dao.model.PositionRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleGroupWithAssignmentIdDTO {
	private RoleGroup roleGroup;
	private long assignmentId;

	public RoleGroupWithAssignmentIdDTO(UserRoleGroupAssignment urga) {
		this.roleGroup = urga.getRoleGroup();
		this.assignmentId = urga.getId();
	}

	public RoleGroupWithAssignmentIdDTO(PositionRoleGroupAssignment prga) {
		this.roleGroup = prga.getRoleGroup();
		this.assignmentId = prga.getId();
	}
}