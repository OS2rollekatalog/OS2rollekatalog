package dk.digitalidentity.rc.dao.model.assignment;

import dk.digitalidentity.rc.config.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Builder
@Entity
@Table(name = "historic_assignment_constraint")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistoricAssignmentConstraint {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "historic_assignment_id", nullable = false,
		foreignKey = @ForeignKey(name = "fk_historic_assignment_constraint_historic_assignment_id"))
	private HistoricAssignment historicAssignment;

	@Column(nullable = false)
	private String constraintTypeUuid;

	@Column(nullable = false)
	private String constraintTypeName;

	@Column(nullable = false)
	private String constraintTypeEntityId;

	/**
	 * Contains comma-seperated list of values.
	 * Values are not strongly typed, so you might need to parse to long
	 */
	@Builder.Default
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

		HistoricAssignmentConstraint that = (HistoricAssignmentConstraint) o;

		// Use ID for equality - safe since entity is always persisted before using in Sets
		return id != null && Objects.equals(id, that.id);
	}

	@Override
	public int hashCode() {
		return this instanceof HibernateProxy
			? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode()
			: getClass().hashCode();
	}
}
