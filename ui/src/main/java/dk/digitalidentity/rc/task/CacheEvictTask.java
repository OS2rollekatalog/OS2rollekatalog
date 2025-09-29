package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.service.SettingsService;

// just use this task to run cacheEvicts - pick the right method to put it into (5, 15, 30, etc minutes)

@Component
@EnableScheduling
public class CacheEvictTask {

	@Autowired
	private SettingsService settingsService;

	@Scheduled(fixedDelay = 5 * 60 * 1000)
	public void runEvery5Minutes() {
		;
	}

	@Scheduled(fixedDelay = 15 * 60 * 1000)
	public void runEvery15Minutes() {
		settingsService.evictCache();
	}
	
	@Scheduled(fixedDelay = 30 * 60 * 1000)
	public void runEvery30Minutes() {
		;
	}
}
