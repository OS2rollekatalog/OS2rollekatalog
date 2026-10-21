package dk.digitalidentity.rc.task.repair;

import dk.digitalidentity.rc.dao.assignment.CurrentExceptedAssignmentDao;
import dk.digitalidentity.rc.service.assignment.AssignedWhenRepairService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The exception side of {@link AssignedWhenRepairJob}. Separate job because the runner keeps one
 * cursor per job and these rows live in their own table.
 */
@Component
@RequiredArgsConstructor
public class ExceptedAssignedWhenRepairJob implements RepairJob {

	private final CurrentExceptedAssignmentDao currentExceptedAssignmentDao;
	private final AssignedWhenRepairService assignedWhenRepairService;

	@Override
	public String id() {
		return "EXCEPTED_ASSIGNMENT_ASSIGNED_WHEN";
	}

	@Override
	public int version() {
		return 1;
	}

	@Override
	public String description() {
		return "assigned_when on calculated assignment exceptions and their open historic rows";
	}

	@Override
	public List<Long> nextChunk(long afterId, int limit) {
		return currentExceptedAssignmentDao.findIdsWithoutAssignedWhenAfter(afterId, PageRequest.of(0, limit));
	}

	@Override
	public boolean repair(long id) {
		return assignedWhenRepairService.repairException(id);
	}
}
