package dk.digitalidentity.rc.controller.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
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
@Schema(name = "UserRole")
public class UserRoleAM {
    @Schema(description = "", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;
    @NotEmpty
    @Schema(description = "")
    private String name;
    @Schema(description = "")
    private String identifier;
    @Schema(description = "")
    private String description;
    @Schema(description = "")
    private String delegatedFromCvr;
    @Schema(description = "")
    private boolean userOnly;
    @Schema(description = "")
    private boolean canRequest;
    @Schema(description = "")
    private boolean sensitiveRole;
    @Positive
    @Schema(description = "")
    private long itSystemId;
    @Valid
    @Schema(description = "")
    private List<SystemRoleAssignmentAM> systemRoleAssignments;

}
