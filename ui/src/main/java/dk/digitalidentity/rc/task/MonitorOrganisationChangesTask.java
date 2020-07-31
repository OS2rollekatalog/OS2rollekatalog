package dk.digitalidentity.rc.task;

import java.util.Calendar;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.log4j.Log4j;

@Log4j
@Component
@EnableScheduling
public class MonitorOrganisationChangesTask {

	@Value("${scheduled.enabled:false}")
	private boolean runScheduled;

	@Autowired
	private UserService userService;
	
	// sometimes the local integration at the customer stops running,
	// so we monitor for missing updates weekly
	@Scheduled(cron = "0 0 14 * * WED")
	public void verifyTaskIsRunning() {
		if (!runScheduled) {
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
