package dk.digitalidentity.rc.attestation.service.util;


import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignment;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static dk.digitalidentity.rc.attestation.service.util.AttestationUtil.hasRoleAssignmentAttestationBeenPerformed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * unit tests for {@link AttestationUtil}
 */
public class AttestationUtilTest {

    @Test
    public void hasRoleAssignmentAttestationBeenPerformed_Works() {
        final var attestation = createTestAttestation();
        final var assignment = systemRoleAssignment();
        assertThat(hasRoleAssignmentAttestationBeenPerformed(attestation, assignment))
                .isTrue();
        assignment.setUserRoleId(attestation.getItSystemUserRoleAttestationEntries()
                .stream().findFirst().orElseThrow().getUserRoleId() + 1);
        assertThat(hasRoleAssignmentAttestationBeenPerformed(attestation, assignment))
                .isFalse();
    }

    @Test
    public void hasRoleAssignmentAttestationBeenPerformed_FailsWithAnythingButSystemRoleAssignments() {
        final var assignment = systemRoleAssignment();
        final var attestation = Attestation.builder()
                .attestationType(Attestation.AttestationType.IT_SYSTEM_ATTESTATION)
                .build();
        assertThrows(IllegalArgumentException.class, () -> hasRoleAssignmentAttestationBeenPerformed(attestation, assignment));
        attestation.setAttestationType(Attestation.AttestationType.ORGANISATION_ATTESTATION);
        assertThrows(IllegalArgumentException.class, () -> hasRoleAssignmentAttestationBeenPerformed(attestation, assignment));
    }

    private static AttestationSystemRoleAssignment systemRoleAssignment() {
        return AttestationSystemRoleAssignment.builder()
                .userRoleId(1)
                .build();
    }

    private static Attestation createTestAttestation() {
        return Attestation.builder()
                .attestationType(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION)
                .itSystemUserRoleAttestationEntries(Collections.singleton(
                        ItSystemRoleAttestationEntry.builder()
                                .userRoleId(1L)
                                .performedByUserId("user")
                                .build()
                ))
                .build();
    }

}
