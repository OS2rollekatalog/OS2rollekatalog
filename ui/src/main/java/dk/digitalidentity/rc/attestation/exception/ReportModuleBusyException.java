package dk.digitalidentity.rc.attestation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ReportModuleBusyException extends ResponseStatusException {
    public ReportModuleBusyException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Report module in use");
    }
}
