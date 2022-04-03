package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.ItSystemChangeUpdateService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class ItSystemChangeUpdateTask {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private ItSystemChangeUpdateService itSystemChangeUpdateService;
	
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 8,12,16 * * *")
	public void notifyAboutItSystemChanges() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		log.info("Running scheduled job");
		
		itSystemChangeUpdateService.notifyAboutItSystems();
	}
}
