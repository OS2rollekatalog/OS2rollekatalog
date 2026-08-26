package dk.digitalidentity.rc.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.controller.mvc.viewmodel.OuRoleAssignmentReportRow;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion.ExclusionType;
import dk.digitalidentity.rc.service.model.AssignedThrough;

/**
 * Flattens {@code historic_ou_assignment} rows into Excel report rows, expanding assignments that
 * inherit to children into one row per org unit in the subtree.
 */
@Component
public class OuRoleAssignmentReportMapper {

	/**
	 * @param assignments    flat assignments active on the report date
	 * @param orgUnitsByUuid the full, unfiltered org unit snapshot for the report date. Org units absent
	 *                       from this map were inactive and are neither emitted nor traversed through.
	 */
	public Map<String, List<OuRoleAssignmentReportRow>> toReportRowsByOuUuid(
		final List<HistoricOuAssignment> assignments,
		final Map<String, HistoryOU> orgUnitsByUuid
	) {
		final Map<String, List<String>> childrenByParent = indexChildrenByParent(orgUnitsByUuid);

		return assignments.stream()
			.flatMap(assignment -> expand(assignment, orgUnitsByUuid, childrenByParent).stream())
			.collect(Collectors.groupingBy(OuRoleAssignmentReportRow::getOuUuid));
	}

	private static Map<String, List<String>> indexChildrenByParent(final Map<String, HistoryOU> orgUnitsByUuid) {
		final Map<String, List<String>> childrenByParent = new HashMap<>();
		for (final HistoryOU orgUnit : orgUnitsByUuid.values()) {
			final String parentUuid = orgUnit.getOuParentUuid();
			if (parentUuid != null && !parentUuid.equals(orgUnit.getOuUuid())) {
				childrenByParent.computeIfAbsent(parentUuid, key -> new ArrayList<>()).add(orgUnit.getOuUuid());
			}
		}
		return childrenByParent;
	}

	private List<OuRoleAssignmentReportRow> expand(
		final HistoricOuAssignment assignment,
		final Map<String, HistoryOU> orgUnitsByUuid,
		final Map<String, List<String>> childrenByParent
	) {
		final String originUuid = assignment.getOuUuid();
		if (!orgUnitsByUuid.containsKey(originUuid)) {
			return List.of();
		}

		if (!assignment.isInheritToChildren()) {
			return List.of(toRow(
				assignment, originUuid, assignment.getAssignedThroughType(), assignment.getAssignedThroughUuid(),
				assignment.getAssignedThroughName(), false
			));
		}

		final HistoryOU originOrgUnit = orgUnitsByUuid.get(originUuid);
		final List<OuRoleAssignmentReportRow> rows = new ArrayList<>();

		// The origin row carries the inherit marker; descendants record where they inherited from.
		rows.add(toRow(
			assignment, originUuid, AssignedThrough.ORGUNIT, originUuid, originOrgUnit.getOuName(),
			true
		));

		for (final String descendantUuid : collectDescendants(originUuid, childrenByParent, orgUnitsByUuid)) {
			rows.add(toRow(
				assignment, descendantUuid, AssignedThrough.ORGUNIT, originUuid, originOrgUnit.getOuName(),
				false
			));
		}
		return rows;
	}

	private static List<String> collectDescendants(
		final String originUuid,
		final Map<String, List<String>> childrenByParent,
		final Map<String, HistoryOU> orgUnitsByUuid
	) {
		final List<String> descendants = new ArrayList<>();
		final Set<String> visited = new HashSet<>();
		visited.add(originUuid);

		final Deque<String> pending = new ArrayDeque<>();
		pending.push(originUuid);
		while (!pending.isEmpty()) {
			for (final String childUuid : childrenByParent.getOrDefault(pending.pop(), List.of())) {
				// An inactive org unit is absent from the snapshot, and blocks traversal of its own subtree.
				if (orgUnitsByUuid.containsKey(childUuid) && visited.add(childUuid)) {
					descendants.add(childUuid);
					pending.push(childUuid);
				}
			}
		}
		return descendants;
	}

	private static OuRoleAssignmentReportRow toRow(
		final HistoricOuAssignment assignment,
		final String ouUuid,
		final AssignedThrough assignedThroughType,
		final String assignedThroughUuid,
		final String assignedThroughName,
		final boolean inherit
	) {
		return OuRoleAssignmentReportRow.builder()
			.ouUuid(ouUuid)
			.roleId(assignment.getRoleId() != null ? assignment.getRoleId() : 0L)
			.roleName(assignment.getRoleName())
			.itSystemId(assignment.getItSystemId())
			.itSystemName(assignment.getItSystemName())
			.roleRoleGroup(assignment.getRoleRoleGroupName())
			.roleRoleGroupId(assignment.getRoleRoleGroupId())
			.assignedByUserId(assignment.getAssignedByUserId())
			.assignedByName(assignment.getAssignedByName())
			.assignedWhen(assignment.getAssignedWhen())
			.startDate(assignment.getStartDate())
			.stopDate(assignment.getStopDate())
			.assignedThroughType(assignedThroughType)
			.assignedThroughUuid(assignedThroughUuid)
			.assignedThroughName(assignedThroughName)
			.manager(assignment.isAppliesOnlyToManager())
			.substitutes(assignment.isAppliesAlsoToSubstitutes())
			.inherit(inherit)
			.exceptedUserUuids(uuidsOfType(assignment, ExclusionType.EXCEPTED_USERS))
			.titleUuids(titleUuids(assignment))
			.functionUuids(uuidsOfType(assignment, ExclusionType.FUNCTIONS))
			.build();
	}

	/** Positive and negative titles share the report's single "titles" column. */
	private static List<String> titleUuids(final HistoricOuAssignment assignment) {
		final List<String> uuids = new ArrayList<>(uuidsOfType(assignment, ExclusionType.POSITIVE_TITLES));
		uuids.addAll(uuidsOfType(assignment, ExclusionType.NEGATIVE_TITLES));
		return uuids;
	}

	private static List<String> uuidsOfType(final HistoricOuAssignment assignment, final ExclusionType type) {
		if (assignment.getExclusions() == null) {
			return List.of();
		}
		return assignment.getExclusions()
			.stream()
			.filter(exclusion -> exclusion.getExclusionType() == type)
			.map(HistoricOuAssignmentExclusion::getUuids)
			.filter(Objects::nonNull)
			.flatMap(List::stream)
			.toList();
	}
}
