package dk.digitalidentity.rc.task.repair;

import dk.digitalidentity.rc.dao.assignment.CurrentAssignmentDao;
import dk.digitalidentity.rc.service.assignment.AssignedWhenRepairService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fills assigned_when on calculated assignments written before the column existed, and on the open
 * historic rows versioned off them.
 * <p>
 * Rows whose source assignment is gone keep a null date and are listed again on every pass - the read
 * side falls back to valid_from for them, as it did before the column existed.
 */
@Component
@RequiredArgsConstructor
public class AssignedWhenRepairJob implements RepairJob {

	private final CurrentAssignmentDao currentAssignmentDao;
	private final AssignedWhenRepairService assignedWhenRepairService;

	@Override
	public String id() {
		return "ASSIGNMENT_ASSIGNED_WHEN";
	}

	@Override
	public int version() {
		return 1;
	}

	@Override
	public String description() {
		return "assigned_when on calculated assignments and their open historic rows";
	}

	@Override
	public List<Long> nextChunk(long afterId, int limit) {
		return currentAssignmentDao.findIdsWithoutAssignedWhenAfter(afterId, PageRequest.of(0, limit));
	}

	@Override
	public boolean repair(long id) {
		return assignedWhenRepairService.repairAssignment(id);
	}
}
