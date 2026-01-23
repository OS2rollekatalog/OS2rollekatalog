package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@Slf4j
public class CleanUpDisabledUserAssignmentsTask {
	@Autowired
	private RoleCatalogueConfiguration configuration;
	@Autowired
	private UserService userService;

	// Run daily at 02:xx
	@Scheduled(cron = "0 0 2 * * ?")
	public void removeAssignmentsForDisabledUsers() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		userService.cleanupDisabledUserAssignments();
	}
}
