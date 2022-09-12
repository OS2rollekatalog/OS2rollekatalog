package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class CleanupInactiveSubstituteMangersTask {

	@Autowired
	private UserService userService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	// Run sometime Saturday between 18:00 and 18:55
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 18 * * SAT")
	public void removeInactiveSubstituteMangers() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		userService.removeInactiveSubstituteManagers();
	}
}
