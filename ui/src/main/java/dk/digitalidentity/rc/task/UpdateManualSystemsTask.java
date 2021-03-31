package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.ManualRolesService;
import lombok.extern.log4j.Log4j;

@Log4j
@Component
@EnableScheduling
public class UpdateManualSystemsTask {

	@Autowired
	private ManualRolesService manualRolesService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	// run every 15 minutes during the day
	@Scheduled(cron = "0 0/15 7-17 * * ?")
	public void processUsersFromWaitingTable() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		log.debug("Running scheduled job");

		manualRolesService.notifyServicedesk();
	}
}
