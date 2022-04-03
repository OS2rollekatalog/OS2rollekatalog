package dk.digitalidentity.rc.log;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class AuditLogCleaner {

	@Autowired
	private AuditLogService auditLogService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	// run every night at 03:00 on Saturdays
    @Scheduled(cron = "0 0 3 * * SAT")
    public void cleanupAuditLogs() {
		if (!configuration.getScheduled().isEnabled()) {
			log.info("Scheduled jobs are disabled on this instance");
			return;
		}
		log.info("Running scheduled job");

		auditLogService.cleanupAuditlogs();
    }
}
