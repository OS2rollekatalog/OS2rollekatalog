package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Organisation {
	
	@FeatureDocumentation(name = "Organisationsniveauer", description = "Indlæs organisationsniveauer fra ekstern kilde")
	private boolean getLevelsFromApi = false;
}
