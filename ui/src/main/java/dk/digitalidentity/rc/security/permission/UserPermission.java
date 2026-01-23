package dk.digitalidentity.rc.security.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Stores permissions for users in database. Rows are only created on login, and are cached for retrieval.
 */
@Builder
@Getter
@Setter
@RequiredArgsConstructor
@Entity
@Table(name = "user_permission")
@AllArgsConstructor
public class UserPermission {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_uuid", nullable = false)
	private String userUuid;

	@Enumerated(EnumType.STRING)
	@Column(name = "section", nullable = false, length = 50)
	private Section section;

	@Enumerated(EnumType.STRING)
	@Column(name = "permission", nullable = false, length = 50)
	private Permission permission;

	@Column(name = "constrained_itsystem_ids", columnDefinition = "TEXT")
	private String constrainedItSystemIds;

	@Column(name = "constrained_ou_uuids", columnDefinition = "TEXT")
	private String constrainedOuUuids;

}
