package dk.digitalidentity.rc.rolerequest.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
public class RequestCleanerTask {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private RequestService rolerequestService;

	// Run every sunday at 02:00-02:30
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(30)} 2 * * 0")
	public void removeRejectedRequests() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		rolerequestService.deleteRejectedAfter14Days();
	}
}
