package dk.digitalidentity.rc.log;

import java.util.Calendar;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.SecurityLogDao;
import dk.digitalidentity.rc.dao.model.SecurityLog;
import dk.digitalidentity.rc.security.SecurityUtil;
import lombok.extern.log4j.Log4j;

@Log4j
@Component
public class SecurityLogger {

	@Autowired
	private SecurityLogDao securityLogDao;

	public void log(String ipAddress, String method, String request) {
		SecurityLog entry = new SecurityLog();

		if (SecurityUtil.getClient() != null) {
			entry.setClientId(SecurityUtil.getClient().getId());
			entry.setClientname(SecurityUtil.getClient().getName());
		}
		else {
			log.error("Failed to identify client during security log!");
			entry.setClientId(0);
			entry.setClientname("UNKNOWN!");
		}

		entry.setTimestamp(new Date());
		entry.setIpAddress(ipAddress);
		entry.setMethod(method);
		entry.setRequest(request);

		securityLogDao.save(entry);
	}
	
	@Transactional(rollbackFor = Exception.class)
	public void clean() {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.MONTH, -3);
		Date before = cal.getTime();

		securityLogDao.deleteByTimestampBefore(before);
	}
}