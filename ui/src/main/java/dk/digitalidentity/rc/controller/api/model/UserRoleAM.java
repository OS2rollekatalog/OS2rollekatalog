package dk.digitalidentity.rc.controller.api.model;

import dk.digitalidentity.rc.rolerequest.model.enums.ApproverOption;
import dk.digitalidentity.rc.rolerequest.model.enums.RequesterOption;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
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
    @Schema(description = "", deprecated = true)
    private boolean canRequest;
    @Schema(description = "")
    private boolean sensitiveRole;
    @Positive
    @Schema(description = "")
    private long itSystemId;
    @Valid
    @Schema(description = "")
    private List<SystemRoleAssignmentAM> systemRoleAssignments;

    @Column
    private RequesterOption requesterPermission;
    @Column
    private ApproverOption approverPermission;

}
