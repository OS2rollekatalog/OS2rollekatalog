package dk.digitalidentity.rc.log;

import java.util.Calendar;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.dao.SecurityLogDao;
import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.SecurityLog;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ClientService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SecurityLogger {

	@Autowired
	private SecurityLogDao securityLogDao;
	
	@Autowired
	private ClientService clientService;

	public void log(HttpServletRequest httpServletRequest, String ipAddress, String method, String request, String clientVersion, String tlsVersion, int responseCode) {
		SecurityLog entry = new SecurityLog();

		Client client = SecurityUtil.getClient();
		if (client == null) {
			String apiKey = httpServletRequest.getHeader("ApiKey");

			// attempt to lookup client from database - this could happen if the firewall rejected - that happens before we hit
			// the security filter, which populates SecurityUtil data, so here we need to lookup the client from the DB instead
			if (StringUtils.hasLength(apiKey)) {
				client = clientService.getClientByApiKey(apiKey);
			}			
		}

		if (client != null) {
			entry.setClientId(client.getId());
			entry.setClientname(client.getName());
		}
		else {
			log.warn("Failed to identify client during security log!");
			entry.setClientId(0);
			entry.setClientname("UNKNOWN!");
		}

		entry.setTimestamp(new Date());
		entry.setIpAddress(ipAddress);
		entry.setMethod(method);
		entry.setRequest(request);
		entry.setClientVersion(trim(clientVersion, 64));
		entry.setTlsVersion(trim(tlsVersion, 64));
		entry.setResponseCode(trim(responseCode + "", 64));

		securityLogDao.save(entry);
	}
	
	@Transactional(rollbackFor = Exception.class)
	public void clean() {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.MONTH, -3);
		Date before = cal.getTime();

		securityLogDao.deleteByTimestampBefore(before);
	}

	private String trim(String text, int length) {
		if (text != null && text.length() > length) {
			return text.substring(0, length-1);
		}
		
		return text;
	}
}