package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditUserRoleRow {
	private String roleUuid;
	private UserRole role;
	private boolean checked;
	private boolean canCheck;
	private boolean ouAssignment = true;
	private boolean containsExceptedUsers;
	private Assignment assignment;
	
	// note, this only makes sense for users
	private AssignedThrough assignedThrough;
	
	// note, this only makes sense for OU's
	private boolean checkedWithInherit;

	// note, this only makes sense for titles
	private long ouAssignments; 
	
	// note, this only makes sense for ous (-2 inherit, -1 everyone, 0 not-assigned, 1+ assigned to 1+ titles)
	private int assignmentType;
	
	//added for the purpose of disabling checkboxes on edit user page
	private boolean shouldBeDisabled;

}
