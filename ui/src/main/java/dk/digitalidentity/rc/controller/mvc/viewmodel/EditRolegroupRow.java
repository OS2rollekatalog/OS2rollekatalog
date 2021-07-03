package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditRolegroupRow {
	private String roleUuid;
	private RoleGroup roleGroup;
	private boolean checked;
	private boolean checkedWithInherit; // note, this only makes sense for OU's
	private boolean ouAssignment = true;
	private boolean containsExceptedUsers; // Makes sense for ous
	private Assignment assignment;
	private long ouAssignments; // note, this only makes sense for titles

	// note, this only makes sense for ous (-2 inherit, -1 everyone, 0 not-assigned, 1+ assigned to 1+ titles)
	private int assignmentType;
	
	//added for the purpose of disabling checkboxes on edit user page
	private boolean shouldBeDisabled;
	
	// note, this only makes sense for users
	private AssignedThrough assignedThrough;
}
