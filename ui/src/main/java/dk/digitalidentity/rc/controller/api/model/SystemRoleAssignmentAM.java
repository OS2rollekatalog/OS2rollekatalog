package dk.digitalidentity.rc.controller.api.model;

import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
@Schema(name = "SystemRoleAssignment")
public class SystemRoleAssignmentAM {
    @Schema(description = "")
    @NotNull
    private long systemRoleId;

    @Schema(description = "")
    @Valid
    private List<SystemRoleAssignmentConstraintValueAM> constraintValues;
}
