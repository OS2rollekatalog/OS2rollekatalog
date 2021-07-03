package dk.digitalidentity.rc.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.service.RequestApproveService;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.extern.log4j.Log4j;

@Log4j
@Component
@EnableScheduling
public class SendMailToRoleAssignerTask {

	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private RequestApproveService requestApproveService;
	
	@Autowired
	private SettingsService settingsService;

	@Scheduled(cron = "0 0/15 * * * ?")
	public void processEmails() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}
		
		if (!settingsService.isRequestApproveEnabled()) {
			return;
		}

		requestApproveService.sendMailToRoleAssigner();
	}
}
