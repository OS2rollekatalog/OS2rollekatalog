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
    private String name;
}
