package dk.digitalidentity.rc.task;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@EnableScheduling
public class SyncRoleAssignmentOrgUnitTask {

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private SettingsService settingsService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Scheduled(cron = " 0 #{new java.util.Random().nextInt(59)} 3 ? * *")
//	@Scheduled(fixedDelay = 20 * 1000)
	public void syncOrgUnitOnRoleAssignments() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}
		performSyncOnce();
		if (configuration.isRemoveRolesAssignmentsWithoutOU()) {
			cleanupRoleAssignmentsWithoutOU();
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() {
		if (!configuration.isSyncRoleAssignmentOrgUnitOnStartup()) {
			return;
		}
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}
		performSyncOnce();
	}

	private void cleanupRoleAssignmentsWithoutOU() {
		log.info("Removing assignments without OU started");
		orgUnitService.removeRoleAssignmentsWithoutOU();
		log.info("Removing assignments without OU completed");
	}

	private void performSyncOnce() {
		log.info("Syncing OrgUnit on role assignments started");
		if (!settingsService.isSyncOrgUnitOnRoleAssignmentsPerformed()) {
			orgUnitService.syncOrgUnitOnRoleAssignments();
			settingsService.setSyncOrgUnitOnRoleAssignmentsPerformed();
		}
		log.info("Syncing OrgUnit on role assignments completed");
	}
}
