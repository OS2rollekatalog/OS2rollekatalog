package dk.digitalidentity.rc.attestation.service.util;

import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.model.entity.AttestationUser;
import dk.digitalidentity.rc.attestation.model.entity.BaseUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static dk.digitalidentity.rc.attestation.model.entity.Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION;

public abstract class AttestationUtil {

    public static boolean isSensitiveUser(final Attestation attestation, final String userUuid) {
        return !attestation.isSensitive() || // if this is sensitive, we only want users with sensitive roles
                        attestation.getUsersForAttestation().stream()
                                .filter(ua -> ua.getUserUuid().equals(userUuid))
                                .findFirst()
                                .map(AttestationUser::isSensitiveRoles)
                                .orElse(false);
    }

    public static boolean hasUserAssignmentAttestationBeenPerformed(final Attestation attestation, final AttestationUserRoleAssignment assignment) {
        return switch (attestation.getAttestationType()) {
            case ORGANISATION_ATTESTATION -> hasUserAssignmentAttestationBeenPerformed(attestation.getOrganisationUserAttestationEntries(), assignment);
            case IT_SYSTEM_ATTESTATION -> hasUserAssignmentAttestationBeenPerformed(attestation.getItSystemUserAttestationEntries(), assignment);
            default -> throw new IllegalArgumentException("Attestation type not supported");
        };
    }

    public static boolean hasOuAssignmentAttestationBeenPerformed(final Attestation attestation, final AttestationOuRoleAssignment assignment) {
        if (attestation.getItSystemOrganisationAttestationEntries() == null) {
            return false;
        }
        return attestation.getItSystemOrganisationAttestationEntries().stream()
                .filter(r -> r.getPerformedByUserUuid() != null)
                .anyMatch(r -> r.getOrganisationUuid().equals(assignment.getOuUuid()));
    }

    public static boolean hasAllOuAttestationsBeenPerformed(final Attestation attestation, final List<AttestationOuRoleAssignment> ouRoleAssignments,
                                                            final Predicate<AttestationOuRoleAssignment> filterPredicate) {
        return ouRoleAssignments.stream()
                .filter(filterPredicate)
                .allMatch(a -> hasOuAssignmentAttestationBeenPerformed(attestation, a));
    }

    public static boolean hasAllUserAttestationsBeenPerformed(final Attestation attestation, final List<AttestationUserRoleAssignment> assignments,
                                                              final Predicate<AttestationUserRoleAssignment> filterPredicate) {
        final AttestationRun run = attestation.getAttestationRun();
        return assignments.stream()
                .filter(filterPredicate)
                .filter(a -> (!run.isSensitive() && !run.isExtraSensitive()) || isSensitiveUser(attestation, a.getUserUuid()))
                .allMatch(a -> hasUserAssignmentAttestationBeenPerformed(attestation, a));
    }

    public static boolean hasAllRoleAssignmentAttestationsBeenPerformed(final Attestation attestation, final Stream<AttestationSystemRoleAssignment> systemRoleAssignments,
                                                                        final Predicate<AttestationSystemRoleAssignment> filterPredicate) {
        return systemRoleAssignments
                .filter(filterPredicate)
                .allMatch(a -> hasRoleAssignmentAttestationBeenPerformed(attestation, a));
    }

    public static boolean hasRoleAssignmentAttestationBeenPerformed(final Attestation attestation, final AttestationSystemRoleAssignment assignment) {
        if (attestation.getAttestationType() == IT_SYSTEM_ROLES_ATTESTATION) {
            return hasRoleAssignmentAttestationBeenPerformed(attestation.getItSystemUserRoleAttestationEntries(), assignment);
        }
        throw new IllegalArgumentException("Trying to check if a role is verified on an user assignment");
    }

    private static <T extends BaseUserAttestationEntry> boolean hasUserAssignmentAttestationBeenPerformed(final Set<T> entries, final AttestationUserRoleAssignment assignment) {
        if (entries == null) {
            return false;
        }
        return entries.stream()
                .filter(r -> r.getPerformedByUserUuid() != null)
                .anyMatch(r -> r.getUserUuid().equals(assignment.getUserUuid()));
    }

    private static boolean hasRoleAssignmentAttestationBeenPerformed(final Set<ItSystemRoleAttestationEntry> entries, final AttestationSystemRoleAssignment assignment) {
        if (entries == null) {
            return false;
        }
        return entries.stream()
                .filter(r -> r.getPerformedByUserId() != null)
                .anyMatch(r -> r.getUserRoleId().equals(assignment.getUserRoleId()));
    }

}
