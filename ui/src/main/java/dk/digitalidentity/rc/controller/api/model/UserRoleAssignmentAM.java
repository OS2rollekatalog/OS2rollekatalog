package dk.digitalidentity.rc.controller.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "UserRoleAssignment")
public class UserRoleAssignmentAM {
    @Schema(description = "")
    @NotNull
    private Long userRoleId;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String assignedByUserId;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String assignedByName;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Date assignedTimestamp;

}
