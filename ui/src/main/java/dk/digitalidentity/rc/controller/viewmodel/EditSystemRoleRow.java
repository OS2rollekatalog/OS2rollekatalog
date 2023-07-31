package dk.digitalidentity.rc.controller.viewmodel;

import java.util.HashMap;

import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import lombok.Data;

@Data
public class EditSystemRoleRow {
	private long id;
	private SystemRole systemRole;
	private boolean checked;
	private HashMap<String, SystemRoleAssignmentConstraintValue> selectedConstraints = new HashMap<>();
}
