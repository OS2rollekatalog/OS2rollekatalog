package dk.digitalidentity.rc.service.orgunit;

import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.OrgUnitNearestManagerDao;
import dk.digitalidentity.rc.dao.model.OrgUnitNearestManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Keeps {@code ou_nearest_manager} in line with the OU hierarchy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgUnitNearestManagerService {
	private final OrgUnitDao orgUnitDao;
	private final OrgUnitNearestManagerDao orgUnitNearestManagerDao;

	/**
	 * Recalculates every OU and writes only the rows that changed.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recompute() {
		long start = System.currentTimeMillis();
		Map<String, OrgUnitNearestManager> calculated = NearestManagerCalculator.calculate(orgUnitDao.findAllParentAndManager());
		Map<String, OrgUnitNearestManager> stored = orgUnitNearestManagerDao.findAll().stream()
			.collect(Collectors.toMap(OrgUnitNearestManager::getOuUuid, Function.identity()));

		List<OrgUnitNearestManager> added = calculated.values().stream()
			.filter(row -> !stored.containsKey(row.getOuUuid()))
			.toList();
		List<OrgUnitNearestManager> changed = calculated.values().stream()
			.filter(row -> stored.containsKey(row.getOuUuid()) && !stored.get(row.getOuUuid()).hasSameManagersAs(row))
			.toList();
		List<OrgUnitNearestManager> removed = stored.values().stream()
			.filter(row -> !calculated.containsKey(row.getOuUuid()))
			.toList();

		orgUnitNearestManagerDao.saveAll(added);
		changed.forEach(row -> stored.get(row.getOuUuid()).copyManagersFrom(row));
		orgUnitNearestManagerDao.deleteAllInBatch(removed);

		log.info("Recomputed nearest managers in {} ms: {} added, {} changed, {} removed",
			System.currentTimeMillis() - start, added.size(), changed.size(), removed.size());
	}
}
