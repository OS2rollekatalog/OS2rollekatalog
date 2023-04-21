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

@Component
@EnableScheduling
@Slf4j
public class SyncNemLoginRolesTask {

	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private NemLoginService nemLoginService;
	
	@Scheduled(cron = " 0 #{new java.util.Random().nextInt(55)} 3,10,14 ? * *")
//	@Scheduled(fixedRate = 60 * 1000 * 1000) // once per hour while testing
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
		
		nemLoginService.syncNemLoginRoles(false);
	}
}
