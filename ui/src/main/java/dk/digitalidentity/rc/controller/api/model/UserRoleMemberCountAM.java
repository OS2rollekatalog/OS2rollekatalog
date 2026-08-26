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
@Schema(name = "UserRoleMemberCount")
public class UserRoleMemberCountAM {
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private long userRoleId;
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private long memberCount;
}
