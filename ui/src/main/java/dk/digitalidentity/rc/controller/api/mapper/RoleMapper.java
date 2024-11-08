package dk.digitalidentity.rc.controller.api.mapper;

import dk.digitalidentity.rc.controller.api.model.SystemRoleAssignmentAM;
import dk.digitalidentity.rc.controller.api.model.UserRoleAM;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;

import java.util.Collections;
import java.util.stream.Collectors;

public abstract class RoleMapper {

    public static UserRoleAM toApi(final UserRole userRole) {
        return UserRoleAM.builder()
                .id(userRole.getId())
                .name(userRole.getName())
                .identifier(userRole.getIdentifier())
                .delegatedFromCvr(userRole.getDelegatedFromCvr())
                .description(userRole.getDescription())
                .userOnly(userRole.isUserOnly())
                .canRequest(userRole.isCanRequest())
                .sensitiveRole(userRole.isSensitiveRole())
                .itSystemId(userRole.getItSystem().getId())
                .systemRoleAssignments(userRole.getSystemRoleAssignments() != null
                        ? userRole.getSystemRoleAssignments().stream().map(RoleMapper::toApi).collect(Collectors.toList())
                        : Collections.emptyList())
                .build();
    }

    public static SystemRoleAssignmentAM toApi(final SystemRoleAssignment systemRoleAssignment) {
        return SystemRoleAssignmentAM.builder()
                .systemRoleId(systemRoleAssignment.getSystemRole().getId())
                .constraintValues(ConstraintMapper.toApi(systemRoleAssignment.getConstraintValues()))
                .build();
    }

}
