package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.dmp.DMPService;
import dk.digitalidentity.rc.service.dmp.DMPStub;

@Component
@EnableScheduling
public class DMPTasks {
	
	@Autowired
	private RoleCatalogueConfiguration config;

	@Autowired
	private DMPStub stub;
	
	@Autowired
	private DMPService dmpService;

	// tokens for calling DMP is wiped from cache every 30 minutes
	@Scheduled(fixedRate = 30 * 60 * 1000)
	public void cleanUpTask() {
		if (config.getScheduled().isEnabled() && config.getIntegrations().getDmp().isEnabled()) {
			stub.cleanUpToken();
		}
	}
	
	// every 4 hours we pull fresh roles from DMP
	@Scheduled(fixedRate = 4 * 60 * 60 * 1000)
	public void synchronizeDmpItSystem() {
		if (config.getScheduled().isEnabled() && config.getIntegrations().getDmp().isEnabled()) {
			dmpService.synchronizeDMPRoles();
		}
	}
	
	// every 5 minutes we perform a deltasync of pending role-changes in DMP
	@Scheduled(cron = "#{new java.util.Random().nextInt(55)} #{new java.util.Random().nextInt(4)}/5 4-20 * * ?")
	public void deltaSyncRoles() {
		if (config.getScheduled().isEnabled() && config.getIntegrations().getDmp().isEnabled()) {
			dmpService.deltaSyncRoles();
		}
	}
	
	// every night we delete users from DMP without roles
	@Scheduled(cron = "${cron.dmp.cleanup:0 #{new java.util.Random().nextInt(55)} 21 * * ?}")
	public void deleteUsers() {
		if (config.getScheduled().isEnabled() && config.getIntegrations().getDmp().isEnabled()) {
			dmpService.deleteUsers();
		}
	}
	
	// every night we perform a full sync of assigned userroles
	@Scheduled(cron = "0 #{new java.util.Random().nextInt(55)} 22 * * ?")
	public void fullSyncRoles() {
		if (config.getScheduled().isEnabled() && config.getIntegrations().getDmp().isEnabled()) {
			dmpService.fullSyncRoles();
		}
	}
}
