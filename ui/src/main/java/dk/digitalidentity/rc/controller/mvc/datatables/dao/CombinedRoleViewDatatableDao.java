package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.CombinedRoleView;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.CombinedRoleViewId;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.datatables.repository.DataTablesRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public interface CombinedRoleViewDatatableDao extends DataTablesRepository<CombinedRoleView, CombinedRoleViewId> {

	static Specification<CombinedRoleView> isNotReadOnly() {
		return (root, _, cb) -> cb.equal(root.get("readOnly"), false);
	}

	static Specification<CombinedRoleView> orgUnitFilterMatchesOrEmpty(List<String> ancestorOuUuids) {
		return (root, _, cb) -> {
			// Check role filter: no filter OR filter matches
			Predicate noRoleFilter = cb.isNull(root.get("orgUnitFilterUuids"));

			List<Predicate> roleFilterMatches = ancestorOuUuids.stream()
				.map(uuid -> cb.like(
					cb.concat(cb.concat(",", root.get("orgUnitFilterUuids")), ","),
					"%," + uuid + ",%"
				))
				.collect(Collectors.toList());

			Predicate roleHasMatchingFilter = roleFilterMatches.isEmpty()
				? cb.disjunction()
				: cb.or(roleFilterMatches.toArray(new Predicate[0]));

			Predicate roleFilterOk = cb.or(noRoleFilter, roleHasMatchingFilter);

			// Check itSystem filter: no filter OR filter matches
			Predicate noItSystemFilter = cb.isNull(root.get("itSystemOrgUnitFilterUuids"));

			List<Predicate> itSystemFilterMatches = ancestorOuUuids.stream()
				.map(uuid -> cb.like(
					cb.concat(cb.concat(",", root.get("itSystemOrgUnitFilterUuids")), ","),
					"%," + uuid + ",%"
				))
				.collect(Collectors.toList());

			Predicate itSystemHasMatchingFilter = itSystemFilterMatches.isEmpty()
				? cb.disjunction()
				: cb.or(itSystemFilterMatches.toArray(new Predicate[0]));

			Predicate itSystemFilterOk = cb.or(noItSystemFilter, itSystemHasMatchingFilter);

			// Both must be satisfied: roleFilterOk AND itSystemFilterOk
			return cb.and(roleFilterOk, itSystemFilterOk);
		};
	}

	static Specification<CombinedRoleView> requesterPermissionIn(Collection<RequestableBy> permittedSettings) {
		return (root, _, cb) -> {
			Predicate[] predicates = permittedSettings.stream()
				.map(setting -> cb.like(
					cb.concat(cb.concat(",", root.get("effectiveRequesterPermission")), ","),
					"%," + setting.toString() + ",%"
				))
				.toArray(Predicate[]::new);
			return cb.or(predicates);
		};
	}

	static Specification<CombinedRoleView> requesterPermissionContains(RequestableBy permission) {
		return requesterPermissionIn(List.of(permission));
	}

	/**
	 * Filter AUTHORIZED rows by receiver's OrgUnit matching the set the requester is authorized for.
	 * Non-AUTHORIZED rows pass through unaffected.
	 * Mirrors {@link UserRoleViewDatatableDao#authorizedRolesLimitedToOrgUnits(String, Set)}.
	 *
	 * @param receiverOuUuid the UUID of the receiving user's current OrgUnit (must not be null)
	 * @param accessibleOrgUnitUuids the set of OrgUnit UUIDs the requester is authorized for
	 * @return a specification that blocks AUTHORIZED rows when the receiver's OU is not in the authorized set
	 */
	static Specification<CombinedRoleView> authorizedRolesLimitedToOrgUnits(String receiverOuUuid, Set<String> accessibleOrgUnitUuids) {
		if (receiverOuUuid == null) {
			throw new IllegalArgumentException("receiverOuUuid cannot be null");
		}
		return (root, query, cb) -> {
			if (accessibleOrgUnitUuids == null || !accessibleOrgUnitUuids.contains(receiverOuUuid)) {
				return cb.not(requesterPermissionContains(RequestableBy.AUTHORIZED).toPredicate(root, query, cb));
			}
			return cb.conjunction();
		};
	}

	/**
	 * Filter AUTHORIZED rows to only those backed by an accessible IT system.
	 * <ul>
	 *   <li>For {@code type='userRole'}: the row's {@code itSystemId} must be in the accessible set.</li>
	 *   <li>For {@code type='roleGroup'}: every user_role inside the role group must have an
	 *   {@code it_system_id} in the accessible set AND the role group must contain at least one
	 *   user_role (matches existing behaviour in
	 *   {@code RequestService#getRequestableRoleGroupsAsDatatable}).</li>
	 * </ul>
	 * Non-AUTHORIZED rows pass through unaffected.
	 *
	 * @param accessibleItSystemIds the set of IT system ids the requester is authorized for; if null or
	 *                              empty, every AUTHORIZED row is rejected
	 * @return a specification restricting AUTHORIZED rows to those with accessible IT systems
	 */
	static Specification<CombinedRoleView> authorizedRolesLimitedToItSystems(Set<Long> accessibleItSystemIds) {
		return (root, query, cb) -> {
			Predicate notAuthorized = cb.not(requesterPermissionContains(RequestableBy.AUTHORIZED).toPredicate(root, query, cb));

			if (accessibleItSystemIds == null || accessibleItSystemIds.isEmpty()) {
				// No accessible IT systems → reject every AUTHORIZED row
				return notAuthorized;
			}

			// userRole branch: direct comparison on the view's itSystemId column
			Predicate userRoleAccessible = cb.and(
				cb.equal(root.get("type"), "userRole"),
				root.get("itSystemId").in(accessibleItSystemIds)
			);

			// roleGroup branch: subquery — role group must have at least one user_role and
			// every user_role inside must be in an accessible it_system.
			Predicate roleGroupAccessible = buildRoleGroupAccessibilityPredicate(root, query, cb, accessibleItSystemIds);

			Predicate isAuthorizedAndAccessible = cb.and(
				requesterPermissionContains(RequestableBy.AUTHORIZED).toPredicate(root, query, cb),
				cb.or(userRoleAccessible, roleGroupAccessible)
			);

			return cb.or(notAuthorized, isAuthorizedAndAccessible);
		};
	}

	/**
	 * Builds the predicate that decides whether a {@code type='roleGroup'} row is accessible under the
	 * current IT-system authorization: the role group must (a) be non-empty and (b) contain only
	 * user_roles whose {@code it_system_id} is in {@code accessibleItSystemIds}.
	 */
	private static Predicate buildRoleGroupAccessibilityPredicate(
		Root<CombinedRoleView> root,
		AbstractQuery<?> query,
		CriteriaBuilder cb,
		Set<Long> accessibleItSystemIds) {

		// EXISTS: role group has at least one user_role (inner join ensures non-empty)
		Subquery<Long> existsAny = query.subquery(Long.class);
		Root<RoleGroup> rgRoot1 = existsAny.from(RoleGroup.class);
		rgRoot1.join("userRoleAssignments");
		existsAny.select(cb.literal(1L))
			.where(cb.equal(rgRoot1.get("id"), root.get("id")));

		// EXISTS: role group has any user_role outside the accessible IT systems.
		// LEFT join on userRole so that an assignment with a null userRole still produces a row
		// (isNull(itSystem) then evaluates to true and the role group is rejected, mirroring
		// the allMatch(userRole != null && itSystem != null && ...) in the non-combined path).
		Subquery<Long> existsOutside = query.subquery(Long.class);
		Root<RoleGroup> rgRoot2 = existsOutside.from(RoleGroup.class);
		Join<RoleGroup, RoleGroupUserRoleAssignment> assignJoin2 = rgRoot2.join("userRoleAssignments");
		Join<RoleGroupUserRoleAssignment, UserRole> urJoin = assignJoin2.join("userRole", JoinType.LEFT);
		existsOutside.select(cb.literal(1L))
			.where(cb.and(
				cb.equal(rgRoot2.get("id"), root.get("id")),
				cb.or(
					cb.isNull(urJoin.get("itSystem")),
					cb.not(urJoin.get("itSystem").get("id").in(accessibleItSystemIds))
				)
			));

		return cb.and(
			cb.equal(root.get("type"), "roleGroup"),
			cb.exists(existsAny),
			cb.not(cb.exists(existsOutside))
		);
	}

	static Specification<CombinedRoleView> excludeUserRolesById(Set<Long> userRoleIds) {
		return (root, _, cb) -> {
			if (userRoleIds == null || userRoleIds.isEmpty()) {
				return cb.conjunction();
			}

			return cb.or(
				cb.notEqual(root.get("type"), "userRole"),
				cb.not(root.get("id").in(userRoleIds))
			);
		};
	}

	static Specification<CombinedRoleView> excludeAlreadyAssigned(List<Long> assignedUserRoleIds, List<Long> assignedRoleGroupIds) {
		return (root, _, cb) -> {
			Predicate notUserRole = cb.not(
				cb.and(
					cb.equal(root.get("type"), "userRole"),
					root.get("id").in(assignedUserRoleIds.isEmpty() ? java.util.Collections.singletonList(-1L) : assignedUserRoleIds)
				)
			);

			Predicate notRoleGroup = cb.not(
				cb.and(
					cb.equal(root.get("type"), "roleGroup"),
					root.get("id").in(assignedRoleGroupIds.isEmpty() ? java.util.Collections.singletonList(-1L) : assignedRoleGroupIds)
				)
			);

			return cb.and(notUserRole, notRoleGroup);
		};
	}
}
