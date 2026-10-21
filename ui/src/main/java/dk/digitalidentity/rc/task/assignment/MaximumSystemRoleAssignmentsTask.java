package dk.digitalidentity.rc.task.assignment;

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
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.SystemRoleService.ExceededSystemRole;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@Slf4j
public class MaximumSystemRoleAssignmentsTask {

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private RoleCatalogueConfiguration configuration;
	
	@Autowired
	private EmailTemplateService emailTemplateService;

	@Scheduled(cron = "${cron.maximum.systemroleassignments:0 #{new java.util.Random().nextInt(59)} 7 ? * *}")
	@WarnIfSlowerThan(millis = 1 * 60 * 1000)
	public void notifyMaximumAssignments() {
		if (!configuration.getScheduled().isEnabled()) {
			log.debug("Scheduled jobs are disabled on this instance");
			return;
		}

		// no reason to run this if the template is disabled
        EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.SYSTEM_ROLE_EXCEEDED_MAX_ASSIGNMENTS);
        if (!template.isEnabled()) {
            return;
        }

        List<ExceededSystemRole> exceeded = systemRoleService.findExceededSystemRoles();
        if (!exceeded.isEmpty()) {
        	systemRoleService.queueNotifications(exceeded);
        }
	}
}
