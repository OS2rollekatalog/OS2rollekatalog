package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.ItSystemService;

@Component
@EnableScheduling
public class ItSystemCleanupTask {

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	// Run daily at 01:00 - 02:00
	@Scheduled(cron = "${cron.itSystemClean:0 #{new java.util.Random().nextInt(59)} 1 * * ?}")
	public void deleteItSystems() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}

		itSystemService.permanentlyDelete();
	}
}
