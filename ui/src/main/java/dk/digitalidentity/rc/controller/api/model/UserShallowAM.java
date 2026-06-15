package dk.digitalidentity.rc.controller.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "UserShallow")
public class UserShallowAM {
	@Schema(description = "The users UUID", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
    private String uuid;
    private String userId;
    @Schema(description = "The user's external UUID (e.g. AD objectGUID) — globally unique and "
            + "domain-independent. Accepted as the user identifier by the role-assignment write API.")
    private String extUuid;
    private String name;
}
