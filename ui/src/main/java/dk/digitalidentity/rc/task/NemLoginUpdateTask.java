package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.nemlogin.NemLoginService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class NemLoginUpdateTask {

	@Autowired
	private NemLoginService nemLoginService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	// run ~4-5 times during "daytime" (every 5 hours from 6 AM to 11 PM)
	@Scheduled(cron = "#{new java.util.Random().nextInt(60)} #{new java.util.Random().nextInt(5)} 23 * * ?")
	public void syncAdminRoles() {
		if (!configuration.getIntegrations().getNemLogin().isEnabled() || !configuration.getScheduled().isEnabled()) {
			return;
		}

		long start = System.currentTimeMillis();

		nemLoginService.syncAdminRoleAssignments();

		if (System.currentTimeMillis() - start > (60 * 1000)) {
			log.warn("Running syncAdminRoles took " + (System.currentTimeMillis() - start) + "ms");
		}
	}

	// run once every "night" - they don't like being called before 10:00 - too many errors
	@Scheduled(cron = "${cron.nemlogin.fullSync:#{new java.util.Random().nextInt(60)} #{new java.util.Random().nextInt(60)} 10 * * ?}")
	public void fullSyncRoleAssignments() {
		if (!configuration.getIntegrations().getNemLogin().isEnabled() || !configuration.getScheduled().isEnabled()) {
			return;
		}

		long start = System.currentTimeMillis();

		nemLoginService.fullRoleSync();

		if (System.currentTimeMillis() - start > (10 * 60 * 1000)) {
			log.warn("Running fullSyncRoleAssignments took " + (System.currentTimeMillis() - start) + "ms");
		}
	}

	// period sync of systemRoles and AdminRoles from MitID Erhverv to OS2rollekatalog
	@Scheduled(cron = "#{new java.util.Random().nextInt(60)} #{new java.util.Random().nextInt(60)} 3,10,14 * * ?")
	public void syncNemLoginRoles() throws Exception {
		if (!configuration.getScheduled().isEnabled() || !configuration.getIntegrations().getNemLogin().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		long start = System.currentTimeMillis();

		nemLoginService.syncNemLoginRoles(true);
		nemLoginService.syncNemLoginAdminRoles();

		if (System.currentTimeMillis() - start > (3 * 60 * 1000)) {
			log.warn("Running syncNemLoginRoles took " + (System.currentTimeMillis() - start) + "ms");
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	public void runOnStartup() {
		if (!configuration.getScheduled().isEnabled() || !configuration.getIntegrations().getNemLogin().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		// false ensures it only runs once
		nemLoginService.syncNemLoginRoles(false);
		nemLoginService.syncNemLoginAdminRoles();

		// this will only really work once, as the moment any assignments are present in DB, nothing will be read from MitID Erhverv
		nemLoginService.syncExistingRoleAssignments();
		nemLoginService.syncAdminRoleAssignments();
	}
}
