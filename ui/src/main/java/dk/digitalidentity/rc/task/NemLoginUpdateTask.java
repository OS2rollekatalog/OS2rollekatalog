package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.nemlogin.NemLoginService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class NemLoginUpdateTask {
	
	@Autowired
	private NemLoginService nemLoginService;
	
	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	// run once every 5 minutes during "daytime"
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(4)}/5 6-23 * * ?")
	public void syncDirtyUserRolesAssignments() {
		if (!configuration.getIntegrations().getNemLogin().isEnabled() || !configuration.getScheduled().isEnabled()) {
			return;
		}

		nemLoginService.updateUserRoleAssignments();
	}

	// run once every night
	@Scheduled(cron = " 0 #{new java.util.Random().nextInt(55)} 5 ? * *")
	public void fullSyncRoleAssignments() {
		if (!configuration.getIntegrations().getNemLogin().isEnabled() || !configuration.getScheduled().isEnabled()) {
			return;
		}

		nemLoginService.fullRoleSync();
	}
	
	// period sync of systemRoles from MitID Erhverv to OS2rollekatalog
	@Scheduled(cron = " 0 #{new java.util.Random().nextInt(55)} 3,10,14 ? * *")
	public void syncNemLoginRoles() throws Exception {
		if (!configuration.getScheduled().isEnabled() || !configuration.getIntegrations().getNemLogin().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		nemLoginService.syncNemLoginRoles(true);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() {
		if (!configuration.getScheduled().isEnabled() || !configuration.getIntegrations().getNemLogin().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;			
		}
		
		// false ensures it only runs once
		nemLoginService.syncNemLoginRoles(false);
		
		// this will only really work once, as the moment any assignments are present in DB, nothing will be read from MitID Erhverv
		nemLoginService.syncExistingRoleAssignments();
	}
}
