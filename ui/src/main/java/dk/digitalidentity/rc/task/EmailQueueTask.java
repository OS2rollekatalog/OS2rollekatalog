package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class EmailQueueTask {

	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private EmailQueueService emailQueueService;

	@Autowired
	private SettingsService settingsService;

	@Scheduled(cron = "0 0/5 * * * ?")
	public void processEmails() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		if (settingsService.isBlockAllEmailTransmissions()) {
			log.error("BlockAllEmailtransmissions is enabled. Not sending emails.");
			return;
		}

		emailQueueService.sendPending();
	}
}
