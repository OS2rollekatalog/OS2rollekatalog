package dk.digitalidentity.rc.config;

import dk.digitalidentity.rc.attestation.config.AttestationConfig;
import dk.digitalidentity.rc.config.model.ApiControl;
import dk.digitalidentity.rc.config.model.Audit;
import dk.digitalidentity.rc.config.model.Customer;
import dk.digitalidentity.rc.config.model.Integrations;
import dk.digitalidentity.rc.config.model.Organisation;
import dk.digitalidentity.rc.config.model.Scheduled;
import dk.digitalidentity.rc.config.model.SubstituteManagerAPI;
import dk.digitalidentity.rc.config.model.Titles;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "rc")
public class RoleCatalogueConfiguration {
	private String version = "2023 r2";
	private String latestVersion = "2023 r2";

	private AttestationConfig attestation = new AttestationConfig();
	private Customer customer = new Customer();
	private Titles titles = new Titles();
	private Audit audit = new Audit();
	private Organisation organisation = new Organisation();
	private Integrations integrations = new Integrations();
	private Scheduled scheduled = new Scheduled();
	private ApiControl apiControl = new ApiControl();
	private SubstituteManagerAPI substituteManagerAPI = new SubstituteManagerAPI();

	private boolean syncRoleAssignmentOrgUnitOnStartup = false;
	private boolean removeRolesAssignmentsWithoutOU = false;

	// enable for new un-released features
	private boolean experimental = false;

	public boolean checkVersion() {
		return Objects.equals(version, latestVersion);
	}
}
