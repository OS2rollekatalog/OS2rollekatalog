package dk.digitalidentity.rc.rolerequest.service.permission;

import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitNearestManager;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.service.permission.ApproverContext.AuthorizedScope;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The authority on approval business rules
 */
public final class ApproverRule {

	private ApproverRule() {
	}

	/**
	 * Builds a predicate that filters requests that a given person may approve
	 */
	public static Predicate forRequest(Root<RoleRequest> request, ApproverContext context, CriteriaBuilder cb, AbstractQuery<?> query) {
		if (context.deleted()) {
			return cb.disjunction();
		}
		if (context.admin()) {
			return cb.conjunction();
		}

		Join<RoleRequest, User> receiver = request.join("receiver", JoinType.LEFT);
		Join<RoleRequest, User> requester = request.join("requester", JoinType.LEFT);
		Join<RoleRequest, UserRole> userRole = request.join("userRole", JoinType.LEFT);
		Join<RoleRequest, RoleGroup> roleGroup = request.join("roleGroup", JoinType.LEFT);
		Join<RoleRequest, OrgUnit> orgUnit = request.join("orgUnit", JoinType.LEFT);
		Expression<String> options = request.get("approverOptionValue");

		List<Predicate> conditions = new ArrayList<>();

		// receivers cannot approve themselves
		conditions.add(isNot(receiver, context.approverUuid(), cb));

		// requesters can only approve their own requests with the right setting
		if (!context.allowSelfApproval()) {
			conditions.add(isNot(requester, context.approverUuid(), cb));
		}

		// must contain a role (userrole or rolegroup)
		conditions.add(cb.or(cb.isNotNull(userRole.get("id")), cb.isNotNull(roleGroup.get("id"))));

		// must conform to one of the below
		conditions.add(cb.or(
			// automatic approval is always valid
			contains(options, ApprovableBy.AUTOMATIC, cb),
			// system responsible approval & person is system responsible for role's system(s)
			cb.and(contains(options, ApprovableBy.SYSTEMRESPONSIBLE, cb),
				itSystemsIn(userRole, roleGroup, context.systemResponsibleItSystemIds(), cb, query)),
			// authorized approval & one scope that covers both orgunit and it system
			cb.and(contains(options, ApprovableBy.AUTHORIZED, cb),
				authorizedScopesCover(userRole, roleGroup, orgUnit, context.authorizedScopes(), cb, query)),
			// approve authorized approval & one scope that covers both orgunit and it system
			cb.and(contains(options, ApprovableBy.APPROVEAUTHORIZED, cb),
				authorizedScopesCover(userRole, roleGroup, orgUnit, context.approveAuthorizedScopes(), cb, query)),
			// manager approval & person is manager for ou
			cb.and(contains(options, ApprovableBy.MANAGERORSUBSTITUTE, cb),
				approvingManagerIs(orgUnit, receiver, context.approverUuid(), cb, query)),
			// authresponsible approval & within authresponsibles ou
			cb.and(contains(options, ApprovableBy.AUTHRESPONSIBLE, cb),
				receiverPositionIn(receiver, context.authorizationManagerOuUuids(), cb, query))
		));
		return cb.and(conditions.toArray(Predicate[]::new));
	}

	private static Predicate isNot(Join<RoleRequest, User> user, String uuid, CriteriaBuilder cb) {
		return cb.or(cb.isNull(user.get("uuid")), cb.notEqual(user.get("uuid"), uuid));
	}

	private static Predicate contains(Expression<String> options, ApprovableBy value, CriteriaBuilder cb) {
		return cb.greaterThan(cb.function("FIND_IN_SET", Integer.class, cb.literal(value.name()), options), 0);
	}

	/**
	 * @param scope the IT system ids, or {@code null} for all IT systems
	 */
	private static Predicate itSystemsIn(Join<RoleRequest, UserRole> userRole, Join<RoleRequest, RoleGroup> roleGroup, Set<Long> scope, CriteriaBuilder cb, AbstractQuery<?> query) {
		Predicate userRoleInScope = scope == null
			? cb.conjunction()
			: scope.isEmpty() ? cb.disjunction() : userRole.get("itSystem").get("id").in(scope);
		return cb.or(
			cb.and(cb.isNotNull(userRole.get("id")), userRoleInScope),
			cb.and(cb.isNotNull(roleGroup.get("id")), RoleGroupItSystemScope.covers(scope, roleGroup.get("id"), cb, query))
		);
	}

	private static Predicate authorizedScopesCover(Join<RoleRequest, UserRole> userRole, Join<RoleRequest, RoleGroup> roleGroup, Join<RoleRequest, OrgUnit> orgUnit, List<AuthorizedScope> scopes, CriteriaBuilder cb, AbstractQuery<?> query) {
		if (scopes.isEmpty()) {
			return cb.disjunction();
		}
		return cb.or(scopes.stream()
			.map(scope -> cb.and(orgUnitIn(orgUnit, scope.ouUuids(), cb), itSystemsIn(userRole, roleGroup, scope.itSystemIds(), cb, query)))
			.toArray(Predicate[]::new));
	}

	/**
	 * @param scope the OU uuids, or {@code null} for all OUs
	 */
	private static Predicate orgUnitIn(Join<RoleRequest, OrgUnit> orgUnit, Set<String> scope, CriteriaBuilder cb) {
		if (scope == null) {
			return cb.conjunction();
		}
		return scope.isEmpty() ? cb.disjunction() : orgUnit.get("uuid").in(scope);
	}

	/**
	 * The approver is the manager who approves for the receiver in the request's OU, or that manager's substitute
	 * in the OU where the manager was found. Makes the same choice as {@link OrgUnitNearestManager#approvingManagerFor}.
	 */
	private static Predicate approvingManagerIs(Join<RoleRequest, OrgUnit> orgUnit, Join<RoleRequest, User> receiver, String approverUuid, CriteriaBuilder cb, AbstractQuery<?> query) {
		Subquery<Long> match = query.subquery(Long.class);
		Root<OrgUnitNearestManager> managers = match.from(OrgUnitNearestManager.class);
		Predicate receiverIsNearestManager = cb.equal(managers.get("nearestManagerUuid"), receiver.get("uuid"));
		Expression<String> managerUuid = cb.<String>selectCase()
			.when(receiverIsNearestManager, managers.get("nextManagerUuid"))
			.otherwise(managers.get("nearestManagerUuid"));
		Expression<String> managerOuUuid = cb.<String>selectCase()
			.when(receiverIsNearestManager, managers.get("nextManagerOuUuid"))
			.otherwise(managers.get("nearestManagerOuUuid"));

		Subquery<Long> substitute = query.subquery(Long.class);
		Root<ManagerSubstitute> substituteRoot = substitute.from(ManagerSubstitute.class);
		substitute.select(cb.literal(1L)).where(
			cb.equal(substituteRoot.get("substitute").get("uuid"), approverUuid),
			cb.equal(substituteRoot.get("manager").get("uuid"), managerUuid),
			cb.equal(substituteRoot.get("orgUnit").get("uuid"), managerOuUuid)
		);

		match.select(cb.literal(1L)).where(
			cb.equal(managers.get("ouUuid"), orgUnit.get("uuid")),
			cb.or(cb.equal(managerUuid, approverUuid), cb.exists(substitute))
		);
		return cb.exists(match);
	}

	private static Predicate receiverPositionIn(Join<RoleRequest, User> receiver, Set<String> orgUnitUuids, CriteriaBuilder cb, AbstractQuery<?> query) {
		if (orgUnitUuids.isEmpty()) {
			return cb.disjunction();
		}
		Subquery<Long> position = query.subquery(Long.class);
		Root<Position> positionRoot = position.from(Position.class);
		position.select(cb.literal(1L)).where(
			cb.equal(positionRoot.get("user").get("uuid"), receiver.get("uuid")),
			positionRoot.get("orgUnit").get("uuid").in(orgUnitUuids)
		);
		return cb.exists(position);
	}
}
