package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.EmailQueueService;
import lombok.extern.log4j.Log4j;

@Log4j
@Component
@EnableScheduling
public class EmailQueueTask {

	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private EmailQueueService emailQueueService;

	@Scheduled(cron = "0 0/5 * * * ?")
	public void processEmails() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		emailQueueService.sendPending();
	}
}
