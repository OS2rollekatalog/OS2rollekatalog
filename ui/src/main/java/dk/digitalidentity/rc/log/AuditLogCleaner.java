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

	// run every night at 03:xx on Saturdays
    @Scheduled(cron = "0 #{new java.util.Random().nextInt(60)} 3 * * SAT")
    public void cleanupAuditLogs() {
		if (!configuration.getScheduled().isEnabled()) {
			log.info("Scheduled jobs are disabled on this instance");
			return;
		}
		log.info("Running scheduled job");

		auditLogService.cleanupAuditlogs();
    }
    
    // we run this 3 times per day, deleting 10.000 records every time (top-munis generate roughly 15.000 every day, so
    // this allows us to reduce the current large set of old logs over time). Note that we do not run it during normal workhours
    @Scheduled(cron = "#{new java.util.Random().nextInt(60)} #{new java.util.Random().nextInt(60)} 4,17,20 * * ?")
    public void cleanupExternalLoginAuditLogs() {
		if (!configuration.getScheduled().isEnabled()) {
			log.info("Scheduled jobs are disabled on this instance");
			return;
		}
		log.info("Running scheduled job");

		auditLogService.cleanupExternalLoginAuditlogs();
    }
}
