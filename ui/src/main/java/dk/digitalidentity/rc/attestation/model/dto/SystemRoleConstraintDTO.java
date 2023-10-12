package dk.digitalidentity.rc.attestation.model.dto;

import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;

public record SystemRoleConstraintDTO(
        String name,
        ConstraintValueType valueType,
        String value
) {}
