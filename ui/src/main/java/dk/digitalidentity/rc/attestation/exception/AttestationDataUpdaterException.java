package dk.digitalidentity.rc.attestation.exception;

public class AttestationDataUpdaterException extends RuntimeException {
    public AttestationDataUpdaterException(String format, Object... args) {
        super(String.format(format, args));
    }
}
