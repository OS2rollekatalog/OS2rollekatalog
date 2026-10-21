package dk.digitalidentity.rc.rolerequest.service;

import static dk.digitalidentity.rc.config.Constants.ROLE_REQUESTAUTHORIZED;
import static dk.digitalidentity.rc.rolerequest.RequestConstants.CACHE_PREFIX;

import java.util.Collections;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.rolerequest.service.permission.ResolvedPermissionService;
import dk.digitalidentity.rc.rolerequest.service.scope.LimitedToItSystems;
import dk.digitalidentity.rc.rolerequest.service.scope.LimitedToOrgUnits;
import dk.digitalidentity.rc.rolerequest.service.scope.LimitedToType;
import dk.digitalidentity.rc.rolerequest.service.scope.SystemRoleScope;
import dk.digitalidentity.rc.rolerequest.service.scope.SystemRoleScopeResolver;
import lombok.RequiredArgsConstructor;

/**
 * Service that is responsible for operations in relation to the request authorized role
 */
@Service
@RequiredArgsConstructor
public class RequestAuthorizedRoleService {
	private final ResolvedPermissionService resolvedPermissionService;
	private final SystemRoleScopeResolver scopeResolver;

	/**
	 * Will look through all roles and check if any is setup to can be requested by authorized role
	 */
	@Cacheable(value = CACHE_PREFIX + "AuthorizedRoleCanRequest")
	public boolean requestAuthorizedRoleCanRequest() {
		return resolvedPermissionService.anyRequestableByAuthorized();
	}

	/**
	 * Returns all org units uuids that are accessible by the users request authorized role, regardless of it-system.
	 * If the user does not have the roles, no OUs are returned.
	 * If the user has the authorized role, the method will check which OUs are accessible based on constraints.
	 */
	public LimitedToOrgUnits accessibleOrgUnits(final User user) {
		return scopeResolver.orgUnitScope(user, ROLE_REQUESTAUTHORIZED);
	}

	/**
	 * The it-systems the user may request or approve for within the given OU.
	 * @param orgUnit {@code null} only matches the scope for all OUs
	 */
	public LimitedToItSystems accessibleItSystemsForOrgUnit(final User user, final OrgUnit orgUnit) {
		final List<SystemRoleScope> scopes = scopeResolver.scopesByOrgUnit(user, ROLE_REQUESTAUTHORIZED);
		return scopes.stream()
			.filter(scope -> orgUnit != null && scope.orgUnits().orgUnits().contains(orgUnit.getUuid()))
			.findFirst()
			.or(() -> scopes.stream().filter(scope -> scope.orgUnits().type() == LimitedToType.ALL).findFirst())
			.map(SystemRoleScope::itSystems)
			.orElseGet(() -> new LimitedToItSystems(LimitedToType.NONE, Collections.emptySet()));
	}
}
