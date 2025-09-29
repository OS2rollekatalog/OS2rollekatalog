package dk.digitalidentity.rc.config.model;

import dk.digitalidentity.rc.config.FeatureDocumentation;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NemLogin {
	
	@FeatureDocumentation(name = "NemLog-in", description = "Integration til NemLog-ins IdM API til synkronisering af brugergrupper")
	private boolean enabled = false;

	// remove once we know this works as intended :)
	private boolean userDryRunOnly = true;
	
	private String keystoreLocation;
	private String keystorePassword;
	private String baseUrl = "https://services.nemlog-in.dk";

	// adminRoles
	private String organizationAdministratorName = "Organisationsadministrator";
	private String organizationAdministratorIdentifier = "OrganizationAdministrator";
	private String organizationAdministratorDescription = "Administrator der kan sætte grundlæggende indstillinger op for organisationen";
	private String identityAdministratorName = "Brugeradministrator";
	private String identityAdministratorIdentifier = "IdentityAdministrator";
	private String identityAdministratorDescription = "Administrator der kan oprette og administrere brugere";
	private String rightsAdministratorName = "Rettighedsadministrator";
	private String rightsAdministratorIdentifier = "RightsAdministrator";
	private String rightsAdministratorDescription = "Administrator der kan tildele rettigheder til brugere";
}
