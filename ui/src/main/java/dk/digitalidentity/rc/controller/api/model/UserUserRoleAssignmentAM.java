package dk.digitalidentity.rc.controller.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "UserUserRoleAssignment")
public class UserUserRoleAssignmentAM {
    public enum AssignedThrough { DIRECT, TITLE, POSITION, ORG_UNIT, ROLE_GROUP }
    @Schema(description = "List of postponed constraint values")
    private List<PostponedConstraintAM> postponedConstraints;
    @Schema(description = "The user role that is assigned")
    private UserRoleAM userRole;
    @Schema(description = "The organisation that is responsible for this assignment")
    private OrgUnitShallowAM responsibleOrgUnit;
    @Schema(description = "Assigned through title, only set if assigned though a title")
    private TitleAM assignedThroughTitle;
    @Schema(description = "Which type of assignment is this")
    private AssignedThrough assignedThrough;
}
