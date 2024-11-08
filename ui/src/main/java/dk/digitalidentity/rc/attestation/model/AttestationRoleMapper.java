package dk.digitalidentity.rc.attestation.model;

import dk.digitalidentity.rc.attestation.model.dto.ItSystemAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.RoleGroupDTO;
import dk.digitalidentity.rc.attestation.model.dto.SystemRoleConstraintDTO;
import dk.digitalidentity.rc.attestation.model.dto.SystemRoleDTO;
import dk.digitalidentity.rc.attestation.model.dto.UserRoleAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.UserRoleDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.AssignedThroughAttestation;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.util.StreamExtensions.distinctByKey;
import static java.util.stream.Collectors.toList;

public class AttestationRoleMapper {

    public static List<RoleGroupDTO> userRoleGroups(final String userUuid, final List<AttestationUserRoleAssignment> userRoleAssignments,
                                                    final Predicate<AttestationUserRoleAssignment> assignmentPredicate) {
        final List<AttestationUserRoleAssignment> allUserAssignmentsWithGroups = userRoleAssignments.stream()
                .filter(r -> r.getRoleGroupId() != null)
                .filter(r -> Objects.equals(r.getUserUuid(), userUuid))
                .filter(assignmentPredicate)
                .toList();
        return allUserAssignmentsWithGroups.stream()
                .filter(distinctByKey(AttestationUserRoleAssignment::getRoleGroupId))
                .map(r -> RoleGroupDTO.builder()
                        .groupName(r.getRoleGroupName())
                        .groupId(r.getRoleGroupId())
                        .groupDescription(r.getRoleGroupDescription())
                        .assignedThrough(AssignedThroughAttestation.valueOf(r.getAssignedThroughType().name()))
                        .assignedThroughName(r.getAssignedThroughName())
                        .userRoles(allUserAssignmentsWithGroups.stream()
                                .filter(r2 -> Objects.equals(r2.getRoleGroupId(), r.getRoleGroupId()))
                                .map(AttestationRoleMapper::mapToUserRoleDto)
                                .collect(Collectors.toList())
                        )
                        .inherited(r.isInherited())
                        .build())
                .collect(Collectors.toList());
    }

    private static UserRoleDTO mapToUserRoleDto(AttestationUserRoleAssignment r) {
        return UserRoleDTO.builder()
                .roleId(r.getUserRoleId())
                .roleName(r.getUserRoleName())
                .roleDescription(r.getUserRoleDescription())
                .itSystemName(r.getItSystemName())
                .assignedThrough(r.getAssignedThroughType() != null
                        ? AssignedThroughAttestation.valueOf(r.getAssignedThroughType().name())
                        : null)
                .assignedThroughName(r.getAssignedThroughName())
                .postponedConstraints(r.getPostponedConstraints())
                .build();
    }


    public static ItSystemAttestationDTO toItSystemAttestationDto(final Attestation attestation, final List<AttestationSystemRoleAssignment> allAssignments) {
        if (attestation.getAttestationType() != Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION) {
            throw new IllegalArgumentException("Unsupported attestationType: " + attestation.getAttestationType());
        }
        final List<AttestationSystemRoleAssignment> allItSystemAssignments = allAssignments.stream()
                .filter(assignment -> assignment.getItSystemId() == attestation.getItSystemId())
                .toList();
        final List<UserRoleAttestationDTO> userRoles = allItSystemAssignments.stream()
                .filter(distinctByKey(AttestationSystemRoleAssignment::getUserRoleId))
                .map(a -> toRoleAssignment(attestation, systemRolesForUserRole(a.getUserRoleId(), allItSystemAssignments)))
                .toList();
        return ItSystemAttestationDTO.builder()
                .createdAt(attestation.getCreatedAt())
                .attestationUuid(attestation.getUuid())
                .itSystemId(attestation.getItSystemId())
                .itSystemName(attestation.getItSystemName())
                .deadLine(attestation.getDeadline())
                .userRoles(userRoles)
                .build();
    }

    private static UserRoleAttestationDTO toRoleAssignment(final Attestation attestationSystemRoles, final List<AttestationSystemRoleAssignment> systemRoleAssignments) {
        final Optional<AttestationSystemRoleAssignment> firstSystemRoleAssignment = systemRoleAssignments.stream().findFirst();
        final String desc = firstSystemRoleAssignment.map(AttestationSystemRoleAssignment::getUserRoleDescription).orElse("");
        final String name = firstSystemRoleAssignment.map(AttestationSystemRoleAssignment::getUserRoleName).orElseThrow();
        final long id = firstSystemRoleAssignment.map(AttestationSystemRoleAssignment::getUserRoleId).orElseThrow();
        final Optional<ItSystemRoleAttestationEntry> attestationUserRoleAttestation = attestationSystemRoles.getItSystemUserRoleAttestationEntries().stream()
                .filter(r -> Objects.equals(r.getUserRoleId(), firstSystemRoleAssignment.map(AttestationSystemRoleAssignment::getUserRoleId).orElse(-1L)))
                .findFirst();
        final String remarks = attestationUserRoleAttestation.map(ItSystemRoleAttestationEntry::getRemarks).orElse(null);
        final boolean verified = attestationUserRoleAttestation.isPresent() && remarks == null;
        return new UserRoleAttestationDTO(name,
                null,
                id,
                desc,
                verified ? attestationUserRoleAttestation.map(ItSystemRoleAttestationEntry::getPerformedByUserId).orElse(null) : null,
                remarks,
                systemRoleAssignments.stream()
                        .map(AttestationRoleMapper::toSystemRole)
                        .collect(toList())
        );
    }

    private static List<AttestationSystemRoleAssignment> systemRolesForUserRole(final long roleId, final List<AttestationSystemRoleAssignment> assignments) {
        return assignments.stream()
                .filter(a -> a.getUserRoleId() == roleId)
                .sorted(Comparator.comparing(AttestationSystemRoleAssignment::getSystemRoleName))
                .collect(toList());
    }


    private static SystemRoleDTO toSystemRole(final AttestationSystemRoleAssignment assignment) {
        return new SystemRoleDTO(assignment.getSystemRoleName(), assignment.getSystemRoleDescription(), assignment.getSystemRoleId(),
                assignment.getConstraints().stream()
                        .map(c -> new SystemRoleConstraintDTO(c.getName(), c.getValueType(), c.getValue()))
                        .collect(Collectors.toList()));
    }

}
