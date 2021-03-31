package dk.digitalidentity.rc.task;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.HistoryService;
import lombok.extern.log4j.Log4j;

@Component
@EnableScheduling
@Log4j
public class HistoryRoleAssignmentsTask {

	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private HistoryService historyService;

	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 4 * * ?")
	// enable this to execute script at bootup
	// @Scheduled(fixedDelay = 60 * 60 * 1000)
	public void generateHistory() {
		if (!configuration.getScheduled().isEnabled() ||
			!configuration.getScheduled().getHistory().isEnabled()) {

			return;
		}
		
		Date now = new Date();
		
		historyService.generateOrganisationHistory();
		historyService.generateItSytemHistory();
		historyService.generateRoleAssignmentHistory();
		historyService.generateKleAssignmentHistory();
		historyService.generateOURoleAssignmentHistory();
		historyService.generateTitleRoleAssignmentHistory();
		
		log.info("Generating historic role assignments took " + ((new Date().getTime() - now.getTime()) / 1000) + " seconds");
	}
	
	@Scheduled(cron = "0 0 5 * * ?")
	public void deleteAncientHistory() {
		if (!configuration.getScheduled().isEnabled() ||
			!configuration.getScheduled().getHistory().isEnabled()) {

			return;
		}

		historyService.deleteOldHistory(configuration.getScheduled().getHistory().getRetention());
	}
}
