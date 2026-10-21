package dk.digitalidentity.rc.rolerequest.service.permission;

import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.util.Set;

final class RoleGroupItSystemScope {

	private RoleGroupItSystemScope() {
	}

	/**
	 * True when the scope covers the role group: unrestricted, or the role group has roles and all of them are in the scope.
	 * @param scope the IT system ids, or {@code null} for all IT systems
	 */
	static Predicate covers(Set<Long> scope, Expression<Long> roleGroupId, CriteriaBuilder cb, AbstractQuery<?> query) {
		if (scope == null) {
			return cb.conjunction();
		}
		if (scope.isEmpty()) {
			return cb.disjunction();
		}

		Subquery<Long> anyRole = query.subquery(Long.class);
		Root<RoleGroupUserRoleAssignment> anyAssignment = anyRole.from(RoleGroupUserRoleAssignment.class);
		anyRole.select(cb.literal(1L)).where(cb.equal(anyAssignment.get("roleGroup").get("id"), roleGroupId));

		Subquery<Long> roleOutsideScope = query.subquery(Long.class);
		Root<RoleGroupUserRoleAssignment> assignment = roleOutsideScope.from(RoleGroupUserRoleAssignment.class);
		Join<RoleGroupUserRoleAssignment, UserRole> userRole = assignment.join("userRole", JoinType.LEFT);
		roleOutsideScope.select(cb.literal(1L)).where(
			cb.equal(assignment.get("roleGroup").get("id"), roleGroupId),
			cb.or(cb.isNull(userRole.get("itSystem")), cb.not(userRole.get("itSystem").get("id").in(scope)))
		);

		return cb.and(cb.exists(anyRole), cb.not(cb.exists(roleOutsideScope)));
	}
}
