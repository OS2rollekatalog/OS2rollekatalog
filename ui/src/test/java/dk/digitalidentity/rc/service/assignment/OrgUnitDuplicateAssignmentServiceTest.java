package dk.digitalidentity.rc.service.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;

class OrgUnitDuplicateAssignmentServiceTest {

	// root -> a -> b -> c, and a -> sibling
	private static final Map<String, String> TREE = new HashMap<>();
	static {
		TREE.put("root", null);
		TREE.put("a", "root");
		TREE.put("b", "a");
		TREE.put("c", "b");
		TREE.put("sibling", "a");
	}

	private static OrgUnit ou(String uuid) {
		OrgUnit ou = new OrgUnit();
		ou.setUuid(uuid);
		return ou;
	}

	private static OrgUnitUserRoleAssignment assignment(long id, String ouUuid, boolean inherit) {
		OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
		assignment.setId(id);
		assignment.setOrgUnit(ou(ouUuid));
		assignment.setInherit(inherit);
		assignment.setAssignedTimestamp(new java.util.Date(id * 1000));
		return assignment;
	}

	private static OrgUnitUserRoleAssignment assignedAt(OrgUnitUserRoleAssignment assignment, long seconds) {
		assignment.setAssignedTimestamp(new java.util.Date(seconds * 1000));
		return assignment;
	}

	private static OrgUnitUserRoleAssignment withTitles(OrgUnitUserRoleAssignment assignment, ContainsTitles mode, String... titleUuids) {
		assignment.setContainsTitles(mode);
		assignment.setTitles(java.util.Arrays.stream(titleUuids).map(uuid -> {
			Title title = new Title();
			title.setUuid(uuid);
			return title;
		}).toList());
		return assignment;
	}

	private static OrgUnitUserRoleAssignment withExceptedOus(OrgUnitUserRoleAssignment assignment, String... ouUuids) {
		assignment.setContainsExceptedOus(true);
		assignment.setExceptedOus(java.util.Arrays.stream(ouUuids).map(OrgUnitDuplicateAssignmentServiceTest::ou).toList());
		return assignment;
	}

	/** redundant id -> id of the assignment it is a duplicate of */
	private static Map<Long, Long> duplicates(OrgUnitUserRoleAssignment... assignments) {
		Map<String, List<OrgUnitUserRoleAssignment>> assignmentsByOrgUnit = java.util.Arrays.stream(assignments)
			.collect(java.util.stream.Collectors.groupingBy(a -> a.getOrgUnit().getUuid()));

		Map<Long, Long> result = new HashMap<>();
		for (OrgUnitUserRoleAssignment assignment : assignments) {
			OrgUnitUserRoleAssignment duplicateOf = OrgUnitDuplicateAssignmentService.findDuplicateAssignment(assignment, assignmentsByOrgUnit, TREE);
			if (duplicateOf != null) {
				result.put(assignment.getId(), duplicateOf.getId());
			}
		}
		return result;
	}

	@Test
	@DisplayName("two identical assignments on the same unit - the one assigned last is redundant")
	void sameUnitExactDuplicate() {
		assertThat(duplicates(assignment(2, "b", false), assignment(1, "b", false))).containsExactly(Map.entry(2L, 1L));
	}

	@Test
	@DisplayName("two identical assignments on the same unit - the one assigned first is kept, whatever the id")
	void sameUnitEarliestAssignedIsKept() {
		assertThat(duplicates(assignedAt(assignment(1, "b", false), 200), assignedAt(assignment(2, "b", false), 100))).containsExactly(Map.entry(1L, 2L));
	}

	@Test
	@DisplayName("two identical assignments on the same unit assigned at the same time - exactly one is kept")
	void sameUnitSameTimestampKeepsOne() {
		Map<Long, Long> result = duplicates(assignedAt(assignment(1, "b", false), 100), assignedAt(assignment(2, "b", false), 100));

		assertThat(result).hasSize(1);
	}

	@Test
	@DisplayName("same unit, inherit + non-inherit - the non-inherited is redundant, even when it is older")
	void sameUnitNonInheritIsDuplicateOfInherit() {
		assertThat(duplicates(assignment(1, "b", false), assignment(2, "b", true))).containsExactly(Map.entry(1L, 2L));
	}

	@Test
	@DisplayName("an assignment below is a duplicate of an inherited one above, whatever its inherit flag")
	void descendantIsDuplicateOfInheritedAncestor() {
		assertThat(duplicates(assignment(1, "a", true), assignment(2, "c", false), assignment(3, "b", true)))
			.containsOnly(Map.entry(2L, 1L), Map.entry(3L, 1L));
	}

	@Test
	@DisplayName("an assignment above without inheritance has no duplicates below")
	void ancestorWithoutInheritIsNoDuplicate() {
		assertThat(duplicates(assignment(1, "a", false), assignment(2, "b", false))).isEmpty();
	}

	@Test
	@DisplayName("units on different branches are never duplicates")
	void siblingsAreNoDuplicates() {
		assertThat(duplicates(assignment(1, "b", true), assignment(2, "sibling", false))).isEmpty();
	}

	@Test
	@DisplayName("different filters are no duplicates")
	void differentFiltersAreNoDuplicates() {
		assertThat(duplicates(
				withTitles(assignment(1, "a", true), ContainsTitles.POSITIVE, "konsulent"),
				withTitles(assignment(2, "b", false), ContainsTitles.POSITIVE, "leder"),
				withTitles(assignment(3, "b", false), ContainsTitles.NEGATIVE, "konsulent"),
				assignment(4, "c", false)))
			.isEmpty();
	}

	@Test
	@DisplayName("identical filters are duplicates")
	void identicalFiltersAreDuplicates() {
		assertThat(duplicates(
				withTitles(assignment(1, "a", true), ContainsTitles.NEGATIVE, "sagsbehandler"),
				withTitles(assignment(2, "b", false), ContainsTitles.NEGATIVE, "sagsbehandler")))
			.containsExactly(Map.entry(2L, 1L));
	}

	@Test
	@DisplayName("excepted units must be identical, even when they do not touch the unit below")
	void differentExceptedOusAreNoDuplicates() {
		assertThat(duplicates(withExceptedOus(assignment(1, "a", true), "sibling"), assignment(2, "b", true))).isEmpty();
	}

	@Test
	@DisplayName("identical excepted units are duplicates when the excepted unit is not on the path")
	void identicalExceptedOusAreDuplicates() {
		assertThat(duplicates(withExceptedOus(assignment(1, "a", true), "c"), withExceptedOus(assignment(2, "b", true), "c")))
			.containsExactly(Map.entry(2L, 1L));
	}

	@Test
	@DisplayName("an assignment that excepts a unit on the path down does not reach the unit below")
	void exceptedOuOnPathIsNoDuplicate() {
		assertThat(duplicates(withExceptedOus(assignment(1, "a", true), "b"), withExceptedOus(assignment(2, "c", false), "b"))).isEmpty();
	}

	@Test
	@DisplayName("a group of assignments that are all duplicates keeps exactly one")
	void exactlyOneIsKept() {
		Map<Long, Long> result = duplicates(assignment(1, "b", true), assignment(2, "b", true), assignment(3, "b", false), assignment(4, "c", true), assignment(5, "c", false));

		assertThat(result.keySet()).containsExactlyInAnyOrder(2L, 3L, 4L, 5L);
	}
}
