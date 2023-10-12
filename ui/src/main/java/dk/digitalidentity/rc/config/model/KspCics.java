package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KspCics {
	
	@FeatureDocumentation(name = "KSP/CICS Integration", description = "Gør det muligt at administrere rettighedstildelinger for KSP/CICS")
	private boolean enabled = false;
	
	private boolean enabledOutgoing = false;
	private String url = "https://int-ewswlbs-wm3q2021.kmd.dk/KMD.YH.KSPAabenSpml/KSPAabenSPML.asmx";
	private String losid;
	private String keystoreLocation;
	private String keystorePassword;
	private String namePrefix = "";
}
