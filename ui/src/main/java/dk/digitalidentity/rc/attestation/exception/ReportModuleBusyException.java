package dk.digitalidentity.rc.attestation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ReportModuleBusyException extends ResponseStatusException {
    private static final long serialVersionUID = 6584944557491815050L;

	public ReportModuleBusyException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Report module in use");
    }
}
