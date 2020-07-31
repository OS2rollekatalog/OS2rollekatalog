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
	private Assignment assignment;
	
	// note, this only makes sense for users
	private AssignedThrough assignedThrough;
	
	// note, this only makes sense for OU's
	private boolean checkedWithInherit;

	// note, this only makes sense for titles
	private long ouAssignments; 
}
