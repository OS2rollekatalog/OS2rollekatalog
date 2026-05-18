package dk.digitalidentity.rc.dao.model.assignment;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.util.HashUtil;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "current_assignment", indexes = {
	@Index(name = "idx_user_itsystem",
		columnList = "assignment_user_uuid, assignment_it_system_id"),
	@Index(name = "idx_userrole_itsystem",
		columnList = "assignment_user_role_id, assignment_it_system_id"),
	@Index(name = "idx_itsystem_userrole",
		columnList = "assignment_it_system_id, assignment_user_role_id"),
	@Index(name = "idx_ou",
		columnList = "assignment_ou_uuid"),
	@Index(name = "idx_responsible_ou",
		columnList = "responsible_ou_uuid"),
	@Index(name = "idx_current_assignment_user_dates",
		columnList = "assignment_user_uuid, start_date, stop_date"),
	@Index(name = "idx_current_assignment_dates",
		columnList = "start_date, stop_date"),
	@Index(name = "idx_current_assignment_role_group_dates",
		columnList = "assignment_role_group_id, start_date, stop_date")
})
@Getter
@Setter
public class CurrentAssignment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String recordHash;

	@Transient
	private String constraintSignature = "";

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	private LocalDate startDate;
	private LocalDate stopDate;

	@Column(nullable = false)
	private long assignmentId;

	@Column
	private String caseNumber;

	@Column
	private String assignedBy;

	@ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE, CascadeType.REFRESH})
	@JoinColumn(name = "assignment_user_uuid")
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE, CascadeType.REFRESH})
	@JoinColumn(name = "assignment_user_role_id")
	private UserRole userRole;

	@ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE, CascadeType.REFRESH})
	@JoinColumn(name = "assignment_it_system_id")
	private ItSystem itSystem;

	@ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE, CascadeType.REFRESH})
	@JoinColumn(name = "assignment_role_group_id")
	private RoleGroup roleGroup;

	@ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE, CascadeType.REFRESH})
	@JoinColumn(name = "assignment_ou_uuid")
	private OrgUnit orgUnit;

	@ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE, CascadeType.REFRESH})
	@JoinColumn(name = "assignment_title_uuid")
	private Title title;

	@ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE, CascadeType.REFRESH})
	@JoinColumn(name = "responsible_ou_uuid")
	private OrgUnit responsibleOrgUnit;

	@OneToMany(mappedBy = "currentAssignment", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<CurrentAssignmentPostponedConstraint> postponedConstraints = new HashSet<>();

	public boolean isActive() {
		LocalDate today = LocalDate.now();
		return (startDate == null || !startDate.isAfter(today)) // startdate is null or has started (today or earlier)
			&& (stopDate == null || stopDate.isAfter(today)); // stopdate is null or hasn't expired yet
	}

	@SuppressWarnings("Convert2MethodRef")
	public String generateRecordHash() {
		HashUtil.HashBuilder builder = HashUtil.builder()
			.add(user.getUuid())
			.add(userRole.getId())
			.add(itSystem.getId())
			.add(startDate)
			.add(stopDate)
			.add(assignmentId)
			.add(caseNumber)
			.addNullable(roleGroup, rg -> rg.getId())
			.addNullable(orgUnit, ou -> ou.getUuid())
			.addNullable(title, t -> t.getUuid())
			.addNullable(responsibleOrgUnit, rou -> rou.getUuid());

		if (postponedConstraints != null) {
			postponedConstraints.stream()
				.sorted(Comparator.comparingLong(CurrentAssignmentPostponedConstraint::getSystemRoleId)
					.thenComparing(CurrentAssignmentPostponedConstraint::getConstraintTypeEntityId))
				.forEach(pc -> {
					builder.add(pc.getSystemRoleId());
					builder.add(pc.getConstraintTypeEntityId());
					if (pc.getValue() != null) {
						pc.getValue().stream().sorted().forEach(builder::add);
					}
				});
		}

		builder.addNullable(constraintSignature, s -> s);

		return builder.build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null) return false;

		Class<?> oEffectiveClass = o instanceof HibernateProxy
			? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass()
			: o.getClass();
		Class<?> thisEffectiveClass = this instanceof HibernateProxy
			? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass()
			: this.getClass();

		if (thisEffectiveClass != oEffectiveClass) return false;

		CurrentAssignment that = (CurrentAssignment) o;

		return recordHash != null && Objects.equals(recordHash, that.recordHash);
	}

	@Override
	public int hashCode() {
		if (this instanceof HibernateProxy hibernateProxy) {
			return hibernateProxy.getHibernateLazyInitializer().getPersistentClass().hashCode();
		}
		return recordHash != null ? recordHash.hashCode() : getClass().hashCode();
	}
}
