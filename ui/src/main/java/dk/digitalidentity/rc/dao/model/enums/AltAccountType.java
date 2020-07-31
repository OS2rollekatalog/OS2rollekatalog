package dk.digitalidentity.rc.dao.model.enums;

public enum AltAccountType {
	KSPCICS("html.enum.altaccounttype.kspcics");
	
	private String message;

	private AltAccountType(String message) {
		this.message = message;
	}
	
	public String getMessage() {
		return message;
	}
}
