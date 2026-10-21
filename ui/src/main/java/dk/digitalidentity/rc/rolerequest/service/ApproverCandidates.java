package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.AuthorizationManager;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
class ApproverCandidates {

	/**
	 * A possible recipient of the approval email for a request.
	 * @param approver the user whose approval rights must be checked before mailing,
	 *                 or {@code null} for an email from the settings, which is always mailed
	 */
	record Candidate(String email, String name, User approver) {}

	private final SettingsService settingsService;
	private final OrgUnitService orgUnitService;
	private final ManagerSubstituteService managerSubstituteService;
	private final ItSystemService itSystemService;
	private final SystemRoleService systemRoleService;
	private final UserRoleService userRoleService;
	private final AssignmentService assignmentService;

	/**
	 * The candidates per request, in the order their emails should be listed.
	 * Candidates are not filtered by whether their approver may actually approve the request.
	 */
	Map<RoleRequest, List<Candidate>> collect(List<RoleRequest> requests) {
		Map<ApprovableBy, String> emailsBySetting = settingsService.getRoleRequestApproverEmails();
		Lookups lookups = new Lookups();
		Map<RoleRequest, List<Candidate>> result = new LinkedHashMap<>();
		for (RoleRequest request : requests) {
			result.put(request, candidates(request, emailsBySetting, lookups));
		}
		return result;
	}

	private List<Candidate> candidates(RoleRequest request, Map<ApprovableBy, String> emailsBySetting, Lookups lookups) {
		List<Candidate> candidates = new ArrayList<>();
		List<ApprovableBy> approverTypes = request.getApproverOption() == null ? List.of() : request.getApproverOption().getApprovableBy();
		for (ApprovableBy approverType : approverTypes) {
			String emailBySetting = emailsBySetting.get(approverType);
			if (StringUtils.hasLength(emailBySetting)) {
				candidates.add(new Candidate(emailBySetting, null, null));
				continue;
			}
			switch (approverType) {
				case AUTHRESPONSIBLE -> authorizationManagers(request).forEach(user -> candidates.add(candidate(user)));
				case MANAGERORSUBSTITUTE -> lookups.nearestManagers(request).forEach(user -> candidates.add(candidate(user)));
				case AUTHORIZED -> lookups.holdersOf(Constants.ROLE_REQUESTAUTHORIZED).forEach(user -> candidates.add(candidate(user)));
				case APPROVEAUTHORIZED -> lookups.holdersOf(Constants.ROLE_APPROVEAUTHORIZED).forEach(user -> candidates.add(candidate(user)));
				case SYSTEMRESPONSIBLE -> itSystems(request).forEach(itSystem ->
					lookups.systemResponsibles(itSystem).forEach(user -> candidates.add(candidate(user))));
				default -> {
				}
			}
		}
		return candidates;
	}

	// from the receiver's positions, not the request's OU, as in ApproverRule
	private static List<User> authorizationManagers(RoleRequest request) {
		User receiver = request.getReceiver();
		if (receiver == null || receiver.getPositions() == null) {
			return List.of();
		}
		return receiver.getPositions().stream()
			.map(Position::getOrgUnit)
			.filter(orgUnit -> orgUnit != null && orgUnit.getAuthorizationManagers() != null)
			.flatMap(orgUnit -> orgUnit.getAuthorizationManagers().stream())
			.map(AuthorizationManager::getUser)
			.filter(user -> user != null && !user.isDeleted() && user.getEmail() != null)
			.toList();
	}

	private static List<ItSystem> itSystems(RoleRequest request) {
		if (request.getUserRole() != null) {
			return request.getUserRole().getItSystem() == null ? List.of() : List.of(request.getUserRole().getItSystem());
		}
		if (request.getRoleGroup() == null || request.getRoleGroup().getUserRoleAssignments() == null) {
			return List.of();
		}
		return request.getRoleGroup().getUserRoleAssignments().stream()
			.map(RoleGroupUserRoleAssignment::getUserRole)
			.filter(userRole -> userRole != null && userRole.getItSystem() != null)
			.map(UserRole::getItSystem)
			.toList();
	}

	private static Candidate candidate(User user) {
		return new Candidate(user.getEmail(), user.getName(), user);
	}

	/**
	 * Lookups that do not depend on the individual request, cached for one run.
	 */
	private final class Lookups {
		private final Map<String, List<User>> holdersBySystemRoleIdentifier = new HashMap<>();
		private final Map<String, List<User>> nearestManagersByOrgUnitAndReceiver = new HashMap<>();
		private final Map<ItSystem, List<User>> systemResponsiblesByItSystem = new IdentityHashMap<>();

		/**
		 * Users with an active role containing the given role catalogue system role.
		 */
		List<User> holdersOf(String systemRoleIdentifier) {
			return holdersBySystemRoleIdentifier.computeIfAbsent(systemRoleIdentifier, _ -> {
				ItSystem roleCatalogue = itSystemService.getFirstByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
				SystemRole systemRole = systemRoleService.getFirstByIdentifierAndItSystemId(systemRoleIdentifier, roleCatalogue.getId());
				if (systemRole == null) {
					log.warn("System role {} does not exist, cannot send emails to its holders", systemRoleIdentifier);
					return List.of();
				}
				return userRoleService.findAllBySystemRole(systemRole).stream()
					.flatMap(userRole -> assignmentService.getActiveByUserRole(userRole).stream())
					.map(CurrentAssignment::getUser)
					.filter(user -> user.getEmail() != null)
					.collect(Collectors.toMap(User::getEmail, user -> user, (first, _) -> first))
					.values().stream()
					.sorted(Comparator.comparing(User::getEmail))
					.toList();
			});
		}

		List<User> nearestManagers(RoleRequest request) {
			OrgUnit orgUnit = request.getOrgUnit();
			if (orgUnit == null) {
				return List.of();
			}
			String receiverUuid = request.getReceiver() == null ? null : request.getReceiver().getUuid();
			return nearestManagersByOrgUnitAndReceiver.computeIfAbsent(orgUnit.getUuid() + "|" + receiverUuid, _ -> {
				OrgUnitService.EffectiveApprover effectiveApprover = orgUnitService.getEffectiveApprover(orgUnit, request.getReceiver());
				if (effectiveApprover == null) {
					return List.of();
				}
				return Stream.concat(Stream.of(effectiveApprover.manager()), managerSubstituteService.getSubstitutesForOrgUnit(effectiveApprover.orgUnit()).stream())
					.filter(user -> user != null && !user.isDeleted() && StringUtils.hasLength(user.getEmail()))
					.toList();
			});
		}

		List<User> systemResponsibles(ItSystem itSystem) {
			return systemResponsiblesByItSystem.computeIfAbsent(itSystem, _ -> {
				List<User> responsibles = itSystemService.getAttestationResponsibles(itSystem);
				if (responsibles.isEmpty()) {
					log.warn("ItSystem {} has no attestation responsible, cannot send system responsible emails", itSystem.getId());
				}
				return responsibles.stream()
					.filter(user -> {
						if (user.getEmail() == null) {
							log.warn("Attestation responsible {} on itSystem {} has no email, cannot send system responsible email", user.getUuid(), itSystem.getId());
							return false;
						}
						return true;
					})
					.toList();
			});
		}
	}
}
