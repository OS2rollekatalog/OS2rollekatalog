package dk.digitalidentity.rc.rolerequest.service.permission;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.dao.RoleRequestDao;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.security.SecurityUtil;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Answers "may this approver approve these requests" by applying {@link ApproverRule} in the database.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovableRequestService {
	private final ApproverContextFactory contextFactory;
	private final RoleRequestDao roleRequestDao;
	private final SecurityUtil securityUtil;

	public boolean canApprove(RoleRequest request, User approver) {
		return request.getId() != null && approvableAmong(approver, List.of(request.getId())).contains(request.getId());
	}

	/**
	 * The requests (from the given ones) that the approver may approve.
	 */
	public Set<Long> approvableAmong(User approver, Collection<Long> requestIds) {
		if (approver == null || requestIds.isEmpty()) {
			return Set.of();
		}
		return approvableAmong(contextFactory.of(approver), requestIds);
	}

	/**
	 * Like {@link #approvableAmong}, but without admin shortcuts
	 */
	public Set<Long> emailRecipientAmong(User approver, Collection<Long> requestIds) {
		if (approver == null || requestIds.isEmpty()) {
			return Set.of();
		}
		return approvableAmong(contextFactory.of(approver).withoutAdminBypass(), requestIds);
	}


	private Set<Long> approvableAmong(ApproverContext context, Collection<Long> requestIds) {
		return roleRequestDao.findIds(approvable(context).and((root, _, _) -> root.get("id").in(requestIds)));
	}

	/**
	 * Newest first.
	 */
	public List<RoleRequest> pendingApprovableRequests(User approver) {
		Set<Long> ids = roleRequestDao.findIds(pending(contextFactory.of(approver)));
		if (ids.isEmpty()) {
			return List.of();
		}
		return roleRequestDao.findByIdInEager(ids).stream().distinct().toList();
	}

	public long countPendingApprovable(User approver) {
		if (!approver.isDeleted() && securityUtil.isAdmin(approver)) {
			return roleRequestDao.count(pendingStatus());
		}
		if (!roleRequestDao.exists(pendingStatus())) {
			return 0;
		}
		return roleRequestDao.count(pending(contextFactory.of(approver)).and(distinctRows()));
	}

	public Specification<RoleRequest> approvable(ApproverContext context) {
		return (root, query, cb) -> ApproverRule.forRequest(root, context, cb, query);
	}

	private Specification<RoleRequest> pending(ApproverContext context) {
		return approvable(context).and(pendingStatus());
	}

	private static Specification<RoleRequest> pendingStatus() {
		return (root, _, cb) -> {
			Join<RoleRequest, UserRole> userRole = root.join("userRole", JoinType.LEFT);
			Join<RoleRequest, RoleGroup> roleGroup = root.join("roleGroup", JoinType.LEFT);
			return cb.and(
				cb.equal(root.get("status"), RequestApproveStatus.REQUESTED),
				cb.or(cb.isNotNull(roleGroup.get("id")), cb.isFalse(userRole.get("readOnly")))
			);
		};
	}

	// guards the count against a future to-many join in ApproverRule
	private static Specification<RoleRequest> distinctRows() {
		return (_, query, cb) -> {
			query.distinct(true);
			return cb.conjunction();
		};
	}
}
