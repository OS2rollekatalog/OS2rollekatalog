package dk.digitalidentity.rc.task.repair;

import dk.digitalidentity.rc.dao.assignment.HistoricOuAssignmentDao;
import dk.digitalidentity.rc.service.assignment.HistoricOuAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Restores record_hash on open historic org unit assignments.
 * <p>
 * The hash decides which row a removal or an edit closes, so a row whose hash was written under an
 * older formula can never be closed again: it stays open forever and an edit adds a second open row
 * beside it. Raise {@link #version()} whenever
 * {@link dk.digitalidentity.rc.service.assignment.HistoricOuAssignmentHashCalculator} changes.
 * <p>
 * Closed rows are immutable past intervals whose hashes are never looked up again, so they are left
 * alone.
 */
@Component
@RequiredArgsConstructor
public class HistoricOuAssignmentHashRepairJob implements RepairJob {

	private final HistoricOuAssignmentDao historicOuAssignmentDao;
	private final HistoricOuAssignmentService historicOuAssignmentService;

	@Override
	public String id() {
		return "HISTORIC_OU_ASSIGNMENT_HASH";
	}

	@Override
	public int version() {
		return 1;
	}

	@Override
	public String description() {
		return "record_hash on open historic org unit assignments";
	}

	@Override
	public List<Long> nextChunk(long afterId, int limit) {
		return historicOuAssignmentDao.findOpenIdsAfter(afterId, PageRequest.of(0, limit));
	}

	@Override
	public boolean repair(long id) {
		return historicOuAssignmentService.repairRecordHash(id);
	}
}
