package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.AuditLog;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.log.AuditLogEntryDao;

@Component
@EnableCaching
public class AuditLogService {
	private static List<EventType> eventTypes;
	
	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private AuditLogEntryDao auditLogEntryDao;

	static {
		eventTypes = new ArrayList<>();
		eventTypes.add(EventType.ASSIGN_KLE);
		eventTypes.add(EventType.REMOVE_KLE);
		eventTypes.add(EventType.ASSIGN_SYSTEMROLE);
		eventTypes.add(EventType.REMOVE_SYSTEMROLE);
		eventTypes.add(EventType.ASSIGN_ROLE_GROUP);
		eventTypes.add(EventType.REMOVE_ROLE_GROUP);
		eventTypes.add(EventType.ASSIGN_USER_ROLE);
		eventTypes.add(EventType.REMOVE_USER_ROLE);
		eventTypes.add(EventType.ATTESTED_ORGUNIT);
	}

	@Cacheable(value = "auditLogEntries")
	public List<AuditLog> getAuditLogEntries() {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, (-1 * configuration.getAudit().getUiDays()));
		
		return auditLogEntryDao.findByTimestampAfterAndEventTypeInOrderByTimestampDesc(cal.getTime(), eventTypes);
	}

	@Scheduled(fixedDelay = 60 * 1000)
	@CacheEvict(value = "auditLogEntries", allEntries = true)
	public void resetAuditLogCache() {
		; // clears cache every minute - we just want to protect
		  // against force-refresh in the browser, as the lookup
		  // can be a bit intensive
	}
}
