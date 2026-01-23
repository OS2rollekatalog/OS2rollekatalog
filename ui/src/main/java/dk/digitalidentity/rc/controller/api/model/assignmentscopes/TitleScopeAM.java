package dk.digitalidentity.rc.controller.api.model.assignmentscopes;


import dk.digitalidentity.rc.controller.api.model.TitleShallowAM;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Schema(
	name = "TitleScope",
	description = "Regel som gør at brugere med udvalgte titler bliver omfattet af tildelingen"
)
public class TitleScopeAM extends AssignmentScopeAM {
	@Schema(description = "Liste af stillinger som tildelingen gælder for", requiredMode = Schema.RequiredMode.REQUIRED)
	@NotNull
	@Valid
	private Set<TitleShallowAM> titles;

	public TitleScopeAM() {
		setType("TITLE");
	}
}
