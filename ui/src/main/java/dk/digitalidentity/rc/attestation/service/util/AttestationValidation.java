package dk.digitalidentity.rc.attestation.service.util;

import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemOrganisationAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemUserAttestationEntry;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.Optional;

public class AttestationValidation {

    public static void validateAttestationOfItSystemUserIsNotPerformed(final Attestation attestation, final String userUuid) {
        final Optional<ItSystemUserAttestationEntry> attestationEntry = attestation.getItSystemUserAttestationEntries().stream()
                .filter(r -> Objects.equals(r.getUserUuid(), userUuid))
                .findFirst();
        if (attestationEntry.isPresent() && attestationEntry.get().getRemarks() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User already rejected at " + attestationEntry.get().getCreatedAt().toString());
        }
        if (attestationEntry.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User already approved at " + attestationEntry.get().getCreatedAt().toString());
        }
    }
    public static void validateAttestationOfItSystemOuIsNotPerformed(final Attestation attestation, final String ouUuid) {
        final Optional<ItSystemOrganisationAttestationEntry> attestationEntry = attestation.getItSystemOrganisationAttestationEntries().stream()
                .filter(r -> Objects.equals(r.getOrganisationUuid(), ouUuid))
                .findFirst();
        if (attestationEntry.isPresent() && attestationEntry.get().getRemarks() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organisation already rejected at " + attestationEntry.get().getCreatedAt().toString());
        }
        if (attestationEntry.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organisation already approved at " + attestationEntry.get().getCreatedAt().toString());
        }
    }

    public static void validateAttestationOfItSystemUserRoleIsNotPerformed(final Attestation attestation, final Long roleId) {
        final Optional<ItSystemRoleAttestationEntry> attestationEntry = attestation.getItSystemUserRoleAttestationEntries().stream()
                .filter(r -> Objects.equals(r.getUserRoleId(), roleId))
                .findFirst();
        if (attestationEntry.isPresent() && attestationEntry.get().getRemarks() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role already rejected at " + attestationEntry.get().getCreatedAt().toString());
        }
        if (attestationEntry.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role already approved at " + attestationEntry.get().getCreatedAt().toString());
        }
    }


}
