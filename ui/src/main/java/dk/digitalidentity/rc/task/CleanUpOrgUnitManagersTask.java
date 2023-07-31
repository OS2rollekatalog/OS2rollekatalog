package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.OrgUnitService;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class CleanUpOrgUnitManagersTask {
	
	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	// Run daily at 03:xx
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 3 * * ?")
	public void removeDeletedManagers() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}
		
		orgUnitService.cleanupOrgUnitManagers();
	}
}
