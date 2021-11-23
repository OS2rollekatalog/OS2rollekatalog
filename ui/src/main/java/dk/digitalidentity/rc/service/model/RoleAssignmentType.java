package dk.digitalidentity.rc.service.model;

public enum RoleAssignmentType {
	ROLEGROUP("html.role.assignment.type.rolegroup"),
	USERROLE("html.role.assignment.type.userrole");

	private String message;

	private RoleAssignmentType(String message){
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
}
