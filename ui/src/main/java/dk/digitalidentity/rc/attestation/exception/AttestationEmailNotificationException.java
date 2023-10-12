package dk.digitalidentity.rc.attestation.exception;

public class AttestationEmailNotificationException extends RuntimeException {
    public AttestationEmailNotificationException(String format, Object... args) {
        super(String.format(format, args));
    }
}
