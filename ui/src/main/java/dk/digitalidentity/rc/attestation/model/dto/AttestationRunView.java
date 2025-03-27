package dk.digitalidentity.rc.attestation.model.dto;

import java.time.LocalDate;

public interface AttestationRunView {
	Long getId();
	LocalDate getCreatedAt();
	LocalDate getDeadline();
	boolean isSensitive();
	boolean isExtraSensitive();
	boolean isFinished();
}
