package dk.digitalidentity.rc.service.kitos.exception;

public class KitosSynchronizationException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	
    public KitosSynchronizationException(final String error) {
        super(error);
    }
}
