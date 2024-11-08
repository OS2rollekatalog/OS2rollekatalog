package dk.digitalidentity.rc.controller.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;
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
@Schema(name = "SystemRoleAssignmentConstraintValue")
public class SystemRoleAssignmentConstraintValueAM {

    @Schema(description = "Id of the constraint type")
    @NotNull
    private Long constraintTypeId;

    @Schema(description = "Value type")
    private ConstraintValueTypeAM constraintValueType;

    @Schema(maxLength = 4096)
    private String constraintValue;

    @Schema(maxLength = 128)
    private String constraintIdentifier;

    @Column(nullable = false)
    private boolean postponed;

}
