package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Kle {
	
	@FeatureDocumentation(name = "KLE UI", description = "Gør det muligt at redigere KLE tildelinger i rollekatalogets brugergrænseflade")
	private boolean uiEnabled = true;

	private boolean useOS2sync = false;
	private String os2SyncUrl;
}
