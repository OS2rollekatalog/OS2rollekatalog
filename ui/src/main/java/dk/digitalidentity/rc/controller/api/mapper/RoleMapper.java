package dk.digitalidentity.rc.controller.api.mapper;

import static dk.digitalidentity.rc.controller.api.mapper.TitleMapper.titleToApi;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.controller.api.model.ConstraintTypeAM;
import dk.digitalidentity.rc.controller.api.model.ConstraintTypeSupportAM;
import dk.digitalidentity.rc.controller.api.model.PostponedConstraintAM;
import dk.digitalidentity.rc.controller.api.model.SystemRoleAM;
import dk.digitalidentity.rc.controller.api.model.SystemRoleAssignmentAM;
import dk.digitalidentity.rc.controller.api.model.UserRoleAM;
import dk.digitalidentity.rc.controller.api.model.UserRoleShallowAM;
import dk.digitalidentity.rc.controller.api.model.UserUserRoleAssignmentAM;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ConstraintTypeSupport;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentPostponedConstraint;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.service.model.AssignedThrough;

public abstract class RoleMapper {

	public static UserUserRoleAssignmentAM currentAssignmentToApi(final CurrentAssignment assignment, final User user, final AssignedThrough assignedThrough) {
		return UserUserRoleAssignmentAM.builder()
			.user(UserMapper.toShallowApi(user))
			.userRole(userRoleToApi(assignment.getUserRole()))
			.responsibleOrgUnit(OrgUnitMapper.toShallowApi(assignment.getResponsibleOrgUnit()))
			.assignedThroughTitle(titleToApi(assignment.getTitle()))
			.assignedThrough(assignedThroughToApi(assignedThrough))
			.postponedConstraints(currentAssignmentPostponedConstraintsToApi(assignment.getPostponedConstraints()))
			.build();
	}

	private static List<PostponedConstraintAM> currentAssignmentPostponedConstraintsToApi(
		final Set<CurrentAssignmentPostponedConstraint> postponedConstraints) {
		if (postponedConstraints == null || postponedConstraints.isEmpty()) {
			return Collections.emptyList();
		}
		return postponedConstraints.stream()
			.map(RoleMapper::currentAssignmentPostponedConstraintToApi)
			.toList();
	}

	private static PostponedConstraintAM currentAssignmentPostponedConstraintToApi(
		final CurrentAssignmentPostponedConstraint pc) {
		return PostponedConstraintAM.builder()
			.constraintTypeId(pc.getConstraintTypeId())
			.constraintTypeEntityId(pc.getConstraintTypeEntityId())
			.systemRoleId(pc.getSystemRoleId())
			.value(String.join(",", pc.getValue()))
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

	public static UserRoleShallowAM toShallowApi(final UserRole userRole) {
		return UserRoleShallowAM.builder()
			.id(userRole.getId())
			.identifier(userRole.getIdentifier())
			.name(userRole.getName())
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
                .canRequest(!userRole.getRequesterPermission().contains(RequestableBy.NONE))
                .sensitiveRole(userRole.isSensitiveRole())
                .itSystemId(userRole.getItSystem().getId())
                .systemRoleAssignments(userRole.getSystemRoleAssignments() != null
                        ? userRole.getSystemRoleAssignments().stream().map(RoleMapper::systemRoleAssignmentToApi).collect(Collectors.toList())
                        : Collections.emptyList())
				.contactEmail(userRole.getContactEmail())
				.advisEmail(userRole.getAdvisEmail())
				.ouFilterEnabled(userRole.isOuFilterEnabled())
				.orgUnitFilterOrgUnits(userRole.getOrgUnitFilterOrgUnits() != null
						? userRole.getOrgUnitFilterOrgUnits().stream().map(OrgUnitMapper::toShallowApi).toList()
						: Collections.emptyList())
				.roleAssignmentAttestationByAttestationResponsible(userRole.isRoleAssignmentAttestationByAttestationResponsible())
				.extraSensitiveRole(userRole.isExtraSensitiveRole())
				.allowPostponing(userRole.isAllowPostponing())
                .requesterPermission(userRole.getRequesterPermission())
                .approverPermission(userRole.getApproverPermission())
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
