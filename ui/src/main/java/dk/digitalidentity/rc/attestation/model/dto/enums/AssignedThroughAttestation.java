package dk.digitalidentity.rc.attestation.model.dto.enums;

public enum AssignedThroughAttestation {
    DIRECT("attestationmodule.enums.assignedThrough.direct"),
    POSITION("attestationmodule.enums.assignedThrough.position"),
    ORGUNIT("attestationmodule.enums.assignedThrough.orgunit"),
    TITLE("attestationmodule.enums.assignedThrough.title");

    private String message;

    private AssignedThroughAttestation(String message){
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
