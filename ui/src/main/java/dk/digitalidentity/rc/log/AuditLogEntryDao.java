package dk.digitalidentity.rc.log;

import java.util.Date;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.AuditLog;

public interface AuditLogEntryDao extends CrudRepository<AuditLog, Long> {
	void deleteByTimestampBefore(Date before);
}
