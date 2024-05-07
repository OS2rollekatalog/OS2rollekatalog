package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.UserRoleService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@Slf4j
public class LinkedSystemRoleSyncTask {

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Scheduled(fixedRate = 60 * 60 * 1000)
	@Transactional
	public void updateLinkedUserRole() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		userRoleService.updateLinkedUserRoles();
	}
}
