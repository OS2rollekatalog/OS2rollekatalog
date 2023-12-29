package dk.digitalidentity.rc.log;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import dk.digitalidentity.rc.dao.model.AuditLog;
import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.EventType;

@Component
public class AuditLogger {

	@Autowired
	private AuditLogEntryDao auditLogEntryDao;

	public void log(AuditLoggable entity, EventType eventType, String description) {
		log(entity, eventType, null, description, null);
	}

	public void log(AuditLoggable entity, EventType eventType) {
		log(entity, eventType, null, null, null);
	}
	
	public void log(AuditLoggable entity, EventType eventType, AuditLoggable secondaryEntity) {
		log(entity, eventType, secondaryEntity, null, null);
	}

	public void log(AuditLoggable entity, EventType eventType, AuditLoggable secondaryEntity, String stopDateUser) {
		log(entity, eventType, secondaryEntity, null, stopDateUser);
	}

	public void log(AuditLoggable entity, EventType eventType, AuditLoggable secondaryEntity, String description, String stopdateUser) {
		String user = null;
		String loggedInUser = SecurityContextHolder.getContext().getAuthentication() == null ? "system" : (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

		if (stopdateUser != null && loggedInUser.equalsIgnoreCase("system")) {
			user = "System på vegne af " + stopdateUser;
		} else {
			user = loggedInUser;
		}

		AuditLog entry = new AuditLog();
		entry.setUsername(user);
		entry.setIpAddress(getClientIp());
		entry.setTimestamp(new Date());

		entry.setEntityId(entity.getEntityId());
		entry.setEntityType(EntityType.getEntityType(entity));
		entry.setEntityName(entity.getEntityName());
		entry.setEventType(eventType);

		if (secondaryEntity != null) {
			entry.setSecondaryEntityId(secondaryEntity.getEntityId());
			entry.setSecondaryEntityType(EntityType.getEntityType(secondaryEntity));
			entry.setSecondaryEntityName(secondaryEntity.getEntityName());
		}

		entry.setDescription(description);
		
		auditLogEntryDao.save(entry);
	}

	private static String getClientIp() {
		String remoteAddr = "";

		HttpServletRequest request = getRequest();
		if (request != null) {
			remoteAddr = request.getHeader("X-FORWARDED-FOR");
			if (remoteAddr == null || "".equals(remoteAddr)) {
				remoteAddr = request.getRemoteAddr();
			}
		}

		return remoteAddr;
	}

	private static HttpServletRequest getRequest() {
		try {
			return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
		}
		catch (IllegalStateException ex) {
			return null;
		}
	}
}
