package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.service.ItSystemMasterService;

@Component
@EnableScheduling
public class ItSystemMasterSyncTask {

	@Value("${scheduled.enabled:false}")
	private boolean runScheduled;

	@Autowired
	private ItSystemMasterService masterService;

	@Scheduled(fixedRate = 15 * 60 * 1000, initialDelay = 40 * 60 * 1000)
	public void fetchItSystems() {
		if (!runScheduled) {
			return;
		}
		
		masterService.fetchItSystems();
	}

	@Scheduled(fixedRate = 15 * 60 * 1000, initialDelay = 60 * 60 * 1000)
	public void updateLocalItSystems() {
		if (!runScheduled) {
			return;
		}

		masterService.updateLocalItSystems();
	}
}
