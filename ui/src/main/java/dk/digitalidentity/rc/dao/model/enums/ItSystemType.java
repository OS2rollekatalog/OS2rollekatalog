package dk.digitalidentity.rc.dao.model.enums;

public enum ItSystemType {
	AD("html.enum.systemtype.ad"),
	SAML("html.enum.systemtype.saml"),
	KOMBIT("html.enum.systemtype.kombit"),
	MANUAL("html.enum.systemtype.manual"),
	KSPCICS("html.enum.systemtype.kspcics");
	
	private String message;

	private ItSystemType(String message) {
		this.message = message;
	}
	
	public String getMessage() {
		return message;
	}
}
