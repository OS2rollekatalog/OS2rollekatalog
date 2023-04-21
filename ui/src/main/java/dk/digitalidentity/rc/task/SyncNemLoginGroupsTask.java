package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.nemlogin.NemLoginService;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class SyncNemLoginGroupsTask {
	
	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private NemLoginService nemLoginService;
	
	@Scheduled(cron = "30 0/2 * * * ?")
	public void syncNemLoginGroups() throws Exception {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		nemLoginService.synchronizeUserRoles();
	}
}
