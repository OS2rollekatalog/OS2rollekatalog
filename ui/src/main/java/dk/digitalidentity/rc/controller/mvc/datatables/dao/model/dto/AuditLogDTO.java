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
	private String entityTypeRaw;
	private String entityId;
	private String eventType;
	private String secondaryEntityName;
	private String username;
	private String description;

	public AuditLogDTO(AuditLogView auditlog, MessageSource messageSource, Locale locale) {
		this.timestamp = auditlog.getTimestamp();
		this.entityName = auditlog.getEntityName();
		this.entityType = auditlog.getEntityType() != null ? messageSource.getMessage(auditlog.getEntityType().getMessage(), null, locale) : null;
		this.entityTypeRaw = auditlog.getEntityType() != null ? auditlog.getEntityType().name() : null;
		this.entityId = auditlog.getEntityId();
		this.eventType = messageSource.getMessage(auditlog.getEventType().getMessage(), null, locale);
		this.secondaryEntityName = auditlog.getSecondaryEntityName();
		this.username = auditlog.getUsername();
		this.description = auditlog.getDescription();
	}
}
