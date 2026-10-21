package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.OrgUnitRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.OrgUnitUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.UserRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.UserUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.assignment.CurrentAssignmentDao;
import dk.digitalidentity.rc.dao.assignment.CurrentExceptedAssignmentDao;
import dk.digitalidentity.rc.dao.assignment.HistoricAssignmentDao;
import dk.digitalidentity.rc.dao.assignment.HistoricExceptedAssignmentDao;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

/**
 * Fills assigned_when on rows written before the column existed.
 * <p>
 * Only the rows a report reaches today are repaired: current_* from their own source assignment, and
 * from there the open historic rows versioned off them. That is where the symptom sits - an
 * assignment untouched since the upgrade has exactly one open historic row, carrying valid_from = the
 * upgrade date. Closed historic versions are left alone and the read side keeps falling back to
 * valid_from: their source row is gone, so the date would have to be reconstructed from
 * history_role_assignments, which is only indexed on dato.
 * <p>
 * Delete this service along with its two repair jobs once the repair has run everywhere.
 */
@Service
@RequiredArgsConstructor
public class AssignedWhenRepairService {

	private final CurrentAssignmentDao currentAssignmentDao;
	private final CurrentExceptedAssignmentDao currentExceptedAssignmentDao;
	private final HistoricAssignmentDao historicAssignmentDao;
	private final HistoricExceptedAssignmentDao historicExceptedAssignmentDao;
	private final UserUserRoleAssignmentDao userUserRoleAssignmentDao;
	private final UserRoleGroupAssignmentDao userRoleGroupAssignmentDao;
	private final OrgUnitUserRoleAssignmentDao orgUnitUserRoleAssignmentDao;
	private final OrgUnitRoleGroupAssignmentDao orgUnitRoleGroupAssignmentDao;

	/**
	 * @return true when a date was found and written, false when the source is gone or already handled
	 */
	@Transactional
	public boolean repairAssignment(long id) {
		CurrentAssignment assignment = currentAssignmentDao.findById(id).orElse(null);
		if (assignment == null || assignment.getAssignedWhen() != null) {
			return false;
		}

		LocalDateTime assignedWhen = resolveFromSource(assignment);
		if (assignedWhen == null) {
			return false;
		}

		currentAssignmentDao.setAssignedWhen(id, assignedWhen);
		historicAssignmentDao.setAssignedWhenOnOpenByRecordHash(assignment.getRecordHash(), assignedWhen);
		return true;
	}

	/** @see #repairAssignment(long) */
	@Transactional
	public boolean repairException(long id) {
		CurrentExceptedAssignment exception = currentExceptedAssignmentDao.findById(id).orElse(null);
		if (exception == null || exception.getAssignedWhen() != null) {
			return false;
		}

		LocalDateTime assignedWhen = resolveFromSource(exception);
		if (assignedWhen == null) {
			return false;
		}

		currentExceptedAssignmentDao.setAssignedWhen(id, assignedWhen);
		historicExceptedAssignmentDao.setAssignedWhenOnOpen(exception.getRecordHash(),
			exception.getExceptionAssignmentId(), exception.getExceptionUserUuid(),
			exception.getExceptionUserRoleId(), assignedWhen);
		return true;
	}

	/**
	 * assignmentId is an id from one of four tables; the org unit / role group combination decides
	 * which, the same four cases {@link mapper.CurrentAssignmentMapper} writes them from.
	 */
	private LocalDateTime resolveFromSource(final CurrentAssignment assignment) {
		// reading an id off a lazy association does not initialise its proxy
		final String userUuid = assignment.getUser() != null ? assignment.getUser().getUuid() : null;
		final Long userRoleId = assignment.getUserRole() != null ? assignment.getUserRole().getId() : null;
		final Long roleGroupId = assignment.getRoleGroup() != null ? assignment.getRoleGroup().getId() : null;
		final String orgUnitUuid = assignment.getOrgUnit() != null ? assignment.getOrgUnit().getUuid() : null;
		final long assignmentId = assignment.getAssignmentId();

		if (orgUnitUuid == null && roleGroupId == null && userUuid != null && userRoleId != null) {
			return toLocalDateTime(userUserRoleAssignmentDao
				.findAssignedTimestamp(assignmentId, userUuid, userRoleId));
		}
		if (orgUnitUuid == null && roleGroupId != null && userUuid != null) {
			return toLocalDateTime(userRoleGroupAssignmentDao
				.findAssignedTimestamp(assignmentId, userUuid, roleGroupId));
		}
		if (orgUnitUuid != null && roleGroupId == null && userRoleId != null) {
			return toLocalDateTime(orgUnitUserRoleAssignmentDao
				.findAssignedTimestamp(assignmentId, orgUnitUuid, userRoleId));
		}
		if (orgUnitUuid != null && roleGroupId != null) {
			return toLocalDateTime(orgUnitRoleGroupAssignmentDao
				.findAssignedTimestamp(assignmentId, orgUnitUuid, roleGroupId));
		}
		return null;
	}

	/** An exception always originates from an org unit assignment. */
	private LocalDateTime resolveFromSource(final CurrentExceptedAssignment exception) {
		if (exception.getExceptionOuUuid() == null) {
			return null;
		}
		if (exception.getExceptionRoleGroupId() == null) {
			return toLocalDateTime(orgUnitUserRoleAssignmentDao.findAssignedTimestamp(
				exception.getExceptionAssignmentId(), exception.getExceptionOuUuid(),
				exception.getExceptionUserRoleId()));
		}
		return toLocalDateTime(orgUnitRoleGroupAssignmentDao.findAssignedTimestamp(
			exception.getExceptionAssignmentId(), exception.getExceptionOuUuid(),
			exception.getExceptionRoleGroupId()));
	}

	private static LocalDateTime toLocalDateTime(final Optional<Date> assignedTimestamp) {
		return assignedTimestamp
			.map(timestamp -> timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
			.orElse(null);
	}
}
