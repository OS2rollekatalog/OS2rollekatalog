package dk.digitalidentity.rc.service.model;

public enum RoleAssignmentType {
	ROLEGROUP("html.role.assignment.type.rolegroup"),
	USERROLE("html.role.assignment.type.userrole"),
	NEGATIVE_ROLEGROUP("html.role.assignment.type.negative_rolegroup"),
	NEGATIVE("html.role.assignment.type.negative");

	private String message;

	private RoleAssignmentType(String message){
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
}
