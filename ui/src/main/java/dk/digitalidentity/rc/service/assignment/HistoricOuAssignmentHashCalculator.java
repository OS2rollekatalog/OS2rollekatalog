package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion.ExclusionType;
import dk.digitalidentity.rc.util.HashUtil;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class HistoricOuAssignmentHashCalculator {

	private static final int CURRENT_VERSION = Arrays.stream(ExclusionType.values())
		.mapToInt(ExclusionType::getSince)
		.max()
		.orElse(1);

	private HistoricOuAssignmentHashCalculator() {}

	public static String compute(HistoricOuAssignment r) {
		return compute(r, CURRENT_VERSION);
	}

	/** The hash the row had before any exclusion type newer than {@code version} existed. */
	public static String compute(HistoricOuAssignment r, int version) {
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
			// both the entries and the uuids inside each are sorted: the exclusions come from unordered
			// JPA bags, so without this the same assignment hashes differently depending on the order
			// the join happened to return, and close-by-hash silently matches nothing
			.add(r.getExclusions().stream()
				.filter(e -> e.getExclusionType().getSince() <= version)
				.map(e -> e.getExclusionType().name() + ":" + sortedUuids(e))
				.sorted()
				.collect(Collectors.joining("|")))
			.build();
	}

	/** Hashes under every earlier identity version, newest first, without the current hash. */
	public static List<String> olderHashes(HistoricOuAssignment r) {
		String current = compute(r);
		return IntStream.iterate(CURRENT_VERSION - 1, v -> v >= 1, v -> v - 1)
			.mapToObj(v -> compute(r, v))
			.filter(hash -> !hash.equals(current))
			.distinct()
			.toList();
	}

	private static List<String> sortedUuids(HistoricOuAssignmentExclusion exclusion) {
		return exclusion.getUuids() == null ? List.of() : exclusion.getUuids().stream().sorted().toList();
	}
}
