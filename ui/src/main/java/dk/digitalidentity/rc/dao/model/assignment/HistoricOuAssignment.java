package dk.digitalidentity.rc.dao.model.assignment;

import dk.digitalidentity.rc.service.model.AssignedThrough;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Builder
@Entity
@Table(name = "historic_ou_assignment",
	indexes = {
		@Index(name = "idx_historic_ou_assignment_temporal", columnList = "valid_from, valid_to"),
		@Index(name = "idx_historic_ou_assignment_ou", columnList = "ou_uuid, valid_from, valid_to"),
	})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistoricOuAssignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String recordHash;

	// Temporal fields
	@Column(nullable = false)
	private LocalDateTime validFrom;

	private LocalDateTime validTo;

	// Identity
	@Column(nullable = false)
	private String ouUuid;

	private String ouName;

	@Column(nullable = false)
	private Long itSystemId;

	private String itSystemName;

	@Column(nullable = false)
	private Long roleId;

	private String roleName;

	@Column(columnDefinition = "TEXT")
	private String roleDescription;

	private Long roleRoleGroupId;

	private String roleRoleGroupName;

	@Column(columnDefinition = "TEXT")
	private String roleGroupDescription;

	@Builder.Default
	@Column(nullable = false)
	private boolean sensitiveRole = false;

	@Builder.Default
	@Column(nullable = false)
	private boolean extraSensitiveRole = false;

	private Long responsibleCollectionId;

	@Builder.Default
	@Column(nullable = false)
	private boolean itSystemAttestationExempt = false;

	// Assignment context
	@Enumerated(EnumType.STRING)
	private AssignedThrough assignedThroughType;

	private String assignedThroughUuid;

	private String assignedThroughName;

	private LocalDateTime assignedWhen;

	private String assignedByUserId;

	private String assignedByName;

	private LocalDate startDate;

	private LocalDate stopDate;

	// OU-level flags
	@Builder.Default
	@Column(nullable = false)
	private boolean appliesOnlyToManager = false;

	@Builder.Default
	@Column(nullable = false)
	private boolean appliesAlsoToSubstitutes = false;

	@Builder.Default
	@Column(nullable = false)
	private boolean inheritToChildren = false;

	// Exclusions (child table)
	@Builder.Default
	@OneToMany(mappedBy = "historicOuAssignment", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<HistoricOuAssignmentExclusion> exclusions = new ArrayList<>();
}
