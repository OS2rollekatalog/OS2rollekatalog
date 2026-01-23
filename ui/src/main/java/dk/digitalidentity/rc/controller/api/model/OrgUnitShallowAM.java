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
@Schema(
	name = "OrgUnitShallow",
	description = "Forenklet repræsentation af en organisationsenhed"
)
public class OrgUnitShallowAM {

	@Schema(
		description = "Unik identifikator for organisationsenheden",
		example = "123e4567-e89b-12d3-a456-426614174000",
		requiredMode = Schema.RequiredMode.REQUIRED
	)
	private String uuid;

	@Schema(
		description = "Navn på organisationsenheden",
		example = "IT-afdelingen",
		accessMode = Schema.AccessMode.READ_ONLY
	)
	private String name;
}
