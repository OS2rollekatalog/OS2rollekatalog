package dk.digitalidentity.rc.dao.model.enums;

public enum OrgUnitLevel {
	LEVEL_1("html.enum.orgunitLevel.1"),
	LEVEL_2("html.enum.orgunitLevel.2"),
	LEVEL_3("html.enum.orgunitLevel.3"),
	LEVEL_4("html.enum.orgunitLevel.4"),
	LEVEL_5("html.enum.orgunitLevel.5"),
	LEVEL_6("html.enum.orgunitLevel.6"),
	NONE("html.enum.orgunitLevel.none");
	
	private String message;
	
	private OrgUnitLevel(String message) {
		this.message = message;
	}
	
	public String getMessage() {
		return message;
	}
}
