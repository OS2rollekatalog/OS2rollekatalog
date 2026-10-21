package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion.ExclusionType;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The literal was produced by the calculator before identity versions existed. If it stops matching,
 * every open row whose hash is looked up through the older version can no longer be closed.
 */
@DisplayName("HistoricOuAssignmentHashCalculator")
class HistoricOuAssignmentHashCalculatorTest {

	private static final String STORED_HASH = "hH30V7d6yvOcPNHKfQsUxodw3R8lm6sFsR7eZxdycUo";

	private HistoricOuAssignment assignment;

	@BeforeEach
	void setUp() {
		assignment = HistoricOuAssignment.builder()
			.ouUuid("ou-uuid")
			.itSystemId(10L)
			.roleId(20L)
			.roleRoleGroupId(30L)
			.assignedThroughType(AssignedThrough.ORGUNIT)
			.assignedThroughUuid("ou-uuid")
			.appliesOnlyToManager(true)
			.appliesAlsoToSubstitutes(false)
			.inheritToChildren(true)
			.startDate(LocalDate.of(2026, 1, 1))
			.exclusions(new ArrayList<>(List.of(exclusion(ExclusionType.EXCEPTED_USERS, "user-uuid-b", "user-uuid-a"))))
			.build();
	}

	private static HistoricOuAssignmentExclusion exclusion(ExclusionType type, String... uuids) {
		return HistoricOuAssignmentExclusion.builder()
			.exclusionType(type)
			.uuids(List.of(uuids))
			.build();
	}

	@Nested
	@DisplayName("compute")
	class Compute {

		@Test
		@DisplayName("keeps the stored hash for a row without excepted org units")
		void keepsStoredHashWithoutExceptedOus() {
			// ---- When ---- //
			String hash = HistoricOuAssignmentHashCalculator.compute(assignment);

			// ---- Then ---- //
			assertThat(hash).isEqualTo(STORED_HASH);
		}

		@Test
		@DisplayName("version 1 ignores excepted org units and matches the stored hash")
		void versionOneIgnoresExceptedOus() {
			// ---- Given ---- //
			assignment.getExclusions().add(exclusion(ExclusionType.EXCEPTED_OUS, "excepted-ou-uuid"));

			// ---- When ---- //
			String hash = HistoricOuAssignmentHashCalculator.compute(assignment, 1);

			// ---- Then ---- //
			assertThat(hash).isEqualTo(STORED_HASH);
		}
	}

	@Nested
	@DisplayName("olderHashes")
	class OlderHashes {

		@Test
		@DisplayName("is empty when no exclusion type newer than version 1 is present")
		void emptyWithoutNewerExclusionTypes() {
			// ---- When ---- //
			List<String> olderHashes = HistoricOuAssignmentHashCalculator.olderHashes(assignment);

			// ---- Then ---- //
			assertThat(olderHashes).isEmpty();
		}

		@Test
		@DisplayName("contains the stored hash when excepted org units are present")
		void containsStoredHashWithExceptedOus() {
			// ---- Given ---- //
			assignment.getExclusions().add(exclusion(ExclusionType.EXCEPTED_OUS, "excepted-ou-uuid"));

			// ---- When ---- //
			List<String> olderHashes = HistoricOuAssignmentHashCalculator.olderHashes(assignment);

			// ---- Then ---- //
			assertThat(olderHashes).containsExactly(STORED_HASH);
		}
	}
}
