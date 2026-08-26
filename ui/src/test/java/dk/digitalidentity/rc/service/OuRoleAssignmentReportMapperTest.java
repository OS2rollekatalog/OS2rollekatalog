package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.controller.mvc.viewmodel.OuRoleAssignmentReportRow;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion.ExclusionType;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OuRoleAssignmentReportMapper")
class OuRoleAssignmentReportMapperTest {

	private final OuRoleAssignmentReportMapper mapper = new OuRoleAssignmentReportMapper();

	private static final String ROOT = "root-uuid";
	private static final String CHILD = "child-uuid";
	private static final String GRANDCHILD = "grandchild-uuid";

	// ---- Common builders ---- //

	private HistoricOuAssignment.HistoricOuAssignmentBuilder base() {
		return HistoricOuAssignment.builder()
			.recordHash("record-hash")
			.ouUuid(ROOT)
			.ouName("Rådhuset")
			.itSystemId(10L)
			.itSystemName("It-system")
			.roleId(20L)
			.roleName("Læseadgang")
			.assignedThroughType(AssignedThrough.DIRECT)
			.assignedThroughUuid(ROOT)
			.assignedThroughName("Rådhuset")
			.assignedByUserId("assigner-id")
			.assignedByName("Assigner Name")
			.inheritToChildren(false)
			.exclusions(new ArrayList<>());
	}

	private static HistoryOU orgUnit(final String uuid, final String name, final String parentUuid) {
		final HistoryOU orgUnit = new HistoryOU();
		orgUnit.setOuUuid(uuid);
		orgUnit.setOuName(name);
		orgUnit.setOuParentUuid(parentUuid);
		return orgUnit;
	}

	/** Rådhuset → Teknik og Miljø → Vej og Park */
	private static Map<String, HistoryOU> threeLevelTree() {
		final Map<String, HistoryOU> orgUnits = new HashMap<>();
		orgUnits.put(ROOT, orgUnit(ROOT, "Rådhuset", null));
		orgUnits.put(CHILD, orgUnit(CHILD, "Teknik og Miljø", ROOT));
		orgUnits.put(GRANDCHILD, orgUnit(GRANDCHILD, "Vej og Park", CHILD));
		return orgUnits;
	}

	private List<OuRoleAssignmentReportRow> mapToRows(
		final HistoricOuAssignment assignment,
		final Map<String, HistoryOU> orgUnits
	) {
		return mapper.toReportRowsByOuUuid(List.of(assignment), orgUnits)
			.values()
			.stream()
			.flatMap(List::stream)
			.toList();
	}

	// ---- ------------- ---- //

	@Nested
	@DisplayName("non-inheriting assignments")
	class NonInheriting {

		@Test
		@DisplayName("emits exactly one row for the assignment's own org unit")
		void emitsSingleRow() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().inheritToChildren(false).build();

			// ---- When ---- //
			Map<String, List<OuRoleAssignmentReportRow>> rows = mapper.toReportRowsByOuUuid(
				List.of(assignment), threeLevelTree());

			// ---- Then ---- //
			assertThat(rows).containsOnlyKeys(ROOT);
		}

		@Test
		@DisplayName("preserves the stored assignedThroughType instead of forcing ORGUNIT")
		void preservesAssignedThroughType() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().inheritToChildren(false)
				.assignedThroughType(AssignedThrough.ROLEGROUP)
				.build();

			// ---- When ---- //
			List<OuRoleAssignmentReportRow> rows = mapToRows(assignment, threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.getFirst().getAssignedThroughType()).isEqualTo(AssignedThrough.ROLEGROUP);
		}

		@Test
		@DisplayName("marks inherit false")
		void marksInheritFalse() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().inheritToChildren(false).build();

			// ---- When ---- //
			List<OuRoleAssignmentReportRow> rows = mapToRows(assignment, threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.getFirst().isInherit()).isFalse();
		}
	}

	@Nested
	@DisplayName("inherit marker")
	class InheritMarker {

		@Test
		@DisplayName("marks the origin row of an inheriting user role assignment")
		void userRoleOriginIsMarked() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().inheritToChildren(true).roleRoleGroupId(null).build();

			// ---- When ---- //
			Map<String, List<OuRoleAssignmentReportRow>> rows = mapper.toReportRowsByOuUuid(List.of(assignment), threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.get(ROOT).getFirst().isInherit()).isTrue();
		}

		@Test
		@DisplayName("does not mark inherited descendant rows of a user role assignment")
		void userRoleDescendantIsNotMarked() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().inheritToChildren(true).roleRoleGroupId(null).build();

			// ---- When ---- //
			Map<String, List<OuRoleAssignmentReportRow>> rows = mapper.toReportRowsByOuUuid(List.of(assignment), threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.get(CHILD).getFirst().isInherit()).isFalse();
		}

		@Test
		@DisplayName("marks the origin row of an inheriting role group assignment")
		void roleGroupOriginIsMarked() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.inheritToChildren(true)
					.roleRoleGroupId(30L)
					.roleRoleGroupName("Rollegruppe")
					.build();

			// ---- When ---- //
			Map<String, List<OuRoleAssignmentReportRow>> rows = mapper.toReportRowsByOuUuid(List.of(assignment), threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.get(ROOT).getFirst().isInherit()).isTrue();
		}

		@Test
		@DisplayName("does not mark inherited descendant rows of a role group assignment")
		void roleGroupDescendantIsNotMarked() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.inheritToChildren(true)
					.roleRoleGroupId(30L)
					.roleRoleGroupName("Rollegruppe")
					.build();

			// ---- When ---- //
			Map<String, List<OuRoleAssignmentReportRow>> rows = mapper.toReportRowsByOuUuid(List.of(assignment), threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.get(GRANDCHILD).getFirst().isInherit()).isFalse();
		}

		@Test
		@DisplayName("does not mark a role group assignment that does not inherit")
		void nonInheritingRoleGroupIsNotMarked() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base()
					.inheritToChildren(false)
					.roleRoleGroupId(30L)
					.roleRoleGroupName("Rollegruppe")
					.build();

			// ---- When ---- //
			Map<String, List<OuRoleAssignmentReportRow>> rows = mapper.toReportRowsByOuUuid(List.of(assignment), threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.get(ROOT).getFirst().isInherit()).isFalse();
		}
	}

	@Nested
	@DisplayName("inheritance expansion")
	class InheritanceExpansion {

		@Test
		@DisplayName("emits one row per org unit in the subtree, including the origin")
		void emitsRowPerDescendant() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().inheritToChildren(true).build();

			// ---- When ---- //
			Map<String, List<OuRoleAssignmentReportRow>> rows = mapper.toReportRowsByOuUuid(
				List.of(assignment), threeLevelTree());

			// ---- Then ---- //
			assertThat(rows).containsOnlyKeys(ROOT, CHILD, GRANDCHILD);
		}

		@Test
		@DisplayName("points descendant rows at the originating org unit via assignedThroughUuid")
		void descendantsPointAtOrigin() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().inheritToChildren(true).build();

			// ---- When ---- //
			Map<String, List<OuRoleAssignmentReportRow>> rows = mapper.toReportRowsByOuUuid(
				List.of(assignment), threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.get(GRANDCHILD).getFirst().getAssignedThroughUuid()).isEqualTo(ROOT);
		}

		@Test
		@DisplayName("names the originating org unit on descendant rows")
		void descendantsNameOrigin() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().inheritToChildren(true).build();

			// ---- When ---- //
			Map<String, List<OuRoleAssignmentReportRow>> rows = mapper.toReportRowsByOuUuid(
				List.of(assignment), threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.get(GRANDCHILD).getFirst().getAssignedThroughName()).isEqualTo("Rådhuset");
		}

		@Test
		@DisplayName("sets assignedThroughType ORGUNIT on descendant rows")
		void descendantsAssignedThroughOrgUnit() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().inheritToChildren(true).build();

			// ---- When ---- //
			Map<String, List<OuRoleAssignmentReportRow>> rows = mapper.toReportRowsByOuUuid(
				List.of(assignment), threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.get(CHILD).getFirst().getAssignedThroughType()).isEqualTo(AssignedThrough.ORGUNIT);
		}

		@Test
		@DisplayName("emits no rows when the originating org unit is absent from the snapshot")
		void skipsWhenOriginOrgUnitInactive() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().ouUuid("inactive-uuid").inheritToChildren(true).build();

			// ---- When ---- //
			Map<String, List<OuRoleAssignmentReportRow>> rows = mapper.toReportRowsByOuUuid(
				List.of(assignment), threeLevelTree());

			// ---- Then ---- //
			assertThat(rows).isEmpty();
		}

		@Test
		@DisplayName("does not traverse through an org unit absent from the snapshot")
		void inactiveOrgUnitBlocksItsSubtree() {
			// ---- Given ---- //
			Map<String, HistoryOU> orgUnits = threeLevelTree();
			orgUnits.remove(CHILD);
			HistoricOuAssignment assignment = base().inheritToChildren(true).build();

			// ---- When ---- //
			Map<String, List<OuRoleAssignmentReportRow>> rows = mapper.toReportRowsByOuUuid(
				List.of(assignment), orgUnits);

			// ---- Then ---- //
			assertThat(rows).containsOnlyKeys(ROOT);
		}
	}

	@Nested
	@DisplayName("role group fan-out")
	class RoleGroupFanOut {

		@Test
		@DisplayName("keeps one row per contained role per org unit")
		void oneRowPerRolePerOrgUnit() {
			// ---- Given ---- //
			HistoricOuAssignment firstRole = base().roleId(20L).roleRoleGroupId(30L).inheritToChildren(true).build();
			HistoricOuAssignment secondRole = base().roleId(21L).roleRoleGroupId(30L).inheritToChildren(true).build();

			// ---- When ---- //
			Map<String, List<OuRoleAssignmentReportRow>> rows = mapper.toReportRowsByOuUuid(
				List.of(firstRole, secondRole), threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.get(CHILD)).extracting(OuRoleAssignmentReportRow::getRoleId).containsExactly(20L, 21L);
		}
	}

	@Nested
	@DisplayName("exclusion mapping")
	class ExclusionMapping {

		private HistoricOuAssignment withExclusion(final ExclusionType type, final String... uuids) {
			HistoricOuAssignmentExclusion exclusion = HistoricOuAssignmentExclusion.builder()
				.exclusionType(type)
				.uuids(List.of(uuids))
				.build();
			return base().exclusions(new ArrayList<>(List.of(exclusion))).build();
		}

		@Test
		@DisplayName("maps EXCEPTED_USERS to the excepted users column")
		void mapsExceptedUsers() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = withExclusion(ExclusionType.EXCEPTED_USERS, "user-uuid");

			// ---- When ---- //
			List<OuRoleAssignmentReportRow> rows = mapToRows(assignment, threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.getFirst().getExceptedUserUuids()).containsExactly("user-uuid");
		}

		@Test
		@DisplayName("maps FUNCTIONS to the functions column")
		void mapsFunctions() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = withExclusion(ExclusionType.FUNCTIONS, "function-uuid");

			// ---- When ---- //
			List<OuRoleAssignmentReportRow> rows = mapToRows(assignment, threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.getFirst().getFunctionUuids()).containsExactly("function-uuid");
		}

		@Test
		@DisplayName("merges POSITIVE_TITLES and NEGATIVE_TITLES into the single titles column")
		void mergesPositiveAndNegativeTitles() {
			// ---- Given ---- //
			HistoricOuAssignmentExclusion positive = HistoricOuAssignmentExclusion.builder()
				.exclusionType(ExclusionType.POSITIVE_TITLES)
				.uuids(List.of("positive-title-uuid"))
				.build();
			HistoricOuAssignmentExclusion negative = HistoricOuAssignmentExclusion.builder()
				.exclusionType(ExclusionType.NEGATIVE_TITLES)
				.uuids(List.of("negative-title-uuid"))
				.build();
			HistoricOuAssignment assignment = base().exclusions(new ArrayList<>(List.of(positive, negative))).build();

			// ---- When ---- //
			List<OuRoleAssignmentReportRow> rows = mapToRows(assignment, threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.getFirst().getTitleUuids()).containsExactly("positive-title-uuid", "negative-title-uuid");
		}

		@Test
		@DisplayName("keeps exclusions out of columns of a different type")
		void doesNotLeakAcrossColumns() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = withExclusion(ExclusionType.EXCEPTED_USERS, "user-uuid");

			// ---- When ---- //
			List<OuRoleAssignmentReportRow> rows = mapToRows(assignment, threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.getFirst().getTitleUuids()).isEmpty();
		}

		@Test
		@DisplayName("copies exclusions onto inherited descendant rows")
		void descendantsInheritExclusions() {
			// ---- Given ---- //
			HistoricOuAssignmentExclusion exclusion = HistoricOuAssignmentExclusion.builder()
				.exclusionType(ExclusionType.EXCEPTED_USERS)
				.uuids(List.of("user-uuid"))
				.build();
			HistoricOuAssignment assignment = base().inheritToChildren(true)
				.exclusions(new ArrayList<>(List.of(exclusion)))
				.build();

			// ---- When ---- //
			Map<String, List<OuRoleAssignmentReportRow>> rows = mapper.toReportRowsByOuUuid(
				List.of(assignment), threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.get(GRANDCHILD).getFirst().getExceptedUserUuids()).containsExactly("user-uuid");
		}
	}

	@Nested
	@DisplayName("field mapping")
	class FieldMapping {

		@Test
		@DisplayName("carries assignedBy fields through to the row")
		void carriesAssignedBy() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().assignedByName("Sagsbehandler").assignedByUserId("sb001").build();

			// ---- When ---- //
			List<OuRoleAssignmentReportRow> rows = mapToRows(assignment, threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.getFirst().getAssignedByName()).isEqualTo("Sagsbehandler");
		}

		@Test
		@DisplayName("maps appliesOnlyToManager onto the manager flag")
		void mapsManagerFlag() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().appliesOnlyToManager(true).build();

			// ---- When ---- //
			List<OuRoleAssignmentReportRow> rows = mapToRows(assignment, threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.getFirst().isManager()).isTrue();
		}

		@Test
		@DisplayName("maps appliesAlsoToSubstitutes onto the substitutes flag")
		void mapsSubstitutesFlag() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().appliesAlsoToSubstitutes(true).build();

			// ---- When ---- //
			List<OuRoleAssignmentReportRow> rows = mapToRows(assignment, threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.getFirst().isSubstitutes()).isTrue();
		}

		@Test
		@DisplayName("maps the role group name onto the report's role group column")
		void mapsRoleGroupName() {
			// ---- Given ---- //
			HistoricOuAssignment assignment = base().roleRoleGroupId(30L).roleRoleGroupName("Rollegruppe").build();

			// ---- When ---- //
			List<OuRoleAssignmentReportRow> rows = mapToRows(assignment, threeLevelTree());

			// ---- Then ---- //
			assertThat(rows.getFirst().getRoleRoleGroup()).isEqualTo("Rollegruppe");
		}
	}
}
