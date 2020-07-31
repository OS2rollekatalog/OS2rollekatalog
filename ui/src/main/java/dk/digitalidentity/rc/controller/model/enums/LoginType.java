package dk.digitalidentity.rc.controller.model.enums;

public enum LoginType {
	SYSTEM_ROLE("html.enum.logintype.system_role"), USER_ROLE("html.enum.logintype.user_role"), OIO_BPP("html.enum.logintype.OIO_BPP");
	
	private String message;

	private LoginType(String message) {
		this.message = message;
	}
	
	public String getMessage() {
		return message;
	}
}
