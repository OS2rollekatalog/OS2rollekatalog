package dk.digitalidentity.rc.service.model;

import dk.digitalidentity.rc.dao.model.PositionUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserRoleWithAssignmentIdDTO {
	private UserRole userRole;
	private long assignmentId;

	public UserRoleWithAssignmentIdDTO(RoleGroupUserRoleAssignment roleGroupUserRoleAssignment) {
		this.userRole = roleGroupUserRoleAssignment.getUserRole();
		this.assignmentId = roleGroupUserRoleAssignment.getId();
	}
	
	public UserRoleWithAssignmentIdDTO(PositionUserRoleAssignment positionUserRoleAssignment) {
		this.userRole = positionUserRoleAssignment.getUserRole();
		this.assignmentId = positionUserRoleAssignment.getId();
	}
}