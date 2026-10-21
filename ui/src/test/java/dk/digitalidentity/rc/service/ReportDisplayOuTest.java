package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOUUser;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.service.StreamingReportContext.UserPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Display OU resolution for the user-role report")
class ReportDisplayOuTest {

	private static final String ROOT = "root-uuid";
	private static final String CHILD = "child-uuid";
	private static final String GRANDCHILD = "grandchild-uuid";
	private static final String UNRELATED = "unrelated-uuid";
	private static final String TITLE = "title-uuid";
	private static final String OTHER_TITLE = "other-title-uuid";

	private final Map<String, HistoryOU> orgUnits = orgUnits(
		ou(ROOT, "Rådhuset", null),
		ou(CHILD, "Ældreområdet", ROOT),
		ou(GRANDCHILD, "Plejecenter", CHILD),
		ou(UNRELATED, "It-afdelingen", ROOT));

	private final Map<String, Set<String>> selfAndAncestors = StreamingReportContext.buildOuSelfAndAncestors(orgUnits);

	@Nested
	@DisplayName("buildOuSelfAndAncestors")
	class BuildOuSelfAndAncestors {

		@Test
		@DisplayName("includes the OU itself and every ancestor up to the root")
		void includesSelfAndAncestors() {
			assertThat(selfAndAncestors.get(GRANDCHILD)).containsExactlyInAnyOrder(GRANDCHILD, CHILD, ROOT);
			assertThat(selfAndAncestors.get(CHILD)).containsExactlyInAnyOrder(CHILD, ROOT);
			assertThat(selfAndAncestors.get(ROOT)).containsExactly(ROOT);
		}

		@Test
		@DisplayName("terminates on cyclic parent data")
		void terminatesOnCycle() {
			Map<String, HistoryOU> cyclic = orgUnits(ou("a", "A", "b"), ou("b", "B", "a"));

			assertThat(StreamingReportContext.buildOuSelfAndAncestors(cyclic).get("a")).containsExactlyInAnyOrder("a", "b");
		}

		@Test
		@DisplayName("stops when a parent is missing from the OU map")
		void stopsOnMissingParent() {
			Map<String, HistoryOU> orphan = orgUnits(ou("a", "A", "gone"));

			assertThat(StreamingReportContext.buildOuSelfAndAncestors(orphan).get("a")).containsExactly("a");
		}
	}

	@Nested
	@DisplayName("userPositionsMap")
	class UserPositionsMap {

		@Test
		@DisplayName("skips OUs the user is only attached to through a trusted function")
		void skipsFunctionOnlyAttachments() {
			HistoryOU employment = ou(CHILD, "Ældreområdet", ROOT);
			employment.setUsers(List.of(ouUser("user-uuid", true, TITLE, false)));
			HistoryOU functionOnly = ou(UNRELATED, "It-afdelingen", ROOT);
			functionOnly.setUsers(List.of(ouUser("user-uuid", false, null, false)));

			StreamingReportContext ctx = build(employment, functionOnly);

			assertThat(ctx.userPositionsMap.get("user-uuid"))
				.extracting(p -> p.ou().getOuUuid())
				.containsExactly(CHILD);
		}

		@Test
		@DisplayName("carries title and doNotInherit from the position row")
		void carriesPositionDetails() {
			HistoryOU employment = ou(CHILD, "Ældreområdet", ROOT);
			employment.setUsers(List.of(ouUser("user-uuid", true, TITLE, true)));

			StreamingReportContext ctx = build(employment);

			assertThat(ctx.userPositionsMap.get("user-uuid"))
				.singleElement()
				.satisfies(p -> {
					assertThat(p.titleUuid()).isEqualTo(TITLE);
					assertThat(p.doNotInherit()).isTrue();
				});
		}

		private StreamingReportContext build(HistoryOU... ous) {
			return StreamingReportContext.build(Map.of(), orgUnits(ous), List.<OrgUnit>of(), Map.of(), Map.of(), Map.of());
		}
	}

	@Nested
	@DisplayName("employmentOusReachedBy")
	class EmploymentOusReachedBy {

		@Test
		@DisplayName("shows the position the role was inherited down to, not the OU it was assigned on")
		void showsInheritingPosition() {
			assertThat(reached(ROOT, null, position(GRANDCHILD))).containsExactly(GRANDCHILD);
		}

		@Test
		@DisplayName("shows a position in the assignment OU itself")
		void showsPositionInAssignmentOu() {
			assertThat(reached(CHILD, null, position(CHILD))).containsExactly(CHILD);
		}

		@Test
		@DisplayName("shows every position below the assignment OU when the user has several")
		void showsAllPositionsBelowAssignmentOu() {
			assertThat(reached(CHILD, null, position(CHILD), position(GRANDCHILD), position(UNRELATED)))
				.containsExactly(CHILD, GRANDCHILD);
		}

		@Test
		@DisplayName("collapses two positions in the same OU into one row")
		void collapsesPositionsInSameOu() {
			assertThat(reached(CHILD, null, position(CHILD, TITLE, false), position(CHILD, OTHER_TITLE, false)))
				.containsExactly(CHILD);
		}

		@Test
		@DisplayName("skips a position that opts out of inheritance")
		void skipsDoNotInherit() {
			assertThat(reached(ROOT, null, position(GRANDCHILD, TITLE, true))).isEmpty();
		}

		@Test
		@DisplayName("skips a position that opts out of inheritance even on its own OU, as the rule engine does")
		void skipsDoNotInheritOnOwnOu() {
			assertThat(reached(GRANDCHILD, null, position(GRANDCHILD, TITLE, true))).isEmpty();
		}

		@Test
		@DisplayName("keeps only the position holding the title a title assignment was made for")
		void filtersOnTitle() {
			assertThat(reached(ROOT, TITLE, position(CHILD, TITLE, false), position(GRANDCHILD, OTHER_TITLE, false)))
				.containsExactly(CHILD);
		}

		@Test
		@DisplayName("drops positions outside the assignment OU, leaving the caller to fall back")
		void dropsUnrelatedPositions() {
			assertThat(reached(CHILD, null, position(UNRELATED))).isEmpty();
		}

		@Test
		@DisplayName("counts every position when the assignment is not tied to an OU")
		void countsAllPositionsForDirectAssignments() {
			assertThat(reached(null, null, position(CHILD), position(UNRELATED))).containsExactly(CHILD, UNRELATED);
		}

		@Test
		@DisplayName("returns empty when the user has no positions")
		void handlesNoPositions() {
			assertThat(ReportService.employmentOusReachedBy(CHILD, null, null, selfAndAncestors)).isEmpty();
			assertThat(ReportService.employmentOusReachedBy(CHILD, null, List.of(), selfAndAncestors)).isEmpty();
		}

		private List<String> reached(String assignmentOuUuid, String assignmentTitleUuid, UserPosition... positions) {
			return ReportService.employmentOusReachedBy(assignmentOuUuid, assignmentTitleUuid, List.of(positions), selfAndAncestors)
				.stream()
				.map(HistoryOU::getOuUuid)
				.toList();
		}
	}

	// ---- Common builders ---- //

	private UserPosition position(String ouUuid) {
		return position(ouUuid, TITLE, false);
	}

	private UserPosition position(String ouUuid, String titleUuid, boolean doNotInherit) {
		return new UserPosition(orgUnits.get(ouUuid), titleUuid, doNotInherit);
	}

	private static HistoryOU ou(String uuid, String name, String parentUuid) {
		HistoryOU ou = new HistoryOU();
		ou.setOuUuid(uuid);
		ou.setOuName(name);
		ou.setOuParentUuid(parentUuid);
		ou.setUsers(List.of());
		return ou;
	}

	private static HistoryOUUser ouUser(String userUuid, boolean hasPosition, String titleUuid, boolean doNotInherit) {
		HistoryOUUser ouUser = new HistoryOUUser();
		ouUser.setUserUuid(userUuid);
		ouUser.setHasPosition(hasPosition);
		ouUser.setTitleUuid(titleUuid);
		ouUser.setDoNotInherit(doNotInherit);
		return ouUser;
	}

	private static Map<String, HistoryOU> orgUnits(HistoryOU... ous) {
		Map<String, HistoryOU> map = new HashMap<>();
		for (HistoryOU ou : ous) {
			map.put(ou.getOuUuid(), ou);
		}
		return map;
	}
}
