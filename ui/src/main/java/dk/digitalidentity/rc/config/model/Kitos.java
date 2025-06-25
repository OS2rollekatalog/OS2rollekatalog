package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Kitos {
	@FeatureDocumentation(name = "OS2kitos integration", description = "Integration til OS2kitos til at vedligeholde systemansvarlig og systemejer på IT-systemer")
	private boolean enabled = false;

	private String cvr;

	private String systemOwnerRoleUUID;
	private String systemResponsibleRoleUUID;
}
