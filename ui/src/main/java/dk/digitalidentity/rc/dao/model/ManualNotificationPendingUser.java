package dk.digitalidentity.rc.dao.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Marks a user whose role assignments changed and that is awaiting the next
 * manual-it-system notification flush. Rows are accumulated by
 * {@code ManualRolesUserAssignmentsChangedListener} and consumed (then deleted)
 * by {@code ManualRolesService.processPendingUsers()}.
 *
 * The unique constraint on userUuid gives free deduplication: a user that
 * changes several times between flushes is only processed once.
 */
@Getter
@Setter
@Entity
public class ManualNotificationPendingUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@Column(nullable = false, unique = true)
	private String userUuid;

	@Column(nullable = false)
	private LocalDateTime createdAt;
}
