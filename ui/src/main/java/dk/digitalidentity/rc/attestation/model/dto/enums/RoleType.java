package dk.digitalidentity.rc.attestation.model.dto.enums;

public enum RoleType {
    USERROLE("attestationmodule.enums.roleType.userRole"), ROLEGROUP("attestationmodule.enums.roleType.roleGroup");

    private String message;
    private RoleType(String message){
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
