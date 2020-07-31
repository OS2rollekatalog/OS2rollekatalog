package dk.digitalidentity.rc.log;

import java.util.Calendar;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.log4j.Log4j;

@Log4j
@Component
@EnableScheduling
@Transactional(rollbackFor = Exception.class)
public class AuditLogCleaner {

	@Value("${scheduled.enabled:false}")
	private boolean runScheduled;

	@Autowired
	private AuditLogEntryDao auditLogEntryDao;
	
	// run every night at 03:00 on Saturdays
    @Scheduled(cron = "0 0 3 * * SAT")
    public void cleanupAuditLogs() {
		if (!runScheduled) {
			log.info("Scheduled jobs are disabled on this instance");
			return;
		}
		log.info("Running scheduled job");

    	Calendar cal = Calendar.getInstance();
    	cal.add(Calendar.MONTH, -6);
    	Date before = cal.getTime();

    	auditLogEntryDao.deleteByTimestampBefore(before);
    }
}
