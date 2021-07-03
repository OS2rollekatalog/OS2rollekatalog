package dk.digitalidentity.rc.service.model;

public enum WhoCanRequest {
	USERS("html.enum.who_can_request.users"),
	AUTHORIZATION_MANAGER("html.enum.who_can_request.authorization_manager");
	
	private String message;

	private WhoCanRequest(String message) {
		this.message = message;
	}
	
	public String getMessage() {
		return message;
	}
}
