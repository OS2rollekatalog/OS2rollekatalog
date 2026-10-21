package dk.digitalidentity.rc.rolerequest.service.permission;

import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Authority of the business rules for requesting a role
 */
public final class RequesterRule {

	public record UserRoleColumns(
		Expression<String> effectivePermission,
		Expression<Boolean> readOnly,
		Expression<String> roleOuFilter,
		Expression<String> itSystemOuFilter,
		Expression<Long> itSystemId
	) {}

	public record RoleGroupColumns(
		Expression<String> effectivePermission,
		Expression<String> ouFilter,
		Expression<Long> roleGroupId
	) {}

	/**
	 * typed as the raw CSV column, also where the entity attribute is converted
 	 */
	public static Expression<String> effectivePermission(Path<?> root) {
		return root.<String>get("effectiveRequesterPermission");
	}

	private RequesterRule() {
	}

	public static Predicate forUserRole(UserRoleColumns columns, RequesterContext context, CriteriaBuilder cb) {
		List<Predicate> conditions = new ArrayList<>();
		conditions.add(cb.isFalse(columns.readOnly()));
		if (context.enforceOuFilter()) {
			// the filters include descendants, so any ancestor of the receiver's OU matches
			conditions.add(ouFilterAllowsAny(columns.roleOuFilter(), context.receiverOuAncestorUuids(), cb));
			conditions.add(ouFilterAllowsAny(columns.itSystemOuFilter(), context.receiverOuAncestorUuids(), cb));
		}
		conditions.add(qualifies(columns.effectivePermission(), context, cb,
			() -> itSystemInScope(columns.itSystemId(), context.authorizedItSystemIds(), cb)));
		return cb.and(conditions.toArray(Predicate[]::new));
	}

	public static Predicate forRoleGroup(RoleGroupColumns columns, RequesterContext context, CriteriaBuilder cb, AbstractQuery<?> query) {
		List<Predicate> conditions = new ArrayList<>();
		if (context.enforceOuFilter()) {
			// the filter includes descendants, so any ancestor of the receiver's OU matches
			conditions.add(ouFilterAllowsAny(columns.ouFilter(), context.receiverOuAncestorUuids(), cb));
		}
		conditions.add(qualifies(columns.effectivePermission(), context, cb,
			() -> RoleGroupItSystemScope.covers(context.authorizedItSystemIds(), columns.roleGroupId(), cb, query)));
		return cb.and(conditions.toArray(Predicate[]::new));
	}

	private static Predicate qualifies(Expression<String> effectivePermission, RequesterContext context, CriteriaBuilder cb, Supplier<Predicate> itSystemsInScope) {
		Predicate notNone = cb.notEqual(effectivePermission, RequestableBy.NONE.name());
		if (context.admin()) {
			return notNone;
		}

		List<Predicate> qualifying = new ArrayList<>();
		if (context.self()) {
			qualifying.add(contains(effectivePermission, RequestableBy.EMPLOYEE, cb));
		}
		if (context.managerOrSubstitute()) {
			qualifying.add(contains(effectivePermission, RequestableBy.MANAGERORSUBSTITUTE, cb));
		}
		if (context.authResponsible()) {
			qualifying.add(contains(effectivePermission, RequestableBy.AUTHRESPONSIBLE, cb));
		}
		if (context.authorized()) {
			qualifying.add(cb.and(contains(effectivePermission, RequestableBy.AUTHORIZED, cb), itSystemsInScope.get()));
		}
		return qualifying.isEmpty()
			? cb.disjunction()
			: cb.and(notNone, cb.or(qualifying.toArray(Predicate[]::new)));
	}

	static Predicate contains(Expression<String> permission, RequestableBy value, CriteriaBuilder cb) {
		return findInSet(cb.literal(value.name()), permission, cb);
	}

	private static Predicate ouFilterAllowsAny(Expression<String> ouFilter, Collection<String> ouUuids, CriteriaBuilder cb) {
		List<Predicate> allowed = new ArrayList<>();
		allowed.add(cb.isNull(ouFilter));
		ouUuids.forEach(uuid -> allowed.add(findInSet(cb.literal(uuid), ouFilter, cb)));
		return cb.or(allowed.toArray(Predicate[]::new));
	}

	private static Predicate findInSet(Expression<String> value, Expression<String> commaSeparated, CriteriaBuilder cb) {
		return cb.greaterThan(cb.function("FIND_IN_SET", Integer.class, value, commaSeparated), 0);
	}

	private static Predicate itSystemInScope(Expression<Long> itSystemId, Set<Long> scope, CriteriaBuilder cb) {
		if (scope == null) {
			return cb.conjunction();
		}
		return scope.isEmpty() ? cb.disjunction() : itSystemId.in(scope);
	}

}
