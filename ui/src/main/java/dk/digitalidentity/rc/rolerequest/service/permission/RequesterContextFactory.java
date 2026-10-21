package dk.digitalidentity.rc.rolerequest.service.permission;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.rolerequest.service.RequestAuthorizedRoleService;
import dk.digitalidentity.rc.rolerequest.service.scope.LimitedToItSystems;
import dk.digitalidentity.rc.rolerequest.service.scope.LimitedToType;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RequesterContextFactory {
	private final SecurityUtil securityUtil;
	private final UserService userService;
	private final OrgUnitService orgUnitService;
	private final RequestAuthorizedRoleService requestAuthorizedRoleService;

	/**
	 * @param receiverOrgUnit the receiver's OU the request is for, or {@code null} if the receiver has none
	 */
	public RequesterContext of(User requester, User receiver, OrgUnit receiverOrgUnit) {
		String receiverOuUuid = receiverOrgUnit == null ? null : receiverOrgUnit.getUuid();
		List<String> receiverOuAncestorUuids = receiverOrgUnit == null ? List.of() : orgUnitService.findAncestorUuidsIncludingSelf(receiverOuUuid);
		boolean self = Objects.equals(requester.getUuid(), receiver.getUuid());

		// RequesterRule ignores every other fact for admins
		if (securityUtil.isAdmin(requester)) {
			return new RequesterContext(true, self, false, false, false, Set.of(), receiverOuUuid, receiverOuAncestorUuids, true);
		}

		LimitedToItSystems itSystems = requestAuthorizedRoleService.accessibleItSystemsForOrgUnit(requester, receiverOrgUnit);

		return new RequesterContext(
			false,
			self,
			userService.isManagerOrSubstituteManagerFor(requester, receiver),
			orgUnitService.isAuthorizationManagerFor(requester, receiver),
			itSystems.type() != LimitedToType.NONE,
			toItSystemScope(itSystems),
			receiverOuUuid,
			receiverOuAncestorUuids,
			true
		);
	}

	private static Set<Long> toItSystemScope(LimitedToItSystems itSystems) {
		return switch (itSystems.type()) {
			case ALL -> null;
			case CONSTRAINED -> Set.copyOf(itSystems.itSystems());
			case NONE -> Set.of();
		};
	}
}
