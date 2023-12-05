package dk.digitalidentity.rc.attestation.exception;

public class AttestationEmailNotificationException extends RuntimeException {
    private static final long serialVersionUID = -8555633422327507486L;

	public AttestationEmailNotificationException(String format, Object... args) {
        super(String.format(format, args));
    }
}
