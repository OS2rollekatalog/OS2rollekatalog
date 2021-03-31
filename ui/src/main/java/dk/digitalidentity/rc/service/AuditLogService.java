package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

	@Autowired
	private AuditLogService self;

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

	@Scheduled(fixedDelay = 5 * 60 * 1000)
	public void resetAuditLogCache() {
		self.clearCache();
	}
	
	@CacheEvict(value = "auditLogEntries", allEntries = true)
	public void clearCache() {
		;
	}
	
	@Transactional
	public void cleanupAuditlogs() {
    	Calendar cal = Calendar.getInstance();
    	cal.add(Calendar.MONTH, -1 * configuration.getAudit().getMonthRetention());
    	Date before = cal.getTime();

    	auditLogEntryDao.deleteByTimestampBefore(before);
	}
}
