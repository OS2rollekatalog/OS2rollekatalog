package dk.digitalidentity.rc.service.model;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserWithRole {
	private User user;
	private AssignedThrough assignedThrough;
	private RoleAssignedToUserDTO assignment;

	public static UserWithRole fromCurrentAssignment(CurrentAssignment assignment, AssignedThrough assignedThrough, RoleAssignmentType roleAssignmentType) {
		UserWithRole dto = new UserWithRole();
		dto.setUser(assignment.getUser());
		dto.setAssignedThrough(assignedThrough);

		if (RoleAssignmentType.USERROLE.equals(roleAssignmentType)) {
			dto.setAssignment(RoleAssignedToUserDTO.fromCurrentAssignmentUserRole(assignment, assignedThrough));
		} else if (RoleAssignmentType.ROLEGROUP.equals(roleAssignmentType)) {
			dto.setAssignment(RoleAssignedToUserDTO.fromCurrentAssignmentRoleGroup(assignment, assignedThrough));
		}

		return dto;
	}
}
