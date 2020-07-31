package dk.digitalidentity.rc.service.model;

public enum AssignedThrough {
	DIRECT("html.assigned.direct"),
	ROLEGROUP("html.assigned.rolegroup"),
	POSITION("html.assigned.position"),
	ORGUNIT("html.assigned.orgunit"),
	TITLE("html.assigned.title");

	private String message;

	private AssignedThrough(String message){
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
}