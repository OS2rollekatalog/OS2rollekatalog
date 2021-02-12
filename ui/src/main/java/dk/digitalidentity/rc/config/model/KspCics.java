package dk.digitalidentity.rc.config.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KspCics {
	private boolean enabled = false;
	private boolean enabledOutgoing = false;
	private String url = "https://intewswlbs-wm.kmd.dk/KMD.YH.KSPAabenSpml/KspAabenSpml.asmx";
	private String losid;
	private String keystoreLocation;
	private String keystorePassword;
}
