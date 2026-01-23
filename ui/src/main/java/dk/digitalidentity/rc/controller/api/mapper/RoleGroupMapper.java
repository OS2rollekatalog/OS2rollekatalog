package dk.digitalidentity.rc.controller.api.mapper;

import dk.digitalidentity.rc.controller.api.model.RoleGroupAM;
import dk.digitalidentity.rc.controller.api.model.RoleGroupShallowAM;
import dk.digitalidentity.rc.controller.api.model.UserRoleGroupAssignmentAM;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;

import java.util.Collections;
import java.util.stream.Collectors;

public abstract class RoleGroupMapper {

	public static RoleGroupShallowAM toShallowApi(final RoleGroup roleGroup) {
		return RoleGroupShallowAM.builder()
			.id(roleGroup.getId())
			.name(roleGroup.getName())
			.build();
	}

    public static RoleGroupAM toApi(final RoleGroup roleGroup) {
        return RoleGroupAM.builder()
                .id(roleGroup.getId())
                .name(roleGroup.getName())
                .description(roleGroup.getDescription())
                .userOnly(roleGroup.isUserOnly())
                .canRequest(!roleGroup.getRequesterPermission().contains(RequestableBy.NONE))

                .userRoles(roleGroup.getUserRoleAssignments() != null
                        ? roleGroup.getUserRoleAssignments().stream()
                        .map(a -> UserRoleGroupAssignmentAM.builder()
                                .userRoleId(a.getUserRole().getId())
                                .assignedByUserId(a.getAssignedByUserId())
                                .assignedByName(a.getAssignedByName())
                                .assignedTimestamp(a.getAssignedTimestamp())
                                .build())
                        .collect(Collectors.toList())
                        : Collections.emptyList())
                .build();
    }
}
