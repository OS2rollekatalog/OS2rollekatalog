package dk.digitalidentity.rc.service.assignment;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.dao.UserRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.UserRoleGroupAssignmentReasonDao;
import dk.digitalidentity.rc.dao.UserUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.UserUserRoleAssignmentReasonDao;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignmentReason;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignmentReason;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.service.assignment.model.AssignmentReasons;
import dk.digitalidentity.rc.service.assignment.model.AssignmentType;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssignmentReasonService {
	private final UserUserRoleAssignmentDao userUserRoleAssignmentDao;
	private final UserRoleGroupAssignmentDao userRoleGroupAssignmentDao;
	private final UserUserRoleAssignmentReasonDao userUserRoleAssignmentReasonDao;
	private final UserRoleGroupAssignmentReasonDao userRoleGroupAssignmentReasonDao;

	// persisted first so a later merge of the user can't swap in a copy without an id
	@Transactional
	public void saveFor(UserUserRoleAssignment assignment, String reason) {
		if (!StringUtils.hasText(reason)) {
			return;
		}
		userUserRoleAssignmentDao.save(assignment);
		userUserRoleAssignmentReasonDao.save(new UserUserRoleAssignmentReason(assignment, reason));
	}

	@Transactional
	public void saveFor(UserRoleGroupAssignment assignment, String reason) {
		if (!StringUtils.hasText(reason)) {
			return;
		}
		userRoleGroupAssignmentDao.save(assignment);
		userRoleGroupAssignmentReasonDao.save(new UserRoleGroupAssignmentReason(assignment, reason));
	}

	@Transactional(readOnly = true)
	public AssignmentReasons findFor(Collection<CurrentAssignment> currentAssignments) {
		Set<Long> userRoleAssignmentIds = assignmentIdsOfType(currentAssignments, AssignmentType.USER_USER_ROLE);
		Set<Long> roleGroupAssignmentIds = assignmentIdsOfType(currentAssignments, AssignmentType.USER_ROLE_GROUP);
		if (userRoleAssignmentIds.isEmpty() && roleGroupAssignmentIds.isEmpty()) {
			return AssignmentReasons.NONE;
		}

		Map<Long, String> userRoleReasons = userRoleAssignmentIds.isEmpty()
			? Map.of()
			: userUserRoleAssignmentReasonDao.findAllById(userRoleAssignmentIds).stream()
				.collect(Collectors.toMap(UserUserRoleAssignmentReason::getId, UserUserRoleAssignmentReason::getReason));
		Map<Long, String> roleGroupReasons = roleGroupAssignmentIds.isEmpty()
			? Map.of()
			: userRoleGroupAssignmentReasonDao.findAllById(roleGroupAssignmentIds).stream()
				.collect(Collectors.toMap(UserRoleGroupAssignmentReason::getId, UserRoleGroupAssignmentReason::getReason));

		return new AssignmentReasons(userRoleReasons, roleGroupReasons);
	}

	// assignmentId spans several source tables, so ids must be filtered by type
	private static Set<Long> assignmentIdsOfType(Collection<CurrentAssignment> currentAssignments, AssignmentType type) {
		return currentAssignments.stream()
			.filter(ca -> AssignmentType.from(ca) == type)
			.map(CurrentAssignment::getAssignmentId)
			.collect(Collectors.toSet());
	}
}
