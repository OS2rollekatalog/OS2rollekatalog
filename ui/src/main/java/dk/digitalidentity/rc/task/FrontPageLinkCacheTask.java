package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.dao.FrontPageLinkDao;
import dk.digitalidentity.rc.service.FrontPageLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@EnableScheduling
public class FrontPageLinkCacheTask {

	@Autowired
	private FrontPageLinkDao frontPageLinkDao;

	@Autowired
	private FrontPageLinkService frontPageLinkService;

	private LocalDateTime lastChecked = LocalDateTime.now();

	@Scheduled(fixedRate = 10000)
	public void checkForChanges() {
		LocalDateTime currentCheck = LocalDateTime.now();
		if (frontPageLinkDao.existsByLastChangedAfter(lastChecked)) {
			frontPageLinkService.evictCache();
			lastChecked = currentCheck;
		}
	}
}
