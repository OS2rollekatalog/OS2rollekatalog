package dk.digitalidentity.rc.dao.history.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "history_ou_role_assignment_exclusions")
@Getter
@Setter
public class HistoryOURoleAssignmentExclusion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignment_id", nullable = false)
	private HistoryOURoleAssignment assignment;

	@Enumerated(EnumType.STRING)
	@Column(name = "exclusion_type", nullable = false, length = 20)
	private ExclusionType exclusionType;

	@Lob
	@Column(name = "user_uuids")
	private String userUuids; // comma-separated UUIDs

	@Lob
	@Column(name = "title_uuids")
	private String titleUuids; // comma-separated UUIDs

	@Lob
	@Column(name = "function_uuids")
	private String functionUuids; // comma-separated UUIDs

	public enum ExclusionType {
		excepted_users, titles, negative_titles, functions
	}
}
