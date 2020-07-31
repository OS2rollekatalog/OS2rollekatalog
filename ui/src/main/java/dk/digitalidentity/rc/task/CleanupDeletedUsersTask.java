package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.service.UserService;
import lombok.extern.log4j.Log4j;

@Component
@EnableScheduling
@Log4j
public class CleanupDeletedUsersTask {

	@Autowired
	private UserService userService;

	@Value("${scheduled.enabled:false}")
	private boolean runScheduled;

	// Run sometime Saturday between 19:00 and 19:55
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 19 * * SAT")
	public void removeDeletedUsers() {
		if (!runScheduled) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		userService.removeOldInactiveUsers();
	}
}
