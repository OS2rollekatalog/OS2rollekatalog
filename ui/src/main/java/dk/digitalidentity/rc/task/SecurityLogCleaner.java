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

	// run every 5 minutes between 02:00 and 03:59 (so 24 times, deleting 25.000 rows each time, for a total of 600.000 rows in total)
	// as this is a heavy operation, we spread the load across the various running instances
	@Scheduled(cron = "#{new java.util.Random().nextInt(59)} #{new java.util.Random().nextInt(4)}/5 2-3 * * ?")
	public void cleanupAuditLogs() {
		if (!configuration.getScheduled().isEnabled()) {
			log.info("Scheduled jobs are disabled on this instance");
			return;
		}

		long start = System.currentTimeMillis();
		securityLogger.clean();
		
		log.info("Cleanup security log took " + (System.currentTimeMillis() - start) + "ms");
	}
}