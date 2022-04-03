package dk.digitalidentity.rc.task;

import java.util.Calendar;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@EnableScheduling
public class MonitorOrganisationChangesTask {

	@Autowired
	private UserService userService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	// sometimes the local integration at the customer stops running,
	// so we monitor for missing updates weekly
	@Scheduled(cron = "0 0 14 * * WED")
	public void verifyTaskIsRunning() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}

    	Calendar cal = Calendar.getInstance();
    	cal.add(Calendar.DATE, -7);
    	Date sevenDaysAgo = cal.getTime();

    	User user = userService.getLatestUpdatedUser();
    	if (user != null && user.getLastUpdated() != null && user.getLastUpdated().after(sevenDaysAgo)) {
			return;
    	}
	
		log.error("It has been more than 7 days since fresh organisationdata has been loaded!");
	}
}
