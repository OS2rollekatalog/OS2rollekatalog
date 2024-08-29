package dk.digitalidentity.rc.attestation.model.dto.enums;

import lombok.Getter;

@Getter
public enum AttestationAdminColor {
    GREEN("panel-primary"),
    YELLOW("panel-warning"),
    RED("panel-danger"),;
    private final String panelClass;
    AttestationAdminColor(final String panelClass) {
        this.panelClass = panelClass;
    }
}
