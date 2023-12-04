package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Organisation {
	
	@FeatureDocumentation(name = "Organisationsniveauer", description = "Indlæs organisationsniveauer fra ekstern kilde")
	private boolean getLevelsFromApi = false;

	@FeatureDocumentation(name = "InkluderPositioner", description = "Ved indlæsning via API, inkluder positioner fra sekundære domæner")
	private boolean includePositionsFromSecondaryDomains = true;

}
