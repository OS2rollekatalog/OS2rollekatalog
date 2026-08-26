package dk.digitalidentity.rc.dao.model;

import java.time.LocalDateTime;

import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationOperation;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationStatus;
import dk.digitalidentity.rc.log.AuditLoggable;
import jakarta.annotation.Nullable;
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
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "manual_assignment_effectuation")
@Getter
@Setter
public class ManualAssignmentEffectuation implements AuditLoggable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_uuid", nullable = false)
	private User user;

	@Nullable
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_role_id")
	private UserRole userRole;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "it_system_id", nullable = false)
	private ItSystem itSystem;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ManualAssignmentEffectuationOperation operation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ManualAssignmentEffectuationStatus status;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column
	private LocalDateTime completedAt;

	@Column
	private String completedByUserUuid;

	@Column
	private String comment;

	@Column(nullable = false)
	private boolean emailSent;

	@Override
	public String getEntityId() {
		return Long.toString(id);
	}

	@Override
	public String getEntityName() {
		String roleName = userRole != null ? userRole.getName() : "";
		return user.getName() + " - " + itSystem.getName() + " - " + roleName;
	}
}
