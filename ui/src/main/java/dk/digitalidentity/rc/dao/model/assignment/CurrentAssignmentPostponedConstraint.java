package dk.digitalidentity.rc.dao.model.assignment;

import dk.digitalidentity.rc.config.StringListConverter;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "current_assignment_postponed_constraint")
@Getter
@Setter
public class CurrentAssignmentPostponedConstraint {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "current_assignment_id", nullable = false,
		foreignKey = @ForeignKey(name = "fk_current_assignment_postponed_constraint_current_assignment_id"))
	private CurrentAssignment currentAssignment;

	@Column(nullable = false)
	private long constraintTypeId;

	@Column(nullable = false)
	private String constraintTypeUuid;

	@Column(nullable = false)
	private String constraintTypeName;

	@Column(nullable = false)
	private String constraintTypeEntityId;

	@Column(nullable = false, name = "constraint_type_ui_type")
	@Enumerated(EnumType.STRING)
	private ConstraintUIType constraintTypeUIType;

	@Column(nullable = false)
	private long systemRoleId;

	/**
	 * Contains comma-seperated list of values.
	 * Values are not strongly typed, so you might need to parse to long
	 */
	@Convert(converter = StringListConverter.class)
	@Column(nullable = false)
	private List<String> value = new ArrayList<>();

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

		CurrentAssignmentPostponedConstraint that = (CurrentAssignmentPostponedConstraint) o;

		// Use ID for equality - safe if entity is always persisted before using in Sets
		return id != null && Objects.equals(id, that.id);
	}

	@Override
	public int hashCode() {
		return this instanceof HibernateProxy
			? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode()
			: getClass().hashCode();
	}
}
