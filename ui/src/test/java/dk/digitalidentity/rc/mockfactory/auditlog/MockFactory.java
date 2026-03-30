package dk.digitalidentity.rc.mockfactory.auditlog;

import dk.digitalidentity.rc.dao.model.AuditLog;
import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.EventType;

import java.util.Date;
import java.util.UUID;

public class MockFactory {

	public static AuditLog createAuditLog(EntityType entityType, EventType eventType, String username) {
		AuditLog auditLog = new AuditLog();
		auditLog.setTimestamp(new Date());
		auditLog.setUsername(username);
		auditLog.setEntityType(entityType);
		auditLog.setEntityId(UUID.randomUUID().toString());
		auditLog.setEntityName("Test Entity");
		auditLog.setEventType(eventType);
		auditLog.setIpAddress("127.0.0.1");
		return auditLog;
	}

	public static AuditLog createAuditLog(EntityType entityType, EventType eventType, String username,
										   EntityType secondaryEntityType, String secondaryEntityId, String secondaryEntityName) {
		AuditLog auditLog = createAuditLog(entityType, eventType, username);
		auditLog.setSecondaryEntityType(secondaryEntityType);
		auditLog.setSecondaryEntityId(secondaryEntityId);
		auditLog.setSecondaryEntityName(secondaryEntityName);
		return auditLog;
	}

}
