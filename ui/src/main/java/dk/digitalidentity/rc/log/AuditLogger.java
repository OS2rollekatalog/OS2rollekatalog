package dk.digitalidentity.rc.log;

import dk.digitalidentity.rc.dao.model.AuditLog;
import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AuditLogger {

	@Autowired
	private AuditLogEntryDao auditLogEntryDao;

	public void log(AuditLoggable entity, EventType eventType) {
		log(entity, eventType, null, null);
	}
	
	public void log(AuditLoggable entity, EventType eventType, AuditLoggable secondaryEntity) {
		log(entity, eventType, secondaryEntity, null);
	}

	public void log(AuditLoggable entity, EventType eventType, AuditLoggable secondaryEntity, String stopdateUser) {
		String user = null;
		String loggedInUser = SecurityContextHolder.getContext().getAuthentication() == null
				? "system"
				: extractPrincipal();

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

		entry.setDescription(buildDescription());
		
		auditLogEntryDao.save(entry);
	}

	private static String buildDescription() {
		final Map<String, String> arguments = AuditLogContextHolder.getContext().getArguments();
		if (arguments != null) {
			return arguments.entrySet().stream()
					.map(e -> e.getKey() + "=" + e.getValue())
					.collect(Collectors.joining(", "));
		}
		return null;
	}

	private static String extractPrincipal() {
		Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (principal instanceof Saml2AuthenticatedPrincipal) {
			return ((Saml2AuthenticatedPrincipal) principal).getName();
		}
		return (String) principal;
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
