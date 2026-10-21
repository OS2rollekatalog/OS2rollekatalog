package dk.digitalidentity.rc.bootstrap;

import dk.digitalidentity.rc.dao.OrgUnitRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.OrgUnitUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.assignment.HistoricOuAssignmentDao;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion.ExclusionType;
import dk.digitalidentity.rc.service.assignment.HistoricOuAssignmentHashCalculator;
import dk.digitalidentity.rc.service.assignment.HistoricOuAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * One-time data migration, run by {@link DataBootstrap}: adds excepted org units to open historic org
 * unit assignments saved before they were recorded, and updates their hash.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExceptedOusHistoricBackfill {

	private final OrgUnitUserRoleAssignmentDao orgUnitUserRoleAssignmentDao;
	private final OrgUnitRoleGroupAssignmentDao orgUnitRoleGroupAssignmentDao;
	private final HistoricOuAssignmentDao historicOuAssignmentDao;
	private final HistoricOuAssignmentService historicOuAssignmentService;

	@Transactional
	public void run() {
		Stream<HistoricOuAssignment> fromUserRoles = orgUnitUserRoleAssignmentDao.findByContainsExceptedOusTrue().stream()
			.filter(a -> a.getUserRole() != null && a.getUserRole().getItSystem() != null)
			.map(historicOuAssignmentService::toHistoricRecord);
		Stream<HistoricOuAssignment> fromRoleGroups = orgUnitRoleGroupAssignmentDao.findByContainsExceptedOusTrue().stream()
			.filter(a -> a.getRoleGroup() != null)
			.flatMap(a -> historicOuAssignmentService.toHistoricRecords(a).stream());

		long updated = Stream.concat(fromUserRoles, fromRoleGroups)
			.filter(this::addExceptedOusToOpenRow)
			.count();
		log.info("Added excepted org units to {} open historic org unit assignments", updated);
	}

	private boolean addExceptedOusToOpenRow(HistoricOuAssignment expected) {
		// already up to date, e.g. edited after the upgrade
		if (historicOuAssignmentDao.existsByRecordHashAndValidToIsNull(expected.getRecordHash())) {
			return false;
		}

		// the row was saved without excepted org units, so it still has the hash from without them
		Optional<HistoricOuAssignment> row = HistoricOuAssignmentHashCalculator.olderHashes(expected).stream()
			.map(historicOuAssignmentDao::findFirstWithExclusionsByRecordHashAndValidToIsNullOrderByIdAsc)
			.flatMap(Optional::stream)
			.findFirst();
		if (row.isEmpty()) {
			return false;
		}

		expected.getExclusions().stream()
			.filter(e -> e.getExclusionType() == ExclusionType.EXCEPTED_OUS)
			.map(e -> HistoricOuAssignmentExclusion.builder()
				.exclusionType(ExclusionType.EXCEPTED_OUS)
				.uuids(e.getUuids())
				.historicOuAssignment(row.get())
				.build())
			.forEach(row.get().getExclusions()::add);

		historicOuAssignmentService.stampOrCloseAsDuplicate(row.get(), expected.getRecordHash());
		return true;
	}
}
