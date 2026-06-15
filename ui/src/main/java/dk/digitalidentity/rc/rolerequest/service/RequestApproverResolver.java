package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestApproverResolver {

	private final ApproverOptionService approverOptionService;
	private final RequestAuthorizedRoleService requestAuthorizedRoleService;
	private final OrgUnitService orgUnitService;
	private final ItSystemService itSystemService;
	private final SettingsService settingsService;

	public boolean canApprove(RoleRequest request, User approver) {
		// As a rule a user may not approve a request they themselves created. The municipality can
		// opt out of this restriction via a setting (e.g. to support decentralised approval where an
		// authorized approver requests on behalf of others and approves it themselves). When the setting
		// is enabled the self-approval block is lifted, but the regular approver-permission checks below
		// still apply, so the user must otherwise be entitled to approve the request.
		if (Objects.equals(request.getRequester(), approver) && !settingsService.isAllowSelfApprovalEnabled()) {
			return false;
		}
		if (request.getUserRole() != null) {
			return canApproveForUserRole(request.getUserRole(), request.getOrgUnit(), approver, request.getReceiver());
		} else if (request.getRoleGroup() != null) {
			return canApproveForRoleGroup(request.getRoleGroup(), request.getOrgUnit(), approver, request.getReceiver());
		}
		log.error("Request {} has neither a userRole nor a roleGroup", request.getId());
		return false;
	}

	public List<ApprovableBy> resolveEffectiveOptions(RoleRequest request) {
		if (request.getUserRole() != null) {
			return approverOptionService.getInheritedApproverOption(request.getUserRole());
		} else if (request.getRoleGroup() != null) {
			return approverOptionService.getInheritedApproverOption(request.getRoleGroup());
		}
		return List.of();
	}

	public boolean isOuAccessAllowed(RoleRequest request, User user) {
		RequestAuthorizedRoleService.LimitedToOrgUnits accessibleOUs = requestAuthorizedRoleService.accessibleOrgUnits(user);
		return accessibleOUs.type() == RequestAuthorizedRoleService.LimitedToType.ALL
			|| (request.getOrgUnit() != null && accessibleOUs.orgUnits().contains(request.getOrgUnit().getUuid()));
	}

	public boolean isItSystemAccessAllowed(RoleRequest request, User user) {
		RequestAuthorizedRoleService.LimitedToItSystems accessibleItSystems = requestAuthorizedRoleService.accessibleItsSystems(user);
		if (accessibleItSystems.type() == RequestAuthorizedRoleService.LimitedToType.ALL) {
			return true;
		}
		if (request.getUserRole() != null && request.getUserRole().getItSystem() != null) {
			return accessibleItSystems.itSystems().contains(request.getUserRole().getItSystem().getId());
		}
		if (request.getRoleGroup() != null) {
			List<RoleGroupUserRoleAssignment> assignments = request.getRoleGroup().getUserRoleAssignments();
			return assignments != null && !assignments.isEmpty()
				&& assignments.stream()
					.filter(a -> a.getUserRole() != null && a.getUserRole().getItSystem() != null)
					.allMatch(a -> accessibleItSystems.itSystems().contains(a.getUserRole().getItSystem().getId()));
		}
		return false;
	}

	private boolean canApproveForUserRole(UserRole role, OrgUnit orgUnit, User approver, User receiver) {
		List<ApprovableBy> options = approverOptionService.getInheritedApproverOption(role);

		if (options.contains(ApprovableBy.AUTOMATIC)) {
			return true;
		}

		if (options.contains(ApprovableBy.SYSTEMRESPONSIBLE)) {
			ItSystem itSystem = role.getItSystem();
			if (itSystem != null && itSystemService.getAttestationResponsibleUserIds(itSystem).contains(approver.getUserId())) {
				return true;
			}
		}

		if (options.contains(ApprovableBy.AUTHORIZED)) {
			RequestAuthorizedRoleService.LimitedToItSystems accessibleItSystems = requestAuthorizedRoleService.accessibleItsSystems(approver);
			if (isItSystemAccessAllowed(accessibleItSystems, role.getItSystem()) && isPermittedInOu(approver, orgUnit)) {
				return true;
			}
		}

		return determineApprovable(options, approver, orgUnit, receiver);
	}

	private boolean canApproveForRoleGroup(RoleGroup roleGroup, OrgUnit orgUnit, User approver, User receiver) {
		List<ApprovableBy> options = approverOptionService.getInheritedApproverOption(roleGroup);

		if (options.contains(ApprovableBy.AUTOMATIC)) {
			return true;
		}

		if (options.contains(ApprovableBy.SYSTEMRESPONSIBLE)) {
			// The approver must be the attestation responsible for ALL of the bundle's IT-systems,
			// not just one of them — mirroring the AUTHORIZED check below. Otherwise being responsible
			// for a single contained role would let you approve the entire bundle, including roles in
			// IT-systems you have no responsibility for.
			List<RoleGroupUserRoleAssignment> assignments = roleGroup.getUserRoleAssignments();
			boolean systemResponsibleOk = assignments != null && !assignments.isEmpty()
				&& assignments.stream()
					.filter(a -> a.getUserRole() != null && a.getUserRole().getItSystem() != null)
					.allMatch(a -> itSystemService.getAttestationResponsibleUserIds(a.getUserRole().getItSystem()).contains(approver.getUserId()));
			if (systemResponsibleOk) {
				return true;
			}
		}

		if (options.contains(ApprovableBy.AUTHORIZED)) {
			List<RoleGroupUserRoleAssignment> assignments = roleGroup.getUserRoleAssignments();
			RequestAuthorizedRoleService.LimitedToItSystems accessibleItSystems = requestAuthorizedRoleService.accessibleItsSystems(approver);
			boolean itSystemOk = assignments != null && !assignments.isEmpty()
				&& assignments.stream()
					.filter(a -> a.getUserRole() != null && a.getUserRole().getItSystem() != null)
					.allMatch(a -> isItSystemAccessAllowed(accessibleItSystems, a.getUserRole().getItSystem()));
			if (itSystemOk && isPermittedInOu(approver, orgUnit)) {
				return true;
			}
		}

		return determineApprovable(options, approver, orgUnit, receiver);
	}

	private static boolean isItSystemAccessAllowed(RequestAuthorizedRoleService.LimitedToItSystems accessibleItSystems, ItSystem itSystem) {
		return accessibleItSystems.type() == RequestAuthorizedRoleService.LimitedToType.ALL
			|| (itSystem != null && accessibleItSystems.itSystems().contains(itSystem.getId()));
	}

	private boolean isPermittedInOu(User approver, OrgUnit orgUnit) {
		RequestAuthorizedRoleService.LimitedToOrgUnits accessibleOUs = requestAuthorizedRoleService.accessibleOrgUnits(approver);
		return accessibleOUs.type() == RequestAuthorizedRoleService.LimitedToType.ALL
			|| (orgUnit != null && accessibleOUs.orgUnits().contains(orgUnit.getUuid()));
	}

	private boolean determineApprovable(List<ApprovableBy> options, User approver, OrgUnit orgUnit, User receiver) {
		if (options.contains(ApprovableBy.MANAGERORSUBSTITUTE)) {
			// Walk up the hierarchy to find an effective approver, skipping any OU
			// where the manager is the receiver themselves (self-approval not allowed).
			OrgUnitService.EffectiveApprover effectiveApprover = orgUnitService.getEffectiveApprover(orgUnit, receiver);
			if (effectiveApprover != null) {
				User manager = effectiveApprover.manager();
				OrgUnit resolvedOu = effectiveApprover.orgUnit();
				boolean isSub = manager.getManagerSubstitutes().stream()
					.anyMatch(ms -> Objects.equals(ms.getSubstitute(), approver)
						&& ms.getOrgUnit() != null && Objects.equals(ms.getOrgUnit().getUuid(), resolvedOu.getUuid()));
				if (Objects.equals(manager, approver) || isSub) {
					return true;
				}
			}
		}

		if (options.contains(ApprovableBy.AUTHRESPONSIBLE)) {
			return orgUnitService.isAuthorizationManagerFor(approver, receiver);
		}

		return false;
	}
}
