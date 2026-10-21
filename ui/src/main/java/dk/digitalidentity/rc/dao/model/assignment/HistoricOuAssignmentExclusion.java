package dk.digitalidentity.rc.dao.model.assignment;

import dk.digitalidentity.rc.config.StringListConverter;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Builder
@Entity
@Table(name = "historic_ou_assignment_exclusion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistoricOuAssignmentExclusion {

	@Getter
	public enum ExclusionType {
		EXCEPTED_USERS(1), POSITIVE_TITLES(1), NEGATIVE_TITLES(1), FUNCTIONS(1), EXCEPTED_OUS(2);

		/** The identity version that introduced this type. A new type changes the record hash. */
		private final int since;

		ExclusionType(int since) {
			this.since = since;
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "historic_ou_assignment_id", nullable = false,
		foreignKey = @ForeignKey(name = "fk_historic_ou_assignment_exclusion_assignment_id"))
	private HistoricOuAssignment historicOuAssignment;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ExclusionType exclusionType;

	@Convert(converter = StringListConverter.class)
	@Column(columnDefinition = "TEXT")
	private List<String> uuids;
}
