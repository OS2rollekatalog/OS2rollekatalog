package dk.digitalidentity.rc.attestation.exception;

public class AttestationDataUpdaterException extends RuntimeException {
    private static final long serialVersionUID = -2106083012583595774L;

	public AttestationDataUpdaterException(String format, Object... args) {
        super(String.format(format, args));
    }
}
