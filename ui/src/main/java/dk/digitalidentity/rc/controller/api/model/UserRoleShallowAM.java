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
@Schema(
	name = "UserRoleShallow",
	description = "Forenklet repræsentation af en jobfunktionsrolle"
)
public class UserRoleShallowAM {
	@Schema(
		description = "Unik numerisk identifikator for jobfunktionsrollen",
		requiredMode = Schema.RequiredMode.REQUIRED,
		example = "12345"
	)
	private Long id;

	@Schema(
		description = "Jobfunktionsrollens unikke tekstidentifikator",
		example = "admin-role",
		accessMode = Schema.AccessMode.READ_ONLY
	)
	private String identifier;

	@Schema(
		description = "Jobfunktionsrollens visningsnavn",
		example = "Administrator",
		accessMode = Schema.AccessMode.READ_ONLY
	)
	private String name;
}
