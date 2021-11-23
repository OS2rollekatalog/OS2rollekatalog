package dk.digitalidentity.rc.controller.mvc.viewmodel;

import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SystemRoleAssignmentConstraintValueDTO {
	private long id;
	private SystemRoleAssignment systemRoleAssignment;
	private ConstraintType constraintType;
	private ConstraintValueType constraintValueType;
	private String constraintValue;
	private String constraintIdentifier;
	private boolean postponed;
	
	public SystemRoleAssignmentConstraintValueDTO(SystemRoleAssignmentConstraintValue systemRoleAssignmentConstraintValue) {
		this.id = systemRoleAssignmentConstraintValue.getId();
		this.systemRoleAssignment = systemRoleAssignmentConstraintValue.getSystemRoleAssignment();
		this.constraintType = systemRoleAssignmentConstraintValue.getConstraintType();
		this.constraintValueType = systemRoleAssignmentConstraintValue.getConstraintValueType();
		this.constraintValue = systemRoleAssignmentConstraintValue.getConstraintValue();
		this.constraintIdentifier = systemRoleAssignmentConstraintValue.getConstraintIdentifier();
		this.postponed = systemRoleAssignmentConstraintValue.isPostponed();
	}
}
