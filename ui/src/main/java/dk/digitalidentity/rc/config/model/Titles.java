package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Titles {
	
	@FeatureDocumentation(name = "Titelkatalog", description = "Gør det muligt at tildele rettigheder på stillingskryds")
	private boolean enabled;
	
	private boolean positionsEnabled = false;
	
	// if titles are disabled, and positions are enabled, positions are enabled (so, extra sanity check ;))
	public boolean isPositionsEnabled() {
		if (!enabled && positionsEnabled) {
			return true;
		}
		
		return false;
	}
}
