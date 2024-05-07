package dk.digitalidentity.rc.controller.api.mapper;

import dk.digitalidentity.rc.controller.api.model.SystemRoleAssignmentAM;
import dk.digitalidentity.rc.controller.api.model.UserRoleAM;
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
                        ? userRole.getSystemRoleAssignments().stream().map(s -> SystemRoleAssignmentAM.builder().systemRoleId(s.getId()).build()).collect(Collectors.toList())
                        : Collections.emptyList())
                .build();
    }

}
