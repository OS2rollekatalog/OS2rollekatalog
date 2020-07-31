package dk.digitalidentity.rc.exceptions;

public class ItSystemNotFoundException extends Exception {
	private static final long serialVersionUID = 1L;

	public ItSystemNotFoundException() {
		super();
	}
	public ItSystemNotFoundException(String message) {
        super(message);
    }

    public ItSystemNotFoundException(String message, Throwable throwable) {
        super(message, throwable);
    }

}