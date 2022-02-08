package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
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
	
	@Async
	public void init() {
		if (configuration.getScheduled().isEnabled() && !kspCicsService.initialized()) {
			updateLocalData();
		}
	}

	// run hourly in daytime
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 5-17 * * ?")
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
}
