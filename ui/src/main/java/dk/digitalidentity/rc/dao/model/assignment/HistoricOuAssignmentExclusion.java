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

	public enum ExclusionType {
		EXCEPTED_USERS, POSITIVE_TITLES, NEGATIVE_TITLES, FUNCTIONS
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
