package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubstituteManagerAPI {

	@FeatureDocumentation(name = "Stedfortræder API", description = "Hvis slået til, vedligeholdes stedfortrædere via det organisatoriske API, og ikke via brugergrænsefladen.")
	private boolean enabled = false;
}