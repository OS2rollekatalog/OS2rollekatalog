package dk.digitalidentity.rc.rolerequest.model.enums;

public enum ReasonOption {
	NONE("html.enum.settings.reason.option.none"),
	POSSIBLE("html.enum.settings.reason.option.possible"),
	OBLIGATORY("html.enum.settings.reason.option.obligatory");

	private String message;

	private ReasonOption(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
}
