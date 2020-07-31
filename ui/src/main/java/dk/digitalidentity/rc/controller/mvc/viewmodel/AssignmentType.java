package dk.digitalidentity.rc.controller.mvc.viewmodel;

public enum AssignmentType {
	NONE("html.enum.assignmenttype.none"),
	DIRECTLY("html.enum.assignmenttype.direct"),
	POSITION("html.enum.assignmenttype.position"),
	ORGUNIT("html.enum.assignmenttype.organisationunit");

	private String value;

	private AssignmentType(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}
}
