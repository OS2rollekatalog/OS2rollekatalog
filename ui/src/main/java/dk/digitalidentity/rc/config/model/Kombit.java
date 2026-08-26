package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Kombit {

	@FeatureDocumentation(name = "KOMBIT PROD Integration", description = "Integration til KOMBITs Administrationsmodul (produktion)")
	private boolean enabled = false;
	
	private String url = "https://admin.serviceplatformen.dk/stsadmin/xapi";
	private String domain;
	private String keystoreLocation;
	private String keystorePassword;
	
	@FeatureDocumentation(name = "KOMBIT TEST Integration", description = "Integration til KOMBITs Administrationsmodul (test)")
	private boolean testEnabled = false;
	
	private String testUrl = "https://admin-test.serviceplatformen.dk/stsadmin/xapi";
	private String testKeystoreLocation;
	private String testKeystorePassword;
}
