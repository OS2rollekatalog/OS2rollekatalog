package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.SystemRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@Slf4j
public class MaximumSystemRoleAssignmentsTask {
	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Scheduled(cron = "0 #{new java.util.Random().nextInt(59)} 7 ? * *")
	public void notifyMaximumAssignments() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		systemRoleService.notifyMaximumAssignments();
	}
}
