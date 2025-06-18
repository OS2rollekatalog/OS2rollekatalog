package dk.digitalidentity.rc.controller.api.model;

import dk.digitalidentity.rc.rolerequest.model.enums.ApproverOption;
import dk.digitalidentity.rc.rolerequest.model.enums.RequesterOption;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
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
@Schema(name = "RoleGroup")
public class RoleGroupAM {
    @Schema(description = "0 when using create endpoint", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;
    @Schema(description = "Name of the rolegroup, should be unique when creating a new rolegroup")
    @NotEmpty
    private String name;
    @Schema(description = "description of the role group")
    private String description;
    @Schema(description = "")
    private Boolean userOnly;
    @Schema(description = "", deprecated = true )
    private Boolean canRequest;
    @Schema(description = "Ids of the JFR associated with the rolegroup")
    private List<UserRoleGroupAssignmentAM> userRoles;

    @Schema(description = "")
    private RequesterOption requesterPermission;
    @Schema(description = "")
    private ApproverOption approverPermission;
}
