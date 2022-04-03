package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.log.SecurityLogger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class SecurityLogCleaner {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private SecurityLogger securityLogger;

	// run between 02:00 and 04:59 on Saturdays
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(59)} #{new java.util.Random().nextInt(3) + 2} * * SAT")
	public void cleanupAuditLogs() {
		if (!configuration.getScheduled().isEnabled()) {
			log.info("Scheduled jobs are disabled on this instance");
			return;
		}

		log.info("Running scheduled job");

		securityLogger.clean();
	}
}