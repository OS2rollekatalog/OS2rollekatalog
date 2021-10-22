package dk.digitalidentity.rc.task;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.UserRoleService;
import lombok.extern.log4j.Log4j;

@Component
@EnableScheduling
@Log4j
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
