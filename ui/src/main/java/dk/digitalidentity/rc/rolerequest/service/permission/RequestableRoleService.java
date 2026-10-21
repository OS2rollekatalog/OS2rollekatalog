package dk.digitalidentity.rc.rolerequest.service.permission;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.CombinedRoleViewDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserRoleViewDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.CombinedRoleView;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleView;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.dao.UserRolePermissionViewDao;
import dk.digitalidentity.rc.rolerequest.model.entity.UserRolePermissionView;
import dk.digitalidentity.rc.rolerequest.service.permission.RequesterRule.RoleGroupColumns;
import dk.digitalidentity.rc.rolerequest.service.permission.RequesterRule.UserRoleColumns;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentService;
import dk.digitalidentity.rc.util.Failure;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Answers "may this requester request these roles for this receiver" by applying {@link RequesterRule} in the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestableRoleService {
	private static final String USER_ROLE = "userRole";
	private static final String ROLE_GROUP = "roleGroup";

	private final RequesterContextFactory contextFactory;
	private final OrgUnitService orgUnitService;
	private final CurrentAssignmentService currentAssignmentService;
	private final UserRoleViewDatatableDao userRoleViewDao;
	private final UserRolePermissionViewDao userRolePermissionViewDao;
	private final CombinedRoleViewDatatableDao combinedRoleViewDao;

	public RequesterContext context(User requester, User receiver, OrgUnit receiverOrgUnit) {
		return contextFactory.of(requester, receiver, receiverOrgUnit);
	}

	public boolean canRequest(User requester, User receiver, OrgUnit receiverOrgUnit, UserRole userRole) {
		return !requestableUserRoleIds(requester, receiver, receiverOrgUnit, List.of(userRole.getId())).isEmpty();
	}

	public boolean canRequest(User requester, User receiver, OrgUnit receiverOrgUnit, RoleGroup roleGroup) {
		return !requestableRoleGroupIds(requester, receiver, receiverOrgUnit, List.of(roleGroup.getId())).isEmpty();
	}

	public Set<Long> requestableUserRoleIds(User requester, User receiver, OrgUnit receiverOrgUnit, Collection<Long> candidateIds) {
		return userRoleIds(context(requester, receiver, receiverOrgUnit), candidateIds);
	}

	public Set<Long> requestableRoleGroupIds(User requester, User receiver, OrgUnit receiverOrgUnit, Collection<Long> candidateIds) {
		return roleGroupIds(context(requester, receiver, receiverOrgUnit), candidateIds);
	}

	/**
	 * The user roles the requester may request for the receiver in at least one of the receiver's OUs.
	 */
	public Set<Long> requestableUserRoleIds(User requester, User receiver) {
		return contextForEachOrgUnit(requester, receiver).stream()
			.flatMap(context -> userRoleViewDao.findIds(userRoles(context)).stream())
			.collect(Collectors.toSet());
	}

	/**
	 * Which of the given roles the requester may request for the receiver in this OU.
	 */
	public PermittedRoleIds requestableAmong(User requester, User receiver, OrgUnit receiverOrgUnit, Collection<Long> userRoleIds, Collection<Long> roleGroupIds) {
		RequesterContext context = context(requester, receiver, receiverOrgUnit);
		return new PermittedRoleIds(userRoleIds(context, userRoleIds), roleGroupIds(context, roleGroupIds));
	}

	/**
	 * @throws org.springframework.security.access.AccessDeniedException if the role is not directly assigned to the receiver
	 */
	public boolean canRequestRemoval(User requester, User receiver, UserRole userRole) {
		assertUserRoleDirectlyAssigned(receiver, userRole.getId());
		return !removableUserRoleIds(requester, receiver, List.of(userRole.getId())).isEmpty();
	}

	/**
	 * @throws org.springframework.security.access.AccessDeniedException if the role group is not directly assigned to the receiver
	 */
	public boolean canRequestRemoval(User requester, User receiver, RoleGroup roleGroup) {
		assertRoleGroupDirectlyAssigned(receiver, roleGroup.getId());
		return !removableRoleGroupIds(requester, receiver, List.of(roleGroup.getId())).isEmpty();
	}

	/**
	 * Which of the given directly assigned roles the requester may have removed from the receiver.
	 * @throws org.springframework.security.access.AccessDeniedException if a role is not directly assigned to the receiver
	 */
	public PermittedRoleIds removableAmong(User requester, User receiver, Collection<Long> userRoleIds, Collection<Long> roleGroupIds) {
		userRoleIds.forEach(id -> assertUserRoleDirectlyAssigned(receiver, id));
		roleGroupIds.forEach(id -> assertRoleGroupDirectlyAssigned(receiver, id));
		List<RequesterContext> contexts = contextForEachOrgUnit(requester, receiver);
		return new PermittedRoleIds(removableUserRoleIds(contexts, userRoleIds), removableRoleGroupIds(contexts, roleGroupIds));
	}

	/**
	 * Removal ignores the OU filter and succeeds if the rule holds in any of the receiver's OUs.
	 * Unlike requesting, it also covers roles in deleted IT systems.
	 */
	public Set<Long> removableUserRoleIds(User requester, User receiver, Collection<Long> candidateIds) {
		if (candidateIds.isEmpty()) {
			return Collections.emptySet();
		}
		return removableUserRoleIds(contextForEachOrgUnit(requester, receiver), candidateIds);
	}

	public Set<Long> removableRoleGroupIds(User requester, User receiver, Collection<Long> candidateIds) {
		if (candidateIds.isEmpty()) {
			return Collections.emptySet();
		}
		return removableRoleGroupIds(contextForEachOrgUnit(requester, receiver), candidateIds);
	}

	private Set<Long> removableUserRoleIds(List<RequesterContext> contexts, Collection<Long> candidateIds) {
		if (candidateIds.isEmpty()) {
			return Collections.emptySet();
		}
		return contexts.stream()
			.flatMap(context -> userRolePermissionViewDao.findIds(removableUserRoles(context.withoutOuFilter())
				.and((root, _, _) -> root.get("id").in(candidateIds))).stream())
			.collect(Collectors.toSet());
	}

	private Set<Long> removableRoleGroupIds(List<RequesterContext> contexts, Collection<Long> candidateIds) {
		return contexts.stream()
			.flatMap(context -> roleGroupIds(context.withoutOuFilter(), candidateIds).stream())
			.collect(Collectors.toSet());
	}

	private void assertUserRoleDirectlyAssigned(User receiver, long userRoleId) {
		if (!currentAssignmentService.hasRoleDirectly(receiver.getUuid(), userRoleId)) {
			throw Failure.accessDenied(log, "UserRole {} is not directly assigned to receiver and therefore cannot be removed", userRoleId);
		}
	}

	private void assertRoleGroupDirectlyAssigned(User receiver, long roleGroupId) {
		if (!currentAssignmentService.hasRoleGroupDirectly(receiver.getUuid(), roleGroupId)) {
			throw Failure.accessDenied(log, "Rolegroup {} is not directly assigned to receiver and therefore cannot be removed", roleGroupId);
		}
	}

	public RequestAuthority requestAuthority(User requester, User receiver, OrgUnit receiverOrgUnit, UserRole userRole) {
		RequesterContext context = context(requester, receiver, receiverOrgUnit);
		return RequestAuthority.BY_PRECEDENCE.stream()
			.filter(authority -> !userRoleIds(context.only(authority), List.of(userRole.getId())).isEmpty())
			.findFirst()
			.orElse(RequestAuthority.NONE);
	}

	public RequestAuthority requestAuthority(User requester, User receiver, OrgUnit receiverOrgUnit, RoleGroup roleGroup) {
		RequesterContext context = context(requester, receiver, receiverOrgUnit);
		return RequestAuthority.BY_PRECEDENCE.stream()
			.filter(authority -> !roleGroupIds(context.only(authority), List.of(roleGroup.getId())).isEmpty())
			.findFirst()
			.orElse(RequestAuthority.NONE);
	}

	public Specification<UserRoleView> userRoles(RequesterContext context) {
		return (root, _, cb) -> RequesterRule.forUserRole(new UserRoleColumns(
			RequesterRule.effectivePermission(root),
			root.get("readOnly"),
			root.get("orgUnitFilterUuids"),
			root.get("itSystemOrgUnitFilterUuids"),
			root.get("itSystemId")
		), context, cb);
	}

	private static Specification<UserRolePermissionView> removableUserRoles(RequesterContext context) {
		return (root, _, cb) -> RequesterRule.forUserRole(new UserRoleColumns(
			RequesterRule.effectivePermission(root),
			root.get("readOnly"),
			cb.nullLiteral(String.class),
			cb.nullLiteral(String.class),
			root.get("itSystemId")
		), context, cb);
	}

	/**
	 * Covers both the user role and the role group rows of the combined view.
	 */
	public Specification<CombinedRoleView> combinedRoles(RequesterContext context) {
		return (root, query, cb) -> cb.or(
			cb.and(
				cb.equal(root.get("type"), USER_ROLE),
				RequesterRule.forUserRole(new UserRoleColumns(
					RequesterRule.effectivePermission(root),
					root.get("readOnly"),
					root.get("orgUnitFilterUuids"),
					root.get("itSystemOrgUnitFilterUuids"),
					root.get("itSystemId")
				), context, cb)
			),
			cb.and(
				cb.equal(root.get("type"), ROLE_GROUP),
				RequesterRule.forRoleGroup(new RoleGroupColumns(
					RequesterRule.effectivePermission(root),
					root.get("orgUnitFilterUuids"),
					root.get("id")
				), context, cb, query)
			)
		);
	}

	public Specification<CombinedRoleView> roleGroups(RequesterContext context) {
		return combinedRoles(context).and((root, _, cb) -> cb.equal(root.get("type"), ROLE_GROUP));
	}

	private Set<Long> userRoleIds(RequesterContext context, Collection<Long> candidateIds) {
		if (candidateIds.isEmpty()) {
			return Collections.emptySet();
		}
		return userRoleViewDao.findIds(userRoles(context).and((root, _, _) -> root.get("id").in(candidateIds)));
	}

	private Set<Long> roleGroupIds(RequesterContext context, Collection<Long> candidateIds) {
		if (candidateIds.isEmpty()) {
			return Collections.emptySet();
		}
		return combinedRoleViewDao.findIds(roleGroups(context).and((root, _, _) -> root.get("id").in(candidateIds)));
	}

	private List<RequesterContext> contextForEachOrgUnit(User requester, User receiver) {
		List<OrgUnit> orgUnits = orgUnitService.getOrgUnitsForUser(receiver);
		if (orgUnits.isEmpty()) {
			return List.of(context(requester, receiver, null));
		}
		return orgUnits.stream().map(orgUnit -> context(requester, receiver, orgUnit)).toList();
	}
}
