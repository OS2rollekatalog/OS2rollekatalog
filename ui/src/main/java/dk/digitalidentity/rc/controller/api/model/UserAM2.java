package dk.digitalidentity.rc.controller.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "User")
public class UserAM2 {
	private String uuid;
	@Schema(description = "ID of user")
	private String userId;
	@Schema(description = "external UUID")
	private String extUuid;
	@Schema(description = "Name of user")
	private String name;
	@Schema(deprecated = true, description = "Also external UUID kept here for backwards compatability, will be removed in future")
	private String extId;
}
