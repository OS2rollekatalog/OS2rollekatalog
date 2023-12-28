package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.HandleInactiveRolesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@Slf4j
public class HandleInactiveRolesTask {

	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private HandleInactiveRolesService service;

	// average municipality has a run-time of roughly 5 seconds (with 0 changes)
	// Run daily at 01:00-01:30 (random, since a bit heavy on the database
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(30)} 1 * * ?")
	public void handleRoles() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		service.perform();
	}
}
