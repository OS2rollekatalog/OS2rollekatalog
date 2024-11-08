package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.entrald.EntraldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
public class EntralMembershipSyncTask {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private EntraldService entraldService;

	// every five minutes at minute 02
	@Scheduled(cron = "0 0/5 * * * ?")
	public void sync() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		if (!configuration.getIntegrations().getEntrald().isMembershipSyncEnabled()) {
			log.debug("Entrald integration membershipSync is not enabled - will not sync");
			return;
		}

		entraldService.membershipSync();
	}
}
