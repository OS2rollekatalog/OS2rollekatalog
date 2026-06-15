package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.ManualRolesService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class UpdateManualSystemsTask {

	@Autowired
	private ManualRolesService manualRolesService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	// Full sweep over all users and manual it-systems. Runs rarely (nightly) as a safety-net that
	// bootstraps the baseline, cleans up rows for deleted users and recovers from any lost events.
	// The cron is randomized (SpEL) per instance so customers don't all run at the same time.
	@Scheduled(cron = "${rc.cron.manual_it_system_task}")
//	@Scheduled(fixedDelay = 20 * 1000)
	public void processUsersFromWaitingTable() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		log.info("Notifying servicedesk about manual it-system changes started");

		manualRolesService.notifyServicedesk();

		log.info("Notifying servicedesk about manual it-system changes completed");
	}

	// Frequent but cheap flush of users marked pending by ManualRolesUserAssignmentsChangedListener.
	// Processes only changed users, batched together into per-it-system digest mails. No-op when nothing changed.
	// The cron is randomized (SpEL) per instance so customers don't all run at the same time.
	@Scheduled(cron = "${rc.cron.manual_it_system_pending_task}")
	public void processPendingUsers() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		manualRolesService.processPendingUsers();
	}
}
