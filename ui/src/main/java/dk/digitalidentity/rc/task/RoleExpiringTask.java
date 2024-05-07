package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.RoleNotificationService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class RoleExpiringTask {
	
	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private RoleNotificationService roleNotificationService;
	
	// should run every Monday morning
	@Scheduled(cron = " 0 #{new java.util.Random().nextInt(55)} 7 ? * MON")
	// @Scheduled(fixedDelay = 60 * 1000 * 1000) // once per hour while testing
	public void notifyAboutExpires() throws Exception {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		roleNotificationService.notifyAboutExpiringRoles();
	}
}
