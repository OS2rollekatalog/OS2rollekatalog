package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.model.dto.temporal.AttestationUserRoleAssignmentDto;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import dk.digitalidentity.rc.service.model.AssignedThrough;

/**
 * Converts {@link HistoricAssignment} records to {@link AttestationUserRoleAssignment} view objects
 * used throughout the attestation services.
 *
 * <p>The two models represent the same data with different conventions:
 * <ul>
 *   <li>Temporal fields are {@code LocalDateTime} in {@code HistoricAssignment} and {@code LocalDate}
 *       in the attestation model (truncated to day).</li>
 *   <li>{@code AssignedThrough} (domain enum) is collapsed to {@code AssignedThroughType}
 *       (attestation enum) per the documented mapping.</li>
 * </ul>
 */
public final class HistoricAssignmentAttestationMapper {

    private HistoricAssignmentAttestationMapper() {}

    /**
     * Converts a {@link HistoricAssignment} to an {@link AttestationUserRoleAssignment}.
     *
     * <p>{@code validFrom}/{@code validTo} are truncated to {@code LocalDate}.
     * {@code assignedThroughType} is mapped from the domain {@link AssignedThrough} enum.
     */
    public static AttestationUserRoleAssignment toAttestationAssignment(final HistoricAssignment ha) {
        final AssignedThroughType throughType = toAttestationThroughType(ha.getAssignedThroughType());
        final String assignedThroughName = resolveAssignedThroughName(ha, throughType);
        final String assignedThroughUuid = resolveAssignedThroughUuid(ha, throughType);

		AttestationUserRoleAssignment result = AttestationUserRoleAssignment.builder()
            .userUuid(ha.getUserUuid())
            .userId(ha.getUserId())
            .userName(ha.getUserName())
            .userRoleId(ha.getUserRoleId() != null ? ha.getUserRoleId() : 0L)
            .userRoleName(ha.getUserRoleName())
            .userRoleDescription(ha.getUserRoleDescription())
            .roleGroupId(ha.getRoleGroupId())
            .roleGroupName(ha.getRoleGroupName())
            .roleGroupDescription(ha.getRoleGroupDescription())
            .itSystemId(ha.getItSystemId())
            .itSystemName(ha.getItSystemName())
            .responsibleCollectionId(ha.getResponsibleCollectionId())
            .responsibleOuName(ha.getResponsibleOUName())
            .responsibleOuUuid(ha.getResponsibleOUUuid())
            // assignedThroughOUUuid is the OU the role was assigned from (= roleOuUuid in attestation)
            .roleOuUuid(ha.getAssignedThroughOUUuid())
            .roleOuName(ha.getAssignedThroughOUName())
            .assignedThroughType(throughType)
            .assignedThroughName(assignedThroughName)
            .assignedThroughUuid(assignedThroughUuid)
            .inherited(false)
            .sensitiveRole(Boolean.TRUE.equals(ha.getSensitiveRole()))
            .extraSensitiveRole(Boolean.TRUE.equals(ha.getExtraSensitiveRole()))
            .assignedFrom(ha.getValidFrom() != null ? ha.getValidFrom().toLocalDate() : null)
            // Constraints are structured in HistoricAssignment; postponedConstraints field is left null
            // as the attestation display services do not currently use it for HistoricAssignment sources.
            .postponedConstraints(null)
            .build();

		result.setValidFrom(ha.getValidFrom() != null ? ha.getValidFrom().toLocalDate() : null);
		result.setValidTo(ha.getValidTo() != null ? ha.getValidTo().toLocalDate() : null);
		result.setUpdatedAt(ha.getUpdatedAt() != null ? ha.getUpdatedAt().toLocalDate() : null);
		result.setRecordHash(ha.getRecordHash());

		return result;
    }

    /**
     * Converts a {@link HistoricAssignment} to the read-only {@link AttestationUserRoleAssignmentDto}
     * used by report services. This is a plain value object with no Hibernate identity, safe to use
     * outside a transaction.
     */
    public static AttestationUserRoleAssignmentDto toDto(final HistoricAssignment ha) {
        final AssignedThroughType throughType = toAttestationThroughType(ha.getAssignedThroughType());
        return new AttestationUserRoleAssignmentDto(
            ha.getValidFrom() != null ? ha.getValidFrom().toLocalDate() : null,
            ha.getValidTo() != null ? ha.getValidTo().toLocalDate() : null,
            ha.getUpdatedAt() != null ? ha.getUpdatedAt().toLocalDate() : null,
            ha.getRecordHash(),
            ha.getUserUuid(),
            ha.getUserId(),
            ha.getUserName(),
            ha.getUserRoleId() != null ? ha.getUserRoleId() : 0L,
            ha.getUserRoleName(),
            ha.getUserRoleDescription(),
            ha.getRoleGroupId(),
            ha.getRoleGroupName(),
            ha.getRoleGroupDescription(),
            ha.getItSystemId(),
            ha.getItSystemName(),
            ha.getResponsibleCollectionId(),
            ha.getResponsibleOUName(),
            ha.getAssignedThroughOUUuid(),
            ha.getAssignedThroughOUName(),
            ha.getResponsibleOUUuid(),
            throughType,
            resolveAssignedThroughName(ha, throughType),
            resolveAssignedThroughUuid(ha, throughType),
            false,
            Boolean.TRUE.equals(ha.getSensitiveRole()),
            Boolean.TRUE.equals(ha.getExtraSensitiveRole()),
            ha.getValidFrom() != null ? ha.getValidFrom().toLocalDate() : null,
            null
        );
    }

    /**
     * Maps the domain {@link AssignedThrough} enum to the attestation {@link AssignedThroughType} enum.
     *
     * <ul>
     *   <li>DIRECT, ROLEGROUP → DIRECT (role group assignments appear as direct user assignments)</li>
     *   <li>POSITION → DIRECT (deprecated, treated as direct)</li>
     *   <li>ORGUNIT → ORGUNIT</li>
     *   <li>TITLE → TITLE</li>
     * </ul>
     */
    static AssignedThroughType toAttestationThroughType(final AssignedThrough assignedThrough) {
        if (assignedThrough == null) {
            return AssignedThroughType.DIRECT;
        }
        return switch (assignedThrough) {
            case DIRECT, ROLEGROUP, POSITION -> AssignedThroughType.DIRECT;
            case ORGUNIT -> AssignedThroughType.ORGUNIT;
            case TITLE -> AssignedThroughType.TITLE;
        };
    }

    /**
     * Resolves the human-readable "assigned through" name depending on the assignment type.
     * For OU-based assignments this is the OU name; for title-based it is the title name.
     */
    private static String resolveAssignedThroughName(final HistoricAssignment ha, final AssignedThroughType throughType) {
        if (throughType == AssignedThroughType.TITLE) {
            return ha.getAssignedThroughTitleName();
        }
        if (throughType == AssignedThroughType.ORGUNIT) {
            return ha.getAssignedThroughOUName();
        }
        return null;
    }

    /**
     * Resolves the UUID of the entity the role was assigned through.
     * For OU-based assignments this is the OU UUID; for title-based it is the title UUID.
     */
    private static String resolveAssignedThroughUuid(final HistoricAssignment ha, final AssignedThroughType throughType) {
        if (throughType == AssignedThroughType.TITLE) {
            return ha.getAssignedThroughTitleUuid();
        }
        if (throughType == AssignedThroughType.ORGUNIT) {
            return ha.getAssignedThroughOUUuid();
        }
        return null;
    }
}
