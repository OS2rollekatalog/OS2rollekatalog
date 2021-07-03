package dk.digitalidentity.rc.controller.mvc.datatables.dao.model.dto;

import java.util.Date;
import java.util.Locale;

import org.springframework.context.MessageSource;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.AuditLogView;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDTO {
	private Date timestamp;
	private String entityName;
	private String entityType;
	private String eventType;
	private String secondaryEntityName;
	private String username;

	public AuditLogDTO(AuditLogView auditlog, MessageSource messageSource, Locale locale) {
		this.timestamp = auditlog.getTimestamp();
		this.entityName = auditlog.getEntityName();
		this.entityType = messageSource.getMessage(auditlog.getEntityType().getMessage(), null, locale);
		this.eventType = messageSource.getMessage(auditlog.getEventType().getMessage(), null, locale);
		this.secondaryEntityName = auditlog.getSecondaryEntityName();
		this.username = auditlog.getUsername();
	}
}
