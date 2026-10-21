package dk.digitalidentity.rc.task;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.log.WarnIfSlowerThan;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.RoleNotificationService;
import dk.digitalidentity.rc.service.RoleNotificationService.ExpiringRoleReport;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class RoleExpiringTask {
	
	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private RoleNotificationService roleNotificationService;
	
	@Autowired
	private EmailTemplateService emailTemplateService;

	// should run every Monday morning
	@Scheduled(cron = " 0 #{new java.util.Random().nextInt(55)} 7 ? * MON")
	@WarnIfSlowerThan(millis = 1 * 60 * 1000)
	public void notifyAboutExpires() throws Exception {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}
		
        EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.ROLE_EXPIRING);
        if (!template.isEnabled()) {
            return;
        }

        List<ExpiringRoleReport> reports = roleNotificationService.collectReports();
        if (reports.isEmpty()) {
            return;
        }

        roleNotificationService.queueEmails(roleNotificationService.render(reports));
	}
}
