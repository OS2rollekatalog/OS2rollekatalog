package dk.digitalidentity.rc.controller.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "PostponedConstraint")
public class PostponedConstraintAM {
    @Schema(description = "Constraint value")
    private String value;
    @Schema(description = "Id of a ConstrainType")
    private Long constraintTypeId;
    @Schema(description = "Entity id of a ConstrainType", accessMode = Schema.AccessMode.READ_ONLY)
    private String constraintTypeEntityId;
    @Schema(description = "Id of a SystemRole")
    private Long systemRoleId;
}
