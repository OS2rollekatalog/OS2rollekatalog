package dk.digitalidentity.rc.dao.model.assignment;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Point-in-time snapshot of a system role assignment on a user role.
 * All denormalized fields (names, descriptions, responsibleCollectionId) reflect values at
 * the time the assignment was recorded and are not updated if those values change later.
 */
@Builder
@Entity
@Table(name = "historic_it_system_assignment",
	indexes = {
		@Index(name = "idx_historic_it_system_assignment_temporal", columnList = "valid_from, valid_to"),
		@Index(name = "idx_historic_it_system_assignment_it_system", columnList = "it_system_id, valid_from, valid_to"),
	})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistoricItSystemAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String recordHash;

	@Column(nullable = false)
	private LocalDateTime validFrom;

	private LocalDateTime validTo;

	@Column(nullable = false)
	private Long itSystemId;

	private String itSystemName;

	@Builder.Default
	@Column(nullable = false)
	private boolean itSystemAttestationExempt = false;

	@Column(name = "responsible_collection_id")
	private Long responsibleCollectionId;

	@Column(nullable = false)
	private Long userRoleId;

	private String userRoleName;

	@Column(columnDefinition = "TEXT")
	private String userRoleDescription;

	@Column(nullable = false)
	private Long systemRoleId;

	private String systemRoleName;

	@Column(columnDefinition = "TEXT")
	private String systemRoleDescription;

	@Builder.Default
	@OneToMany(mappedBy = "historicItSystemAssignment", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<HistoricItSystemAssignmentConstraint> constraints = new ArrayList<>();
}
