package dk.digitalidentity.rc.dao.model.assignment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "historic_excepted_assignment")
@Getter
@Setter
public class HistoricExceptedAssignment {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String recordHash;

	@Column(nullable = false)
	private long exceptionAssignmentId;

	private String exceptionUserUuid;
	private long exceptionUserRoleId;
	private String exceptionUserRoleName;
	private String exceptionUserRoleDescription;
	private Long exceptionRoleGroupId;
	private String exceptionRoleGroupName;
	private String exceptionRoleGroupDescription;
	private long exceptionItSystemId;
	private String exceptionItSystemName;
	private String exceptionOuUuid;
	private String exceptionOuName;
	private String exceptionTitleUuid;
	private String exceptionTitleName;

	@Column(name = "responsible_ou_uuid")
	private String responsibleOUUuid;

	@Column(name = "responsible_ou_name")
	private String responsibleOUName;

	private String assignedBy;

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

		HistoricExceptedAssignment that = (HistoricExceptedAssignment) o;

		return recordHash != null && Objects.equals(recordHash, that.recordHash);
	}

	@Override
	public int hashCode() {
		return this instanceof HibernateProxy
			? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode()
			: getClass().hashCode();
	}
}
