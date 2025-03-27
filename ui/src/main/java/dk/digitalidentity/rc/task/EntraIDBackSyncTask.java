package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.entraid.EntraIDService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
public class EntraIDBackSyncTask {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private EntraIDService entraIDService;

	// every 30 minutes starting at minute 02
	@Scheduled(cron = "${rc.integrations.entraID.backSyncTask.cron}")
	public void sync() throws ReflectiveOperationException {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		if (!configuration.getIntegrations().getEntraID().isBackSyncEnabled()) {
			log.debug("EntraID integration backsync is not enabled - will not sync");
			return;
		}

		entraIDService.backSync();
	}
}
