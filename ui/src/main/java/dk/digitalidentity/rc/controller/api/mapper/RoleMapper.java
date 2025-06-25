package dk.digitalidentity.rc.controller.api.mapper;

import dk.digitalidentity.rc.controller.api.model.ConstraintTypeAM;
import dk.digitalidentity.rc.controller.api.model.ConstraintTypeSupportAM;
import dk.digitalidentity.rc.controller.api.model.PostponedConstraintAM;
import dk.digitalidentity.rc.controller.api.model.SystemRoleAM;
import dk.digitalidentity.rc.controller.api.model.SystemRoleAssignmentAM;
import dk.digitalidentity.rc.controller.api.model.UserRoleAM;
import dk.digitalidentity.rc.controller.api.model.UserShallowAM;
import dk.digitalidentity.rc.controller.api.model.UserUserRoleAssignmentAM;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ConstraintTypeSupport;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.UserRoleAssignedToUser;
import dk.digitalidentity.rc.service.model.UserRoleAssignmentWithInfo;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.controller.api.mapper.OrgUnitMapper.toShallowApi;
import static dk.digitalidentity.rc.controller.api.mapper.TitleMapper.titleToApi;

public abstract class RoleMapper {

    public static UserUserRoleAssignmentAM userRoleAssignmentToApi(final UserRoleAssignmentWithInfo assignment, final User user) {
        final UserRoleAssignedToUser userRoleAssignedToUser = assignment.toUserRoleAssignedToUser();
        return UserUserRoleAssignmentAM.builder()
                .user(UserMapper.toShallowApi(user))
                .userRole(userRoleToApi(assignment.getUserRole()))
                .responsibleOrgUnit(toShallowApi(userRoleAssignedToUser.getOrgUnit()))
                .assignedThroughTitle(titleToApi(userRoleAssignedToUser.getTitle()))
                .assignedThrough(assignedThroughToApi(userRoleAssignedToUser.getAssignedThrough()))
                .postponedConstraints(postponedConstraintsToApi(assignment.getPostponedConstraints()))
                .build();
    }

    private static UserUserRoleAssignmentAM.AssignedThrough assignedThroughToApi(AssignedThrough assignedThrough) {
        return switch (assignedThrough) {
            case null -> UserUserRoleAssignmentAM.AssignedThrough.DIRECT;
            case DIRECT -> UserUserRoleAssignmentAM.AssignedThrough.DIRECT;
            case ROLEGROUP -> UserUserRoleAssignmentAM.AssignedThrough.ROLE_GROUP;
            case POSITION -> UserUserRoleAssignmentAM.AssignedThrough.POSITION;
            case ORGUNIT -> UserUserRoleAssignmentAM.AssignedThrough.ORG_UNIT;
            case TITLE -> UserUserRoleAssignmentAM.AssignedThrough.TITLE;
        };
    }

    private static List<PostponedConstraintAM> postponedConstraintsToApi(final List<PostponedConstraint> postponedConstraints) {
        if (postponedConstraints == null) {
            return Collections.emptyList();
        }
        return postponedConstraints.stream().map(RoleMapper::postponedConstraintToApi).toList();
    }

    private static PostponedConstraintAM postponedConstraintToApi(final PostponedConstraint pc) {
        return PostponedConstraintAM.builder()
                .constraintTypeId(pc.getConstraintType().getId())
                .constraintTypeEntityId(pc.getConstraintType().getEntityId())
                .systemRoleId(pc.getSystemRole().getId())
                .value(pc.getValue())
                .build();
    }

    public static UserRoleAM userRoleToApi(final UserRole userRole) {
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
                        ? userRole.getSystemRoleAssignments().stream().map(RoleMapper::systemRoleAssignmentToApi).collect(Collectors.toList())
                        : Collections.emptyList())
                .build();
    }

    public static SystemRoleAssignmentAM systemRoleAssignmentToApi(final SystemRoleAssignment systemRoleAssignment) {
        return SystemRoleAssignmentAM.builder()
                .systemRoleId(systemRoleAssignment.getSystemRole().getId())
                .systemRoleIdentifier(systemRoleAssignment.getSystemRole().getIdentifier())
                .constraintValues(ConstraintMapper.toApi(systemRoleAssignment.getConstraintValues()))
                .build();
    }

    public static ConstraintTypeAM constraintTypeToApi(final ConstraintType constraintType) {
        if (constraintType == null) {
            return null;
        }
        return ConstraintTypeAM.builder()
                .id(constraintType.getId())
                .uiType(constraintType.getUiType())
                .regex(constraintType.getRegex())
                .uuid(constraintType.getUuid())
                .entityId(constraintType.getEntityId())
                .name(constraintType.getName())
                .description(constraintType.getDescription())
                .build();
    }


    public static List<ConstraintTypeSupportAM> toConstraintTypeSupportApi(final List<ConstraintTypeSupport> constraintTypeSupports) {
        return constraintTypeSupports.stream().map(RoleMapper::constraintTypeSupportToApi).collect(Collectors.toList());
    }

    public static ConstraintTypeSupportAM constraintTypeSupportToApi(final ConstraintTypeSupport constraintTypeSupport) {
        if (constraintTypeSupport == null) {
            return null;
        }
        return ConstraintTypeSupportAM.builder()
                .mandatory(constraintTypeSupport.isMandatory())
                .constraintType(constraintTypeToApi(constraintTypeSupport.getConstraintType()))
                .build();
    }

    public static List<SystemRoleAM> systemRolesToApi(final List<SystemRole> systemRoleList) {
        return systemRoleList.stream().map(RoleMapper::systemRolesToApi).collect(Collectors.toList());
    }

    public static SystemRoleAM systemRolesToApi(final SystemRole systemRole) {
        if (systemRole == null) {
            return null;
        }
        return SystemRoleAM.builder()
                .id(systemRole.getId())
                .name(systemRole.getName())
                .identifier(systemRole.getIdentifier())
                .description(systemRole.getDescription())
                .supportedConstraintTypes(toConstraintTypeSupportApi(systemRole.getSupportedConstraintTypes()))
                .weight(systemRole.getWeight())
                .build();
    }

    public static ConstraintTypeSupport constraintTypeSupportToEntity(final ConstraintTypeSupportAM constraintTypeSupportAM) {
        if (constraintTypeSupportAM == null) {
            return null;
        }
        final ConstraintTypeSupport constraintTypeSupport = new ConstraintTypeSupport();
        constraintTypeSupport.setMandatory(constraintTypeSupportAM.isMandatory());
        constraintTypeSupport.setConstraintType(constraintTypeToEntity(constraintTypeSupportAM.getConstraintType()));
        return constraintTypeSupport;
    }

    public static ConstraintType constraintTypeToEntity(final ConstraintTypeAM constraintTypeAM) {
        if (constraintTypeAM == null) {
            return null;
        }
        final ConstraintType constraintType = new ConstraintType();
        constraintType.setId(constraintTypeAM.getId());
        constraintType.setName(constraintTypeAM.getName());
        constraintType.setUiType(constraintTypeAM.getUiType());
        constraintType.setRegex(constraintTypeAM.getRegex());
        constraintType.setUuid(constraintTypeAM.getUuid());
        constraintType.setEntityId(constraintTypeAM.getEntityId());
        constraintType.setDescription(constraintTypeAM.getDescription());
        return constraintType;
    }

    public static List<ConstraintTypeSupport> constraintTypeSupportsToEntity(final List<ConstraintTypeSupportAM> constraintTypeSupports) {
        if (constraintTypeSupports == null) {
            return Collections.emptyList();
        }
        return constraintTypeSupports.stream()
                .map(RoleMapper::constraintTypeSupportToEntity)
                .toList();
    }

    public static SystemRole systemRoleToEntity(final SystemRoleAM systemRoleAM) {
        if (systemRoleAM == null) {
            return null;
        }
        final SystemRole systemRole = new SystemRole();
        if (systemRoleAM.getId() != null) {
            systemRole.setId(systemRoleAM.getId());
        }
        systemRole.setName(systemRoleAM.getName());
        systemRole.setIdentifier(systemRoleAM.getIdentifier());
        systemRole.setWeight(systemRoleAM.getWeight() != null ? systemRoleAM.getWeight() : 1);
        systemRole.setDescription(systemRoleAM.getDescription());
        systemRole.setSupportedConstraintTypes(constraintTypeSupportsToEntity(systemRoleAM.getSupportedConstraintTypes()));
        return systemRole;
    }

}
