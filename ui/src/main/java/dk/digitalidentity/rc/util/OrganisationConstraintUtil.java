package dk.digitalidentity.rc.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.service.OrgUnitService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrganisationConstraintUtil {
	private final OrgUnitService orgUnitService;

	// Takes the entire + and - pre-fixed string and parses it into a list of uuids the given user has access to
	public List<String> getOrganisationConstraintUuids(String constraintValue) {
		if (constraintValue == null || constraintValue.trim().isEmpty()) {
			return new ArrayList<>();
		}

		if (!containsInclusionsOrExclusions(constraintValue)) {
			return Arrays.stream(constraintValue.split(",")).map(String::trim).filter(uuid -> !uuid.isEmpty())
					.collect(Collectors.toList());
		}

		// Handle inheritance format with + and - prefixes
		return getConstraintAllowedUuids(constraintValue);
	}

	private boolean containsInclusionsOrExclusions(final String constraintValue) {
		String[] parts = StringUtils.split(constraintValue, ",");
		if (parts == null || parts.length == 0) {
			return false;
		}
		return Stream.of(parts)
			.map(String::trim)
			.anyMatch(s -> s.startsWith("+") || s.startsWith("-"));
	}

	private List<String> getConstraintAllowedUuids(String constraintValue) {
		if (constraintValue == null || constraintValue.trim().isEmpty()) {
			return new ArrayList<>();
		}

		// Parse the constraint value into parent-exclusions map
		Map<String, Set<String>> parentsAndExclusionMap = parseConstraintToParentExclusionsMap(constraintValue);

		// Get all parent OrgUnits
		List<OrgUnit> parents = orgUnitService.getByUuidIn(parentsAndExclusionMap.keySet().stream().toList());

		// Get all children from all parents
		List<OrgUnit> allChildrenFromParents = getAllChildrenFromParents(parents);

		// Build the final allowed UUIDs set
		Set<String> allowedUuids = new HashSet<>();

		// Add all parent UUIDs
		for (OrgUnit parent : parents) {
			allowedUuids.add(parent.getUuid());
		}

		// Add all children UUIDs
		for (OrgUnit child : allChildrenFromParents) {
			allowedUuids.add(child.getUuid());
		}

		// Remove excluded UUIDs for each parent
		for (Map.Entry<String, Set<String>> entry : parentsAndExclusionMap.entrySet()) {
			Set<String> exclusions = entry.getValue();

			if (!exclusions.isEmpty()) {
				allowedUuids.removeAll(exclusions);
			}
		}

		return new ArrayList<>(allowedUuids);
	}

	private static List<OrgUnit> getAllChildrenFromParents(List<OrgUnit> parents) {
		Set<OrgUnit> allChildren = new HashSet<>();

		for (OrgUnit parent : parents) {
			getAllChildrenRecursive(parent, allChildren);
		}

		return new ArrayList<>(allChildren);
	}

	private static void getAllChildrenRecursive(OrgUnit orgUnit, Set<OrgUnit> allChildren) {
		if (orgUnit.getChildren() != null) {
			for (OrgUnit child : orgUnit.getChildren()) {
				// Add the child to our collection
				allChildren.add(child);

				// Recursively get all children of this child
				getAllChildrenRecursive(child, allChildren);
			}
		}
	}

	private static Map<String, Set<String>> parseConstraintToParentExclusionsMap(String constraintValue) {
		if (constraintValue == null || constraintValue.trim().isEmpty()) {
			return new HashMap<>();
		}

		Map<String, Set<String>> parentExclusionsMap = new HashMap<>();
		Set<String> currentExclusions = new HashSet<>();
		String currentParent = null;

		String[] constraints = constraintValue.split(",");

		for (String constraint : constraints) {
			constraint = constraint.trim();

			if (constraint.startsWith("+")) {
				if (currentParent != null) {
					parentExclusionsMap.put(currentParent, new HashSet<>(currentExclusions));
				}

				currentParent = constraint.substring(1);
				currentExclusions = new HashSet<>();
			}
			else if (constraint.startsWith("-")) {
				if (currentParent != null) {
					currentExclusions.add(constraint.substring(1));
				}
			}
		}

		if (currentParent != null) {
			parentExclusionsMap.put(currentParent, currentExclusions);
		}

		return parentExclusionsMap;
	}
}
