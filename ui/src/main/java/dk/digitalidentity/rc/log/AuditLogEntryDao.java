package dk.digitalidentity.rc.log;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.model.AuditLog;

public interface AuditLogEntryDao extends JpaRepository<AuditLog, Long> {

	@Modifying
	@Query(value = "DELETE FROM audit_log WHERE event_type = 'LOGIN_EXTERNAL' AND timestamp < ?1 LIMIT 10000 ", nativeQuery = true)
	void deleteLoginExternalByTimestampBefore(Date before);

	@Modifying
	@Query(value = "DELETE FROM audit_log WHERE timestamp < ?1 LIMIT 25000 ", nativeQuery = true)
	void deleteByTimestampBefore(Date before);

	@Query(value = "SELECT max(id) FROM audit_log", nativeQuery = true)
	long getMaxId();

	@Query(value = "SELECT a.* FROM audit_log a WHERE a.id > ?1 ORDER BY a.id ASC LIMIT ?2", nativeQuery = true)
	public List<AuditLog> findAllWithOffsetAndSize(long offset, long size);

}
