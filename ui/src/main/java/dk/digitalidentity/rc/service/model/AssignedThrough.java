package dk.digitalidentity.rc.service.model;

public enum AssignedThrough {
	DIRECT("html.assigned.direct"),
	ROLEGROUP("html.assigned.rolegroup"),
	// TODO: deprecated, but logs might use this enum I think, so lets more it in 2027
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