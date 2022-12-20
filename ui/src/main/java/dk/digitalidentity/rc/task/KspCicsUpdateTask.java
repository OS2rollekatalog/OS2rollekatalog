package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.cics.KspCicsService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class KspCicsUpdateTask {
	
	@Autowired
	private KspCicsService kspCicsService;
	
	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private SettingsService settingsService;
	
	@Async
	public void init() {
		if (configuration.getScheduled().isEnabled() && !kspCicsService.initialized()) {
			updateLocalData();
		}
	}

	// run hourly in daytime
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 6-18 * * ?")
	public void updateLocalData() {
		if (!configuration.getIntegrations().getKspcics().isEnabled() ||
			!configuration.getScheduled().isEnabled()) {

			return;
		}

		log.info("Updating KSP/CICS user accounts and userProfiles");

		kspCicsService.updateUsers();
		kspCicsService.updateUserProfiles();
	}

	// run once every 5 minutes
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(4)}/5 * * * ?")
	public void syncUserProfileAssignments() {
		if (!configuration.getIntegrations().getKspcics().isEnabled() ||
			!configuration.getIntegrations().getKspcics().isEnabledOutgoing() || 
			!configuration.getScheduled().isEnabled()) {

			return;
		}

		kspCicsService.updateUserProfileAssignments();
	}
	
	// run every minute
	@Scheduled(fixedDelay = 60 * 1000)
	public void checkForManualUpdate() {
		if (!configuration.getIntegrations().getKspcics().isEnabled() ||
			!configuration.getScheduled().isEnabled()) {

			return;
		}
		
		if (settingsService.isRunCics()) {
			settingsService.setRunCics(false);
			updateLocalData();
		}
	}
}
