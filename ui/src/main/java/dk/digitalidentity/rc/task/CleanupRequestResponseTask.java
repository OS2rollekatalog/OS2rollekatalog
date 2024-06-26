package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.RequestApproveService;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class CleanupRequestResponseTask {

	@Autowired
	private RequestApproveService requestApproveService;

	@Autowired
	private SettingsService settingsService;

	@Autowired
	private RoleCatalogueConfiguration configuration;
		
	// Run daily at 04:xx
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 4 * * ?")
	public void sendNotifications() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		if (!settingsService.isRequestApproveEnabled()) {
			return;
		}

		requestApproveService.deleteOld();
	}
}
