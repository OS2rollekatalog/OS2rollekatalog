package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Email {

	@FeatureDocumentation(name = "Email", description = "Giver rollekataloget mulighed for at afsende emails")
	private boolean enabled = false;
	
	private String from = "no-reply@rollekatalog.dk";
	private String username;
	private String password;
	private String host;
}
