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
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@RequiredArgsConstructor
@Builder
@Entity
@Table(name = "historic_assignment",
	indexes = {
		@Index(name = "idx_historic_assignment_temporal", columnList = "valid_from, valid_to"),
	})
@Getter
@Setter
@AllArgsConstructor
public class HistoricAssignment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String recordHash;

	private LocalDateTime updatedAt;
	/**
	 * User-specified date for when the assignment takes effect
	 */
	private LocalDate startDate;

	/**
	 * User-specified date for when the assignment should stop having an effect.
	 */
	private LocalDate stopDate;
	/**
	 * Specifies when the assignment was created
	 */
	@Column(nullable = false)
	private LocalDateTime validFrom;

	/**
	 * Specifies the date when the assignment became invalid.
	 */
	private LocalDateTime validTo;

	private String userUuid;

	private String userId;

	private String userName;

	private Long userRoleId;

	private String userRoleName;

	private String userRoleDescription;

	@Builder.Default
	@Column(nullable = false)
	private Boolean sensitiveRole = false;

	@Builder.Default
	@Column(nullable = false)
	private Boolean extraSensitiveRole = false;

	private Long itSystemId;

	private String itSystemName;

	private Long roleGroupId;

	private String roleGroupName;

	private String roleGroupDescription;

	private String assignedBy;

	@Enumerated(EnumType.STRING)
	private AssignedThrough assignedThroughType;

	@Column(name = "assigned_through_ou_uuid")
	private String assignedThroughOUUuid;

	@Column(name = "assigned_through_ou_name")
	private String assignedThroughOUName;

	private String assignedThroughTitleUuid;

	private String assignedThroughTitleName;

	@Column(name = "assigned_through_rg_id")
	private Long assignedThroughRoleGroupId;
	@Column(name = "assigned_through_rg_name")
	private String assignedThroughRoleGroupName;

	/**
	 * The OU whose manager is responsible for attesting this assignment.
	 * <p>
	 * Mutually exclusive with {@link #responsibleUserUuid}: exactly one of the two is set.
	 * This field is null when the IT-system has a designated attestation responsible
	 * ({@link #responsibleUserUuid} is set instead).
	 * <p>
	 * For manager users, this is redirected to the nearest parent OU with a different manager,
	 * so managers do not attest their own access.
	 */
	@Column(name = "responsible_ou_uuid")
	private String responsibleOUUuid;

	@Column(name = "responsible_ou_name")
	private String responsibleOUName;

	@Builder.Default
	@OneToMany(mappedBy = "historicAssignment", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<HistoricAssignmentConstraint> constraints = new HashSet<>();

	/**
	 * The UUID of the IT-system's designated attestation responsible for this assignment.
	 * <p>
	 * Mutually exclusive with {@link #responsibleOUUuid}: exactly one of the two is set.
	 * This field is only set when all three conditions hold:
	 * <ol>
	 *   <li>The assignment is not via a role group</li>
	 *   <li>The role has {@code roleAssignmentAttestationByAttestationResponsible = true}</li>
	 *   <li>The IT-system has a non-null {@code attestationResponsible}</li>
	 * </ol>
	 * When set, the attestation tracker routes this assignment to an IT-system attestation
	 * rather than an organisation attestation.
	 */
	@Column(name = "responsible_user_uuid")
	private String responsibleUserUuid;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null) return false;

		// Get the ACTUAL class, not the proxy class
		Class<?> oEffectiveClass = o instanceof HibernateProxy
			? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass()
			: o.getClass();
		Class<?> thisEffectiveClass = this instanceof HibernateProxy
			? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass()
			: this.getClass();

		if (thisEffectiveClass != oEffectiveClass) return false;

		HistoricAssignment that = (HistoricAssignment) o;

		// Use recordHash as business key for equality
		return recordHash != null && Objects.equals(recordHash, that.recordHash);
	}

	@Override
	public int hashCode() {
		// Return constant hashCode based on actual class
		return this instanceof HibernateProxy
			? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode()
			: getClass().hashCode();
	}
}
