package dk.digitalidentity.rc.config.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Kle {
	private boolean uiEnabled = true;
	private boolean useOS2sync = false;
	private String os2SyncUrl;
}
