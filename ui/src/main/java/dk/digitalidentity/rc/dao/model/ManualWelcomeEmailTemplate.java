package dk.digitalidentity.rc.dao.model;

import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationOperation;
import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "manual_welcome_email_templates")
public class ManualWelcomeEmailTemplate implements AuditLoggable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "it_system_id", nullable = false)
	private ItSystem itSystem;

	// ASSIGN = welcome mail sent when a role assignment is completed, REMOVE = mail sent when a
	// role removal is completed. One row per (itSystem, operation) - see uq_manual_welcome_email_templates_it_system_operation.
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ManualAssignmentEffectuationOperation operation;

	@Column
	@NotNull
	private String title;

	@Column
	@NotNull
	private String message;

	@Column
	private String notes;

	@Column
	private boolean enabled;

	@Override
	public String getEntityName() {
		return itSystem != null ? itSystem.getName() : "";
	}

	@Override
	public String getEntityId() {
		return Long.toString(id);
	}
}
