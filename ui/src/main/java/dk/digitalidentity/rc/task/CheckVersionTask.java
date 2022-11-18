package dk.digitalidentity.rc.task;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.AppManagerService;
import dk.digitalidentity.rc.service.model.ApplicationApiDTO;

@Component
@EnableScheduling
public class CheckVersionTask {

	@Autowired
	private AppManagerService appManagerService;
	
	@Autowired
	private RoleCatalogueConfiguration configuration;

	// check for a new version once per hour
	@Scheduled(initialDelay = 1000, fixedDelay = 60 * 60 * 1000)
	public void checkVersion() {
		if (!StringUtils.hasLength(configuration.getIntegrations().getAppManager().getUrl())) {
			return;
		}
		
		List<ApplicationApiDTO> applications = appManagerService.getApplications();
		if (applications == null) {
			return;
		}

		for (ApplicationApiDTO app : applications) {
			if (Objects.equals(app.getIdentifier(), "rollekatalog")) {
				configuration.setLatestVersion(app.getNewestVersion());
			}
		}
	}
}
