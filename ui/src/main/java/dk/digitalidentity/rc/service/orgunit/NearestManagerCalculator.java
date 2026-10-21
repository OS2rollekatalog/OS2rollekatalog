package dk.digitalidentity.rc.service.orgunit;

import dk.digitalidentity.rc.dao.model.OrgUnitNearestManager;
import dk.digitalidentity.rc.dao.projections.OrgUnitParentAndManager;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The single definition of an OU's nearest and next manager.
 * Nearest: walk from the OU up through its parents to the first OU with a manager.
 * Next: continue above that OU to the first OU whose manager is a different person.
 * Inactive OUs take part like any other OU.
 */
@Slf4j
public final class NearestManagerCalculator {

	private record FoundManager(String managerUuid, String orgUnitUuid) {}

	private final Map<String, OrgUnitParentAndManager> orgUnitsByUuid;

	private NearestManagerCalculator(Collection<OrgUnitParentAndManager> orgUnits) {
		this.orgUnitsByUuid = orgUnits.stream().collect(Collectors.toMap(OrgUnitParentAndManager::uuid, Function.identity()));
	}

	/**
	 * @return the managers per OU uuid; OUs without a manager anywhere above them are left out
	 */
	public static Map<String, OrgUnitNearestManager> calculate(Collection<OrgUnitParentAndManager> orgUnits) {
		NearestManagerCalculator calculator = new NearestManagerCalculator(orgUnits);
		return orgUnits.stream()
			.map(orgUnit -> calculator.nearestManagerOf(orgUnit.uuid()))
			.flatMap(Optional::stream)
			.collect(Collectors.toMap(OrgUnitNearestManager::getOuUuid, Function.identity()));
	}

	private Optional<OrgUnitNearestManager> nearestManagerOf(String ouUuid) {
		Set<String> visited = new HashSet<>();
		return firstManagerFrom(ouUuid, null, visited, ouUuid).map(nearest -> {
			String parentOfNearest = orgUnitsByUuid.get(nearest.orgUnitUuid()).parentUuid();
			Optional<FoundManager> next = firstManagerFrom(parentOfNearest, nearest.managerUuid(), visited, ouUuid);
			return new OrgUnitNearestManager(ouUuid, nearest.managerUuid(), nearest.orgUnitUuid(),
				next.map(FoundManager::managerUuid).orElse(null), next.map(FoundManager::orgUnitUuid).orElse(null));
		});
	}

	/**
	 * Walks up from {@code startUuid} (inclusive) to the first OU with a manager other than {@code skippedManagerUuid}.
	 */
	private Optional<FoundManager> firstManagerFrom(String startUuid, String skippedManagerUuid, Set<String> visited, String forOuUuid) {
		String currentUuid = startUuid;
		while (currentUuid != null) {
			if (!visited.add(currentUuid)) {
				log.warn("Cyclic OU hierarchy above {} at {}, stopping the manager lookup", forOuUuid, currentUuid);
				return Optional.empty();
			}
			OrgUnitParentAndManager current = orgUnitsByUuid.get(currentUuid);
			if (current == null) {
				return Optional.empty();
			}
			if (current.managerUuid() != null && !Objects.equals(current.managerUuid(), skippedManagerUuid)) {
				return Optional.of(new FoundManager(current.managerUuid(), current.uuid()));
			}
			currentUuid = current.parentUuid();
		}
		return Optional.empty();
	}
}
