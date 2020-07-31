package dk.digitalidentity.rc.exceptions;

public class OrgUnitNotFoundException extends Exception {
	private static final long serialVersionUID = 1L;

	public OrgUnitNotFoundException(String message) {
		super(message);
	}

	public OrgUnitNotFoundException(String message, Throwable throwable) {
		super(message, throwable);
	}
}