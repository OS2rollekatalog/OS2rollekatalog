package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.PendingADUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class AddDirtyADGroupsTask {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private PendingADUpdateService pendingADUpdateService;

//	@Scheduled(fixedRate = 60 * 1000 * 5)
	@Scheduled(cron = "${rc.cron.add_dirty_ad_groups}")
	public void addDirtyADGroups() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}

		pendingADUpdateService.addADGroupsFromMemberShipSyncFilter();
	}
}
