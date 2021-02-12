package dk.digitalidentity.rc.task;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.AttestationService;
import dk.digitalidentity.rc.service.EmailService;

@Component
@EnableScheduling
public class NotifyOnAttestations {
	
	@Autowired
	private EmailService emailService;

	@Autowired
	private AttestationService attestationService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	// Run daily at 08:00
	@Scheduled(cron = "0 0 8 * * ?")
	@Transactional(rollbackFor = Exception.class)
	public void notifyOnAttestations() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}

		List<User> managers = attestationService.getManagersToNotify();
		for (User manager : managers) {
			if (manager.getEmail() != null && manager.getEmail().length() > 0) {
				String subject = "Der ligger rettigheder der skal attesteres af dig";
				String message = "Nogle af dine medarbejderes rettigheder skal attesteres.<br/><br/>Log på rollekataloget for at gennemgå disse.";

				emailService.sendMessage(manager.getEmail(), subject, message);
			}
		}
	}
}
