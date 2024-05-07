package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.HistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Date;

@Component
@EnableScheduling
@Slf4j
public class HistoryRoleAssignmentsTask {

	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private HistoryService historyService;

	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 4 * * ?")
	// enable this to execute script at bootup
//	@Scheduled(fixedDelay = 24 * 60 * 60 * 1000)
	public void generateHistory() {
		if (!configuration.getScheduled().isEnabled() ||
			!configuration.getScheduled().getHistory().isEnabled()) {

			return;
		}
		final LocalDate today = LocalDate.now();
		if (!historyService.lastGeneratedDate().isBefore(today)) {
			log.error("Last history generated happened on or after today ?!");
			return;
		}

		final Date startTime = new Date();
		historyService.generateOrganisationHistory();
		historyService.generateItSytemHistory();
		historyService.generateRoleAssignmentHistory();
		historyService.generateKleAssignmentHistory();
		historyService.generateOURoleAssignmentHistory();
		historyService.generateTitleRoleAssignmentHistory();
		historyService.generateExceptedUsersHistory();

		// Keep this last, so we can se what dates the generation where successful
		historyService.generateDate();
		
		log.info("Generating historic role assignments took " + ((new Date().getTime() - startTime.getTime()) / 1000) + " seconds");
	}
	
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 5 * * ?")
	public void deleteAncientHistory() {
		if (!configuration.getScheduled().isEnabled() ||
			!configuration.getScheduled().getHistory().isEnabled()) {

			return;
		}

		historyService.deleteOldHistory(configuration.getScheduled().getHistory().getRetention());
	}
}
