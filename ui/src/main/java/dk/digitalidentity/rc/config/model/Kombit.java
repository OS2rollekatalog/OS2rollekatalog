package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Kombit {

	@FeatureDocumentation(name = "KOMBIT Integration", description = "Integration til KOMBITs Administrationsmodul (produktion)")
	private boolean enabled = false;
	
	private String url = "https://admin.serviceplatformen.dk/stsadmin/xapi";
	private String domain;
	private String keystoreLocation;
	private String keystorePassword;
}
