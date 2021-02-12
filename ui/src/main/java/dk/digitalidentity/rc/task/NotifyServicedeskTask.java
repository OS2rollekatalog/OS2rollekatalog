package dk.digitalidentity.rc.task;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.RequestApprove;
import dk.digitalidentity.rc.service.EmailService;
import dk.digitalidentity.rc.service.RequestApproveService;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.extern.log4j.Log4j;

@Component
@EnableScheduling
@Log4j
public class NotifyServicedeskTask {

	@Autowired
	private RequestApproveService requestApproveService;

	@Autowired
	private EmailService emailService;
	
	@Autowired
	private SettingsService settingsService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	// run every 10 minutes
	@Scheduled(fixedDelay = 10 * 60 * 1000)
	public void sendNotifications() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		if (!settingsService.isRequestApproveEnabled()) {
			return;
		}
		
		String helpdeskEmail = settingsService.getRequestApproveServicedeskEmail();
		if (helpdeskEmail == null || helpdeskEmail.length() == 0) {
			return;
		}
		
		List<RequestApprove> requests = requestApproveService.getPendingNotifications();
		if (requests.size() > 0) {
			String subject = "Der ligger " + requests.size() + " ny" + ((requests.size() == 1) ? "" : "e") + " anmodning" + ((requests.size() == 1) ? "" : "er");
			String message = "Der ligger " + requests.size() + " ny" + ((requests.size() == 1) ? "" : "e") + " anmodning" + ((requests.size() == 1) ? "" : "er") + " om rettighedstildelinger, og afventer behandling.<br/><br/>Log på rollekataloget for at behandle anmodning" + ((requests.size() == 1) ? "en." : "erne.");

			emailService.sendMessage(helpdeskEmail, subject, message);
			
			for (RequestApprove request : requests) {
				request.setRoleAssignerNotified(true);
				requestApproveService.save(request);
			}
		}
	}
}
