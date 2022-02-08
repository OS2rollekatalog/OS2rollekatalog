package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Master {
	
	@FeatureDocumentation(name = "ItSystem Master", description = "Abonnement på ItSystem Master databasen")
	
	private boolean enabled = true;
	private String url = "https://master.rollekatalog.dk";
}
