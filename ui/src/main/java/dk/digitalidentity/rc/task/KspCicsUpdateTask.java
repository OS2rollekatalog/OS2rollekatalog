package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.service.cics.KspCicsService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class KspCicsUpdateTask {
	
	@Autowired
	private KspCicsService kspCicsService;
	
	@Value("${kmd.kspcics.enabled:false}")
	private boolean kspCicsEnabled;
	
	@Value("${scheduled.enabled:false}")
	private boolean runScheduled;
	
	@Value("${kmd.kspcics.enabledOutgoing:false}")
	private boolean kspCicsEnabledOutgoing;

	@Async
	public void init() {
		if (runScheduled && !kspCicsService.initialized()) {
			updateLocalData();
		}
	}

	// Run once between 3 and 4 every night (no reason to spam KMD at the same time for each customer, so spread it out)
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 3 * * ?")
	public void updateLocalData() {
		if (!kspCicsEnabled || !runScheduled) {
			return;
		}

		log.info("Updating KSP/CICS user accounts and userProfiles");

		kspCicsService.updateUsers();
		kspCicsService.updateUserProfiles();
	}

	// run once every 5 minutes, starting 15 minutes after boot
	@Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 15 * 60 * 1000)
	public void syncUserProfileAssignments() {
		if (!kspCicsEnabled || !kspCicsEnabledOutgoing || !runScheduled) {
			return;
		}

		kspCicsService.updateUserProfileAssignments();
	}
}
