package dk.digitalidentity.rc.rolerequest.service.permission;

import dk.digitalidentity.rc.dao.SpecificationIdFinder.EntityId;
import dk.digitalidentity.rc.rolerequest.dao.ItSystemPermissionViewDao;
import dk.digitalidentity.rc.rolerequest.dao.RoleGroupPermissionViewDao;
import dk.digitalidentity.rc.rolerequest.dao.UserRolePermissionViewDao;
import dk.digitalidentity.rc.rolerequest.model.entity.ItSystemPermissionView;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleGroupPermissionView;
import dk.digitalidentity.rc.rolerequest.model.entity.UserRolePermissionView;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.rolerequest.model.permission.ApproverPermission;
import dk.digitalidentity.rc.rolerequest.model.permission.RequesterPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * The permissions that apply to roles after inheritance, as resolved by the database views.
 * A resolved permission is never INHERIT. Ids that are not found resolve to NONE / nobody.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResolvedPermissionService {
	private final UserRolePermissionViewDao userRolePermissionViewDao;
	private final RoleGroupPermissionViewDao roleGroupPermissionViewDao;
	private final ItSystemPermissionViewDao itSystemPermissionViewDao;

	public RequesterPermission userRoleRequesterPermission(long userRoleId) {
		return userRolePermissionViewDao.findById(userRoleId)
			.map(UserRolePermissionView::getEffectiveRequesterPermission)
			.orElse(RequesterPermission.none());
	}

	public RequesterPermission roleGroupRequesterPermission(long roleGroupId) {
		return roleGroupPermissionViewDao.findById(roleGroupId)
			.map(RoleGroupPermissionView::getEffectiveRequesterPermission)
			.orElse(RequesterPermission.none());
	}

	public Map<Long, RequesterPermission> userRoleRequesterPermissions(Collection<Long> userRoleIds) {
		return resolve(userRoleIds, userRolePermissionViewDao::findByIdIn, UserRolePermissionView::getId, UserRolePermissionView::getEffectiveRequesterPermission, RequesterPermission.none());
	}

	public Map<Long, RequesterPermission> roleGroupRequesterPermissions(Collection<Long> roleGroupIds) {
		return resolve(roleGroupIds, roleGroupPermissionViewDao::findByIdIn, RoleGroupPermissionView::getId, RoleGroupPermissionView::getEffectiveRequesterPermission, RequesterPermission.none());
	}

	public Map<Long, RequesterPermission> itSystemRequesterPermissions(Collection<Long> itSystemIds) {
		return resolve(itSystemIds, itSystemPermissionViewDao::findByIdIn, ItSystemPermissionView::getId, ItSystemPermissionView::getEffectiveRequesterPermission, RequesterPermission.none());
	}

	public ApproverPermission userRoleApproverPermission(long userRoleId) {
		return userRolePermissionViewDao.findById(userRoleId)
			.map(UserRolePermissionView::getEffectiveApproverPermission)
			.orElse(ApproverPermission.nobody());
	}

	public ApproverPermission roleGroupApproverPermission(long roleGroupId) {
		return roleGroupPermissionViewDao.findById(roleGroupId)
			.map(RoleGroupPermissionView::getEffectiveApproverPermission)
			.orElse(ApproverPermission.nobody());
	}

	public Map<Long, ApproverPermission> userRoleApproverPermissions(Collection<Long> userRoleIds) {
		return resolve(userRoleIds, userRolePermissionViewDao::findByIdIn, UserRolePermissionView::getId, UserRolePermissionView::getEffectiveApproverPermission, ApproverPermission.nobody());
	}

	public Map<Long, ApproverPermission> roleGroupApproverPermissions(Collection<Long> roleGroupIds) {
		return resolve(roleGroupIds, roleGroupPermissionViewDao::findByIdIn, RoleGroupPermissionView::getId, RoleGroupPermissionView::getEffectiveApproverPermission, ApproverPermission.nobody());
	}

	public Map<Long, ApproverPermission> itSystemApproverPermissions(Collection<Long> itSystemIds) {
		return resolve(itSystemIds, itSystemPermissionViewDao::findByIdIn, ItSystemPermissionView::getId, ItSystemPermissionView::getEffectiveApproverPermission, ApproverPermission.nobody());
	}

	public List<Long> automaticUserRoleIds() {
		return ids(userRolePermissionViewDao.findByEffectiveApproverPermission(ApproverPermission.automatic()));
	}

	public List<Long> automaticRoleGroupIds() {
		return ids(roleGroupPermissionViewDao.findByEffectiveApproverPermission(ApproverPermission.automatic()));
	}

	public boolean anyRequestableByAuthorized() {
		return userRolePermissionViewDao.exists(requesterPermissionContains(RequestableBy.AUTHORIZED))
			|| roleGroupPermissionViewDao.exists(requesterPermissionContains(RequestableBy.AUTHORIZED));
	}

	private static <T> Specification<T> requesterPermissionContains(RequestableBy value) {
		return (root, _, cb) -> RequesterRule.contains(RequesterRule.effectivePermission(root), value, cb);
	}

	private static List<Long> ids(Set<EntityId> entityIds) {
		return entityIds.stream().map(EntityId::id).toList();
	}

	private static <V, P> Map<Long, P> resolve(Collection<Long> ids, Function<Collection<Long>, List<V>> finder, ToLongFunction<V> id, Function<V, P> permission, P missing) {
		Set<Long> distinctIds = Set.copyOf(ids);
		if (distinctIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, P> found = finder.apply(distinctIds).stream()
			.collect(Collectors.toMap(id::applyAsLong, permission));
		return distinctIds.stream().collect(Collectors.toMap(Function.identity(), key -> found.getOrDefault(key, missing)));
	}
}
