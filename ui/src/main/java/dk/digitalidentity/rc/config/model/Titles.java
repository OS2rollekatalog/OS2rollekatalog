package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Titles {
	
	@FeatureDocumentation(name = "Titelkatalog", description = "Gør det muligt at tildele rettigheder på stillingskryds")
	private boolean enabled;
}
