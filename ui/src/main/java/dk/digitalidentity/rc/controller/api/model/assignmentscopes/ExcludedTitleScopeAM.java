package dk.digitalidentity.rc.controller.api.model.assignmentscopes;

import dk.digitalidentity.rc.controller.api.model.TitleShallowAM;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(
	name = "ExcludedTitleScope",
	description = "Regel som indeholder ekskluderede stillinger"
)
public class ExcludedTitleScopeAM extends AssignmentScopeAM {
	@Schema(description = "Liste af stillinger som er ekskluderet fra tildelingen")
	@NotNull
	private List<TitleShallowAM> excludedTitles;

	public ExcludedTitleScopeAM() {
		setType("EXCLUDED_TITLE");
	}
}
