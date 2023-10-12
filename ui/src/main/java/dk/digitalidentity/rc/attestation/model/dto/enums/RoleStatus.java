package dk.digitalidentity.rc.attestation.model.dto.enums;

public enum RoleStatus {
	ACTIVE("attestationmodule.enums.roleStatus.active"), INACTIVE("attestationmodule.enums.roleStatus.inactive");

	private String message;
	private RoleStatus(String message){
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
}
