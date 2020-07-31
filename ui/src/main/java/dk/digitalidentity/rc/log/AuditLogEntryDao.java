package dk.digitalidentity.rc.log;

import java.util.Date;
import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.AuditLog;
import dk.digitalidentity.rc.dao.model.enums.EventType;

public interface AuditLogEntryDao extends CrudRepository<AuditLog, Long> {
	void deleteByTimestampBefore(Date before);

	List<AuditLog> findByTimestampAfterAndEventTypeInOrderByTimestampDesc(Date date, List<EventType> eventTypes);
}
