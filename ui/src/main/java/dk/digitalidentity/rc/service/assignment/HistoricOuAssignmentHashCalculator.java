package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignment;
import dk.digitalidentity.rc.util.HashUtil;

import java.util.stream.Collectors;

public final class HistoricOuAssignmentHashCalculator {

	private HistoricOuAssignmentHashCalculator() {}

	public static String compute(HistoricOuAssignment r) {
		return HashUtil.builder()
			.add(r.getOuUuid())
			.add(r.getItSystemId())
			.add(r.getRoleId())
			.add(r.getRoleRoleGroupId())
			.add(r.getAssignedThroughType() != null ? r.getAssignedThroughType().name() : null)
			.add(r.getAssignedThroughUuid())
			.add(r.isAppliesOnlyToManager())
			.add(r.isAppliesAlsoToSubstitutes())
			.add(r.isInheritToChildren())
			.add(r.getStartDate())
			.add(r.getStopDate())
			.add(r.getExclusions().stream()
				.map(e -> e.getExclusionType().name() + ":" + e.getUuids())
				.sorted()
				.collect(Collectors.joining("|")))
			.build();
	}
}
