package dk.digitalidentity.rc.controller.mvc.datatables.dao.model;

import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "view_datatables_auditlogs")
public class AuditLogView {

	@Id
	@Column
	private long id;

	@Column
	private Date timestamp;

	@Column
	private String username;

	@Enumerated(EnumType.STRING)
	@Column
	private EntityType entityType;

	@Column
	private String entityName;

	@Enumerated(EnumType.STRING)
	@Column
	private EventType eventType;

	@Column
	private String secondaryEntityName;

}
