package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.AuditLogViewDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.AuditLogView;
import dk.digitalidentity.rc.log.AuditLogEntryDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;

@Component
public class AuditLogService {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private AuditLogEntryDao auditLogEntryDao;

	@Autowired
	private AuditLogViewDao auditLogViewDao;

	@Transactional
	public void cleanupAuditlogs() {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.MONTH, -1 * configuration.getAudit().getMonthRetention());
		Date before = cal.getTime();

		auditLogEntryDao.deleteByTimestampBefore(before);
	}

	@Transactional
	public void cleanupExternalLoginAuditlogs() {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.MONTH, -1);
		Date before = cal.getTime();

		auditLogEntryDao.deleteLoginExternalByTimestampBefore(before);
	}

	public Iterable<AuditLogView> downloadAuditLog() {
		return auditLogViewDao.findAll(Sort.unsorted());
	}
}
