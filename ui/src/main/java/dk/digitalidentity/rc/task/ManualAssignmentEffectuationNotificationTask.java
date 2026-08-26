package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.ManualAssignmentEffectuationService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class ManualAssignmentEffectuationNotificationTask {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private ManualAssignmentEffectuationService manualAssignmentEffectuationService;

	@Scheduled(cron = "0 0/15 * * * ?")
	public void notifyPendingEffectuations() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		manualAssignmentEffectuationService.notifyPendingEffectuationsDigest();
	}
}
