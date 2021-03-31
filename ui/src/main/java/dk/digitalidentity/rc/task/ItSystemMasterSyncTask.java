package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.ItSystemMasterService;

@Component
@EnableScheduling
public class ItSystemMasterSyncTask {

	@Autowired
	private ItSystemMasterService masterService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Scheduled(cron = "0 7/15 6-20 * * *")
	public void fetchItSystems() {
		if (!configuration.getScheduled().isEnabled() ||
			!configuration.getIntegrations().getMaster().isEnabled()) {
			return;
		}
		
		masterService.fetchItSystems();
	}

	@Scheduled(cron = "0 2/15 6-20 * * *")
	public void updateLocalItSystems() {
		if (!configuration.getScheduled().isEnabled() ||
			!configuration.getIntegrations().getMaster().isEnabled()) {
			return;
		}

		masterService.updateLocalItSystems();
	}
}
