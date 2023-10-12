package dk.digitalidentity.rc.attestation.model.dto.enums;

public enum AttestationStatus {
	APPROVED("attestationmodule.enums.attestationStatus.approved"),
	REMARKS("attestationmodule.enums.attestationStatus.remarks"),
	DELETE("attestationmodule.enums.attestationStatus.delete"),
	NOT_VERIFIED("attestationmodule.enums.attestationStatus.notVerified");

	private String message;
	private AttestationStatus(String message){
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
}
