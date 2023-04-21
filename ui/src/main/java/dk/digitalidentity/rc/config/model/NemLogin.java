package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NemLogin {
	
	@FeatureDocumentation(name = "NemLog-in", description = "Integration til NemLog-ins IdM API til synkronisering af brugergrupper")
	private boolean enabled = false;

	private String keystoreLocation;
	private String keystorePassword;
	private String baseUrl = "https://services.nemlog-in.dk";
}
