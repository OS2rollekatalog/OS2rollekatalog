package dk.digitalidentity.rc.rolerequest.service.permission;

import static dk.digitalidentity.rc.config.Constants.ROLE_APPROVEAUTHORIZED;
import static dk.digitalidentity.rc.config.Constants.ROLE_REQUESTAUTHORIZED;

import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.rolerequest.service.scope.LimitedToItSystems;
import dk.digitalidentity.rc.rolerequest.service.scope.LimitedToOrgUnits;
import dk.digitalidentity.rc.rolerequest.service.scope.SystemRoleScope;
import dk.digitalidentity.rc.rolerequest.service.scope.SystemRoleScopeResolver;
import dk.digitalidentity.rc.rolerequest.service.permission.ApproverContext.AuthorizedScope;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ApproverContextFactory {
	private final SecurityUtil securityUtil;
	private final SettingsService settingsService;
	private final OrgUnitService orgUnitService;
	private final ItSystemDao itSystemDao;
	private final SystemRoleScopeResolver scopeResolver;

	@Transactional(readOnly = true)
	public ApproverContext of(User approver) {
		return new ApproverContext(
			approver.getUuid(),
			securityUtil.isAdmin(approver),
			approver.isDeleted(),
			settingsService.isAllowSelfApprovalEnabled(),
			orgUnitService.getByAuthorizationManagerMatchingUser(approver).stream().map(OrgUnit::getUuid).collect(Collectors.toSet()),
			itSystemDao.findIdsByAttestationResponsibleUserId(approver.getUserId()),
			authorizedScopes(approver, ROLE_REQUESTAUTHORIZED),
			authorizedScopes(approver, ROLE_APPROVEAUTHORIZED)
		);
	}

	private List<AuthorizedScope> authorizedScopes(User approver, String systemRoleIdentifier) {
		return scopeResolver.scopesByOrgUnit(approver, systemRoleIdentifier).stream().map(ApproverContextFactory::toAuthorizedScope).toList();
	}

	private static AuthorizedScope toAuthorizedScope(SystemRoleScope scope) {
		return new AuthorizedScope(toOrgUnitScope(scope.orgUnits()), toItSystemScope(scope.itSystems()));
	}

	private static Set<String> toOrgUnitScope(LimitedToOrgUnits orgUnits) {
		return switch (orgUnits.type()) {
			case ALL -> null;
			case CONSTRAINED -> Set.copyOf(orgUnits.orgUnits());
			case NONE -> Set.of();
		};
	}

	private static Set<Long> toItSystemScope(LimitedToItSystems itSystems) {
		return switch (itSystems.type()) {
			case ALL -> null;
			case CONSTRAINED -> Set.copyOf(itSystems.itSystems());
			case NONE -> Set.of();
		};
	}
}
