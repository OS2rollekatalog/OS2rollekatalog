package dk.digitalidentity.rc.attestation.model.dto.enums;

import lombok.Getter;

@Getter
public enum AdminAttestationStatus {
    NOT_STARTED("Ikke begyndt"), ON_GOING("I gang"), FINISHED("Færdig");
    private final String caption;

    AdminAttestationStatus(final String caption) {
        this.caption = caption;
    }
}
