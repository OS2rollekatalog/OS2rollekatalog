package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.AttestationService;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class AttestationNotificationTask {
	
	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private AttestationService attestationService;
	
	@Autowired
	private SettingsService settingService;

	// BSG: I moved this from 7:xx to 4:xx because we hav a bug below. We send out the mails before we
	//      update the timestamps (setNextAttestationDeadlines)... this is a quickfix, as Attetation
	//      is to be re-implemented, and this will likely go away
	@Scheduled(cron = " 0 #{new java.util.Random().nextInt(55)} 4 ? * *")
	// @Scheduled(fixedDelay = 60 * 1000 * 1000) // once per hour while testing
	public void notifyOnAttestation() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}
		
		if (settingService.isScheduledAttestationEnabled()) {
			log.info("Running attestation notification task");

			// the order is kinda important, as the first notify needs to be last in the order, or it will trigger the reminder
			attestationService.setNextAttestationDeadlines();
			attestationService.notifyThirdParty();
			attestationService.notifyReminder();
			attestationService.firstNotify();
		}
	}
}
