package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DMP {
	
	@FeatureDocumentation(name = "Danmarks Miljøportal", description = "Integration til rolle/rettighedsstyring i Danmarks Miljøportal")
	private boolean enabled = false;
	
	private String clientId;
	private String clientSecret;
	
	// users cannot be created without an email, and it must be unique, so a temlate is required
	private String dummyEmail = "noemail-{userId}@kommune.dk";

	// TODO: We do not know for sure that these URIs will work....
	private String tokenUrl = "https://log-in.miljoeportal.dk/runtime/oauth2/token.idp";
	private String serviceUrl = "https://brugerstyring.miljoeportal.dk/external/api";
}
