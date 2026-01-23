package dk.digitalidentity.rc.controller.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
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
	name = "RoleGroupShallow",
	description = "Forenklet repræsentation af en rollegruppe"
)
public class RoleGroupShallowAM {

	@Schema(
		description = "Unik numerisk identifikator for rollegruppen. Skal være 0 ved oprettelse (create endpoint)",
		requiredMode = Schema.RequiredMode.REQUIRED,
		example = "0"
	)
	private Long id;

	@Schema(
		description = "Navn på rollegruppen. Skal være unikt ved oprettelse af en ny rollegruppe",
		accessMode = Schema.AccessMode.READ_ONLY,
		example = "IT Administrator Gruppe"
	)
	@NotEmpty
	private String name;
}
