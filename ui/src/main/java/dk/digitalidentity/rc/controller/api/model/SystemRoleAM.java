package dk.digitalidentity.rc.controller.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
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
@Schema(name = "SystemRole")
public class SystemRoleAM {

    @Schema(description = "Unique ID for the system role")
    private Long id;

    @NotEmpty
    @Schema(description = "Name of the role")
    private String name;

    @NotEmpty
    @Schema(description = "Unique identifier of systemrole")
    private String identifier;

    @Schema(description = "System role description")
    private String description;

    @Schema
    private Integer weight;

    @Schema(description = "Supported constraint types")
    private List<ConstraintTypeSupportAM> supportedConstraintTypes;
}
