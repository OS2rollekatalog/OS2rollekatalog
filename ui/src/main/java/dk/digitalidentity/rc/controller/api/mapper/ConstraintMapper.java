package dk.digitalidentity.rc.controller.api.mapper;

import dk.digitalidentity.rc.controller.api.model.ConstraintTypeAM;
import dk.digitalidentity.rc.controller.api.model.ConstraintValueTypeAM;
import dk.digitalidentity.rc.controller.api.model.SystemRoleAssignmentConstraintValueAM;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;

import java.util.List;

public abstract class ConstraintMapper {

    public static ConstraintTypeAM toApi(ConstraintType constraintType) {
        return ConstraintTypeAM.builder()
                .id(constraintType.getId())
                .name(constraintType.getName())
                .uuid(constraintType.getUuid())
                .regex(constraintType.getRegex())
                .description(constraintType.getDescription())
                .entityId(constraintType.getEntityId())
                .uiType(constraintType.getUiType())
                .build();
    }

    private static SystemRoleAssignmentConstraintValueAM toApi(final SystemRoleAssignmentConstraintValue constraintValue) {
        return SystemRoleAssignmentConstraintValueAM.builder()
                .constraintIdentifier(constraintValue.getConstraintIdentifier())
                .constraintValueType(ConstraintValueTypeAM.valueOf(constraintValue.getConstraintValueType().name()))
                .constraintTypeId(constraintValue.getConstraintType().getId())
                .constraintTypeEntityId(constraintValue.getConstraintType().getEntityId())
                .constraintValue(constraintValue.getConstraintValue())
                .postponed(constraintValue.isPostponed())
                .build();
    }

    public static List<SystemRoleAssignmentConstraintValueAM> toApi(final List<SystemRoleAssignmentConstraintValue> constraintValues) {
        return constraintValues.stream().map(ConstraintMapper::toApi).toList();
    }

}
