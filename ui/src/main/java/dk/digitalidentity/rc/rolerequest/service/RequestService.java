package dk.digitalidentity.rc.rolerequest.service;

import static dk.digitalidentity.rc.rolerequest.RequestConstants.CACHE_PREFIX;
import static java.util.stream.Collectors.groupingBy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.CombinedRoleViewDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.CombinedRoleView;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.RoleAssignedToUserDTO;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import dk.digitalidentity.rc.service.model.UserRoleAssignedToUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.SessionConstants;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.RolegroupDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserRoleViewDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleView;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.exceptions.NotFoundException;
import dk.digitalidentity.rc.rolerequest.dao.RoleRequestDao;
import dk.digitalidentity.rc.rolerequest.dao.SpecificationBuilder;
import dk.digitalidentity.rc.rolerequest.log.RequestLogEvent;
import dk.digitalidentity.rc.rolerequest.log.RequestLoggable;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestPostponedConstraint;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestService {
	private final UserRoleViewDatatableDao userRoleDatatableDao;
	private final RoleRequestDao roleRequestDao;
	private final UserService userService;
	private final OrgUnitService orgUnitService;
	private final SettingsService settingsService;
	private final UserRoleService userRoleService;
	private final RoleGroupService roleGroupService;
	private final RolegroupDatatableDao rolegroupDatatableDao;
	private final ItSystemService itSystemService;
	private final RequestNotifierService requestNotifierService;
	private final ApproverOptionService approverOptionService;
	private final RequestAuthorizedRoleService requestAuthorizedRoleService;
	private final UserDatatableDao userDatatableDao;
	private final CombinedRoleViewDatatableDao combinedRoleViewDao;

	public boolean hasPendingRemovalRequestForUserrole(Long userRoleId, String recieverUuid) {
		return !roleRequestDao.findByRequestActionAndReceiver_UuidAndUserRole_Id(RequestAction.REMOVE, recieverUuid, userRoleId).isEmpty();
	}

	public boolean hasPendingRemovalRequestForRolegroup(Long roleGroupId, String recieverUuid) {
		return !roleRequestDao.findByRequestActionAndReceiver_UuidAndRoleGroup_Id(RequestAction.REMOVE, recieverUuid, roleGroupId).isEmpty();
	}

	/**
	 * This method checks if the current user can request any roles for them self
	 */
	public boolean canRequestAnyRoles(final User user) {
		// TODO KBP - TMP TMPT This performed so poorly we we are returning true for now - this will result in a button being
		// shown even though there might not be any roles that can be requested.
		// Needs fixing asap

		return true;
/*		List<RoleGroupAssignedToUser> roleGroups = userService.getAllRoleGroupsAssignedToUser(user);
		int countOfAlreadyAssigned = 0;
		int countOfAllRoleGroups = 0;
		int countOfAllUserRoles = 0;
		int countOfAlreadyAssignedOrRequestedRoleGroups = roleGroups.size();
		for (UserRole userRole : userRoleService.getAll()) {
			List<Position> positions = user.getPositions();
			for (Position position : positions) {
				if (canRequest(userRole, user, position.getOrgUnit(), settingsService.getRolerequestRequester())) {
					countOfAllUserRoles++;
					break;
				}
			}
		}
		List<RoleGroup> allRoleGroups = roleGroupService.getAll();
		for (RoleGroup entry : allRoleGroups) {
			List<Position> positions = user.getPositions();
			for (Position position : positions) {
				if (canRequest(user, entry, user, position.getOrgUnit())) {
					countOfAllRoleGroups++;
					break;
				}
			}
		}
		for (ItSystem itSystem : itSystemService.getAll()) {
			countOfAlreadyAssigned += userService.getAllUserRolesAssignedToUser(user, itSystem).size();
		}
		return (countOfAlreadyAssigned != countOfAllUserRoles) ||
			(countOfAlreadyAssignedOrRequestedRoleGroups != countOfAllRoleGroups);*/
	}

	/**
	 * Determines if a role can be requested for a user
	 *
	 * @param role              the UserRole being requested
	 * @param receivingUser     the user for which the role is being requested
	 * @param receiversOrgUnit  the orgUnit of the receiving user
	 * @param globalRequesterSetting global requester settings
	 * @return true if user can request the role, false otherwise
	 */
	public boolean canRequest(final UserRole role, final User receivingUser, final OrgUnit receiversOrgUnit, List<RequestableBy> globalRequesterSetting) {
		if (role.isReadOnly()) {
			return false;
		}

		// Check orgUnit filter - if role  or itSystem has orgUnit filter and receiving user's orgUnit is not in it, deny immediately
		// UNLESS user is admin - admin can always request regardless of orgUnit filter
		if (!SecurityUtil.isAdmin()) {
			if (role.getItSystem().getOrgUnitFilterOrgUnits() != null && !role.getItSystem().getOrgUnitFilterOrgUnits().isEmpty()) {
				List<String> ouUuidsWithChildren = itSystemService.getOUFilterUuidsWithChildren(role.getItSystem());
				if (receiversOrgUnit == null || ouUuidsWithChildren.stream()
					.noneMatch(uuid -> uuid.equals(receiversOrgUnit.getUuid()))) {
					return false;
				}
			}

			if (role.getOrgUnitFilterOrgUnits() != null && !role.getOrgUnitFilterOrgUnits().isEmpty()) {
				List<String> ouUuidsWithChildren = userRoleService.getOUFilterUuidsWithChildren(role);
				if (receiversOrgUnit == null || ouUuidsWithChildren.stream()
					.noneMatch(uuid -> uuid.equals(receiversOrgUnit.getUuid()))) {
					return false;
				}
			}
		}


		List<RequestableBy> relevantPermission = role.getRequesterPermission();

		ItSystem itSystem = role.getItSystem();
		// Check ITSystem permission if role is set to inherit
		if (relevantPermission != null && relevantPermission.contains(RequestableBy.INHERIT)) {
			relevantPermission = itSystem.getRequesterPermission();
		}

		// Check global permissions if ITSystem and role is set to inherit
		if (relevantPermission == null || relevantPermission.contains(RequestableBy.INHERIT)) {
			relevantPermission = globalRequesterSetting;
		}

		// If permission is still null or inherit at this point, something is wrong
		if (relevantPermission == null || relevantPermission.contains(RequestableBy.INHERIT)) {
			throw new IllegalArgumentException("Global settings for requester should never be null.");
		}

		if (relevantPermission.contains(RequestableBy.NONE)) {
			return false;
		}

		// Check ADMIN permission
		if (SecurityUtil.isAdmin() && relevantPermission.contains(RequestableBy.ADMIN)) {
			return true;
		}

		// Check AUTHORIZED permission with constraints
		boolean isRequestAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED);
		if (isRequestAuthorized && !SecurityUtil.isAdmin() && relevantPermission.contains(RequestableBy.AUTHORIZED)) {
			final User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
			final RequestAuthorizedRoleService.LimitedToOrgUnits accessibleOrgUnits = requestAuthorizedRoleService.accessibleOrgUnits(loggedInUser);
			final RequestAuthorizedRoleService.LimitedToItSystems accessibleItSystems = requestAuthorizedRoleService.accessibleItsSystems(loggedInUser);

			// Check if requesting user has AUTHORIZED access to the receiving user's orgUnit
			boolean hasOrgUnitAccess = accessibleOrgUnits.type() == RequestAuthorizedRoleService.LimitedToType.ALL
				|| (receiversOrgUnit != null && accessibleOrgUnits.orgUnits().contains(receiversOrgUnit.getUuid()));

			// Check if requesting user has access to the specific IT system
			boolean hasItSystemAccess = accessibleItSystems.type() == RequestAuthorizedRoleService.LimitedToType.ALL
				|| (itSystem != null && accessibleItSystems.itSystems().contains(itSystem.getId()));

			if (hasOrgUnitAccess && hasItSystemAccess) {
				return true;
			}
		}

		return determineRequestable(relevantPermission, receivingUser);
	}

	/**
	 * Determines if a rolegroup can be requested for a user
	 *
	 * @param requestingUser    the user requesting the role
	 * @param role              the RoleGroup being requested
	 * @param receivingUser     the user for which the role is being requested
	 * @param receiversOrgUnit  the orgUnit of the receiving user
	 * @return true if request is allowed, false otherwise
	 */
	public boolean canRequest(final User requestingUser, RoleGroup role, User receivingUser, final OrgUnit receiversOrgUnit) {

		List<RequestableBy> relevantPermission = role.getRequesterPermission();

		// Check global permissions if role is set to inherit
		if (relevantPermission == null || relevantPermission.contains(RequestableBy.INHERIT)) {
			relevantPermission = settingsService.getRolerequestRequester();
		}

		// If permission is still null or inherit at this point, something is wrong
		if (relevantPermission == null || relevantPermission.contains(RequestableBy.INHERIT)) {
			throw new IllegalArgumentException("Global settings for requester should never be null.");
		}

		// Check ADMIN permission
		if (SecurityUtil.isAdmin() && relevantPermission.contains(RequestableBy.ADMIN)) {
			return true;
		}

		// Check AUTHORIZED permission with constraints
		boolean isRequestAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED);
		if (isRequestAuthorized && !SecurityUtil.isAdmin() && relevantPermission.contains(RequestableBy.AUTHORIZED)) {
			final RequestAuthorizedRoleService.LimitedToOrgUnits accessibleOrgUnits = requestAuthorizedRoleService.accessibleOrgUnits(requestingUser);
			final RequestAuthorizedRoleService.LimitedToItSystems accessibleItSystems = requestAuthorizedRoleService.accessibleItsSystems(requestingUser);

			// Check if requesting user has AUTHORIZED access to the receiving user's orgUnit
			boolean hasOrgUnitAccess = accessibleOrgUnits.type() == RequestAuthorizedRoleService.LimitedToType.ALL
				|| (receiversOrgUnit != null && accessibleOrgUnits.orgUnits().contains(receiversOrgUnit.getUuid()));

			// Check IT system access
			boolean hasAccessToItSystems;
			if (accessibleItSystems.type() == RequestAuthorizedRoleService.LimitedToType.ALL) {
				hasAccessToItSystems = true;
			} else if (accessibleItSystems.type() == RequestAuthorizedRoleService.LimitedToType.CONSTRAINED) {
				Set<Long> accessibleIds = accessibleItSystems.itSystems();

				// Check if all user roles in the role group have IT systems that are accessible
				hasAccessToItSystems = role.getUserRoleAssignments() != null
					&& !role.getUserRoleAssignments().isEmpty()
					&& role.getUserRoleAssignments().stream()
					.allMatch(assignment -> assignment.getUserRole() != null
						&& assignment.getUserRole().getItSystem() != null
						&& accessibleIds.contains(assignment.getUserRole().getItSystem().getId()));
			} else {
				hasAccessToItSystems = false;
			}

			if (hasOrgUnitAccess && hasAccessToItSystems) {
				return true;
			}
		}

		return determineRequestable(relevantPermission, receivingUser);
	}

	public boolean canApprove(RoleRequest request) {
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (SecurityUtil.isAdmin()) {
			return true;
		}
		if (request.getReceiver() != null && loggedInUser.getUuid().equalsIgnoreCase(request.getReceiver().getUuid())) {
			return false;
		}
		//Check if user can approve this request
		if (request.getUserRole() == null) {
			return canApprove(request.getRoleGroup(), loggedInUser);
		} else {
			return canApprove(loggedInUser, request.getUserRole(), loggedInUser, request.getOrgUnit());
		}
	}

	private boolean canApprove(final User requestingUser, UserRole role, User approvingUser, final OrgUnit receiversOrgUnit) {

		if (SecurityUtil.isAdmin()) {
			return true;
		}

		List<ApprovableBy> relevantPermission = role.getApproverPermission();

		ItSystem itSystem = role.getItSystem();
		//Check ITSystem permission if role is set to inherit
		if (relevantPermission.contains(ApprovableBy.INHERIT)) {
			relevantPermission = itSystem.getApproverPermission();
		}

		//Check global permissions if ITSystem and role is set to inherit
		if (relevantPermission == null || relevantPermission.contains(ApprovableBy.INHERIT)) {
			relevantPermission = settingsService.getRolerequestApprover();
		}

		//If permission is still null or inherit at this point, something is wrong
		if (relevantPermission == null || relevantPermission.contains(ApprovableBy.INHERIT)) {
			throw new IllegalArgumentException("Global settings for approver should never be null.");
		}

		//if approval is automatic return true
		if (relevantPermission.contains(ApprovableBy.AUTOMATIC)) {
			return true;
		}

		//if setting is system responsible, return true
		if (relevantPermission.contains(ApprovableBy.SYSTEMRESPONSIBLE) && itSystem.getAttestationResponsible() == approvingUser) {
			return true;
		}

		//Check is user is Authorized and constraints allow approval of authorized
		boolean isAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED); // bemyndiget
		if (isAuthorized && relevantPermission.contains(ApprovableBy.AUTHORIZED)) {
			if (isAuthorizedForITSystem(itSystem, requestingUser) && isAuthorizedForOrgUnit(receiversOrgUnit, requestingUser)) {
				return true;
			}
		}

		return determineApprovable(relevantPermission, approvingUser);
	}

	private boolean canApprove(RoleGroup role, User approvingUser) {
		if (SecurityUtil.isAdmin()) {
			return true;
		}

		List<ApprovableBy> relevantPermission = role.getApproverPermission();

		//Check global permissions if ITSystem and role is set to inherit
		if (relevantPermission == null || relevantPermission.contains(ApprovableBy.INHERIT)) {
			relevantPermission = settingsService.getRolerequestApprover();
		}

		//If permission is still null or inherit at this point, something is wrong
		if (relevantPermission == null || relevantPermission.contains(ApprovableBy.INHERIT)) {
			throw new IllegalArgumentException("Global settings for approver should never be null.");
		}

		//if approval is automatic return true
		if (relevantPermission.contains(ApprovableBy.AUTOMATIC)) {
			return true;
		}

		//Check is user is Authorized and constraints allow approval of authorized
		boolean isAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED); // bemyndiget
		if (isAuthorized && relevantPermission.contains(ApprovableBy.AUTHORIZED)) {
			return true;
		}

		return determineApprovable(relevantPermission, approvingUser);
	}

	/**
	 * Matches a role request permission with a user to determine if the user is allowed to request
	 *
	 * @param permissions   A list of RequestableBy values representing permissions for a role
	 * @param receivingUser the user for which the role is being requested
	 * @return true if request is allowed, false otherwise
	 */
	private boolean determineRequestable(List<RequestableBy> permissions, User receivingUser) {
		User requestingUser = userService.getOptionalByUserId(SecurityUtil.getUserId())
			.orElseThrow(() -> new NotFoundException("Unable to find user for user id: " + SecurityUtil.getUserId()));

		boolean isRequestingForSelf = requestingUser.equals(receivingUser);
		if (isRequestingForSelf && permissions.contains(RequestableBy.EMPLOYEE)) {
			return true;
		}

		boolean isManagerOrSubstitute = userService.isManagerOrSubstituteManagerFor(requestingUser, receivingUser);
		if (isManagerOrSubstitute && permissions.contains(RequestableBy.MANAGERORSUBSTITUTE)) {
			return true;
		}

		boolean isAuthResponsible = !orgUnitService.getByAuthorizationManagerMatchingUser(requestingUser).isEmpty();
		return isAuthResponsible && permissions.contains(RequestableBy.AUTHRESPONSIBLE);
	}

	private boolean determineApprovable(List<ApprovableBy> permissions, User approvingUser) {

		boolean isManagerOrSubstitute = !userService.getSubstitutesManager(approvingUser).isEmpty() || userService.isManager(approvingUser); // leder eller stedfortræder
		//Shortcircuit, if possible
		if (isManagerOrSubstitute && permissions.contains(ApprovableBy.MANAGERORSUBSTITUTE)) {
			return true;
		}

		boolean isAuthResponsible = !orgUnitService.getByAuthorizationManagerMatchingUser(approvingUser).isEmpty(); // autorisationsansvarlig
		return isAuthResponsible && permissions.contains(ApprovableBy.AUTHRESPONSIBLE);
	}

	/***
	 * Returns a list of UserRoles that can be requested for the given user, on behalf of the currently logged-in user
	 * @param recievingUser the User which is the target of the requested role
	 * @return a list of UserRoles that is allowed to be requested
	 */
	public Stream<UserRole> getRequestableUserRoles(User recievingUser) {
		User requestingUser = userService.getOptionalByUserId(SecurityUtil.getUserId()).orElseThrow(() -> new NotFoundException("Unable to find user for user id: " + SecurityUtil.getUserId()));
		boolean isAdmin = SecurityUtil.isAdmin();
		boolean isRequestingForSelf = requestingUser.equals(recievingUser); //anmodende bruger er brugeren som er logget ind
		boolean isManagerOrSubstitute = !userService.getSubstitutesManager(requestingUser).isEmpty() || userService.isManager(requestingUser); // leder eller stedfortræder
		boolean isAuthResponsible = !orgUnitService.getByAuthorizationManagerMatchingUser(requestingUser).isEmpty(); // autorisationsansvarlig
		boolean isAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED); // bemyndiget
		List<RequestableBy> globalPermission = settingsService.getRolerequestRequester();

		List<RequestableBy> permittedSettings = new ArrayList<>();
		for (RequestableBy permission : RequestableBy.values()) {
			if (
				isPermissionMatchingRights(permission, isRequestingForSelf, isManagerOrSubstitute, isAuthResponsible, isAdmin, isAuthorized)
			) {
				permittedSettings.add(permission);
			}
		}

		//construct allowed settings for inherited ITsystem permissions, adding INHERIT if global settings would allow user request access
		List<RequestableBy> allowedItSettings = new ArrayList<>(permittedSettings);
		if (permittedSettings.contains(globalPermission)) {
			allowedItSettings.add(RequestableBy.INHERIT);
		}

		Stream<UserRole> userRoles = Stream.concat(
			//find all roles with directly matching permissions
			userRoleService.getUserRolesWithRequesterPermissions(permittedSettings).stream(),
			//find all roles with INHERIT, which have IT systems matching permissions
			userRoleService.getUserRolesWithInheritedPermissionsMatching(allowedItSettings).stream());

		if (isAuthorized) {
			//Further filtering by those that can be requested by Authorized, depending on constraints
			RequestAuthorizedRoleService.LimitedToOrgUnits limitedToOrgUnits = requestAuthorizedRoleService.accessibleOrgUnits(requestingUser);
			RequestAuthorizedRoleService.LimitedToItSystems accessibleItsSystems = requestAuthorizedRoleService.accessibleItsSystems(requestingUser);
			userRoles = userRoles.filter(userRole -> {
				//Check those that can be requested by Authorized for constrained Orgunits or itsystems
				if (approverOptionService.getInheritedRequesterPermission(userRole).contains(RequestableBy.AUTHORIZED)) {
					//Check It system

					boolean constraintsMatch = accessibleItsSystems.type() == RequestAuthorizedRoleService.LimitedToType.ALL
						|| (accessibleItsSystems.type() == RequestAuthorizedRoleService.LimitedToType.CONSTRAINED
							&& accessibleItsSystems.itSystems().contains(userRole.getItSystem().getId()));


					for (OrgUnit orgUnit : orgUnitService.getByUserRole(userRole, false)) {
						//Only return true if authorized for ALL orgunits in the role

						if (limitedToOrgUnits.type() == RequestAuthorizedRoleService.LimitedToType.CONSTRAINED
							&& !limitedToOrgUnits.orgUnits().contains(orgUnit.getUuid())) {
							constraintsMatch = false;
							break;
						}
					}

					return constraintsMatch;
				}
				return true;
			});
		}

		return userRoles;
	}

	/***
	 * Helper method checking if a set of rights matches a request setting
	 * @param permission the RequestOption setting specifying who can request a given role
	 * @param isRequestingForSelf Is the user requesting the role also the reciever of the role?
	 * @param isManagerOrSubstitute is the user requesting the role a Manager or substitute manager?
	 * @param isAuthResponsible is the user requesting the role AuthResponsible?
	 * @return true if any of the rights matches the permissions, false otherwise
	 */
	private boolean isPermissionMatchingRights(RequestableBy permission, boolean isRequestingForSelf, boolean isManagerOrSubstitute, boolean isAuthResponsible, boolean isAdmin, boolean isRequestAuthorized) {
		return (isAdmin && permission.equals(RequestableBy.ADMIN))
			|| (isRequestAuthorized && permission.equals(RequestableBy.AUTHORIZED))
			|| (isRequestingForSelf && permission.equals(RequestableBy.EMPLOYEE))
			|| (isManagerOrSubstitute && permission.equals(RequestableBy.MANAGERORSUBSTITUTE))
			|| (isAuthResponsible && permission.equals(RequestableBy.AUTHRESPONSIBLE));
	}

	private boolean isPermissionMatchingRights(List<RequestableBy> permissions, boolean isRequestingForSelf, boolean isManagerOrSubstitute, boolean isAuthResponsible, boolean isAdmin, boolean isRequestAuthorized) {
		return permissions.stream().anyMatch(permission -> isPermissionMatchingRights(permission, isRequestingForSelf, isManagerOrSubstitute, isAuthResponsible, isAdmin, isRequestAuthorized));
	}

	public Optional<RoleRequest> getRoleRequestById(Long id) {
		return roleRequestDao.findById(id);
	}

	public void deleteRolerequest(Long rolerequestId) {
		roleRequestDao.deleteById(rolerequestId);
	}

	@RequestLoggable(logEvent = RequestLogEvent.REQUEST)
	public RoleRequest saveNewRequest(RoleRequest roleRequest) {
		return roleRequestDao.save(roleRequest);
	}

	/**
	 * Gets all pending request the currently logged in user has the rights to approve
	 *
	 * @return a set of pending requests which can be approved by the current user
	 */
	@Transactional(readOnly = true)
	public Set<RoleRequest> getPendingApprovableRequests() {
		final User user = userService.getOptionalByUserId(SecurityUtil.getUserId())
			.orElseThrow(() -> new NotFoundException("Unable to find user for user id: " + SecurityUtil.getUserId()));

		final Set<RoleRequest> allRequests = roleRequestDao.findByStatus(RequestApproveStatus.REQUESTED)
			.stream()
			.filter(request -> request.getRoleGroup() != null || (request.getUserRole() != null && !request.getUserRole().isReadOnly()))
			.collect(Collectors.toSet());
		if (SecurityUtil.isAdmin()) {
			// Admin can se all requests
			return allRequests;
		} else {
			// Gets all orgunits the user is manager or substitude in
			final Set<String> managerOrSubstitudeForOus = orgUnitService.getByManager().stream()
				.map(OrgUnit::getUuid)
				.collect(Collectors.toSet());
			final Set<String> authManagerForOus = orgUnitService.getActiveByAuthorizationManagerOrManagerMatchingUser(user)
				.stream()
				.map(OrgUnit::getUuid)
				.collect(Collectors.toSet());
			final RequestAuthorizedRoleService.LimitedToOrgUnits authorizedForOu =
				requestAuthorizedRoleService.accessibleOrgUnits(user);
			final RequestAuthorizedRoleService.LimitedToItSystems authorizedForItSystems =
				requestAuthorizedRoleService.accessibleItsSystems(user);

			// Look through all requests and determinate which are approvable by the current user
			return allRequests.stream().filter(request -> {
				final List<ApprovableBy> approvableBy = request.getApproverOption();
				boolean canApprove = false;
				if (approvableBy.contains(ApprovableBy.MANAGERORSUBSTITUTE))  {
					canApprove |= managerOrSubstitudeForOus.contains(request.getOrgUnit().getUuid());
				}
				if (approvableBy.contains(ApprovableBy.AUTHRESPONSIBLE)) {
					canApprove |= authManagerForOus.contains(request.getOrgUnit().getUuid());
				}
				if (approvableBy.contains(ApprovableBy.AUTHORIZED)) {
					boolean ouAccessible = (authorizedForOu.type() == RequestAuthorizedRoleService.LimitedToType.ALL)
						|| authorizedForOu.orgUnits().contains(request.getOrgUnit().getUuid());
					boolean itSystemAccessible = (authorizedForItSystems.type() == RequestAuthorizedRoleService.LimitedToType.ALL)
						|| (request.getUserRole() != null && authorizedForItSystems.itSystems().contains(request.getUserRole().getItSystem().getId()));
					canApprove |= (ouAccessible && itSystemAccessible);
				}
				if (approvableBy.contains(ApprovableBy.SYSTEMRESPONSIBLE) && request.getUserRole() != null) {
					final ItSystem itSystem = request.getUserRole().getItSystem();
					canApprove |= itSystem.getAttestationResponsible() != null &&
						itSystem.getAttestationResponsible().getUserId().equals(user.getUserId());
				}
				return canApprove;
			})
			.collect(Collectors.toSet());
		}

	}

	/**
	 * Gets all requests with the currently logged in user as Requester, grouped by RequestGroup
	 *
	 * @return a map with the group id as key. All ungrouped requests have the "ungrouped" key
	 */
	public Map<String, List<RoleRequest>> getRequestsForUserByGroup(User user) {
		return roleRequestDao.findByRequester_Uuid(user.getUuid()).stream()
			.collect(groupingBy(request -> request.getRequestGroupIdentifier() == null ? "ungrouped" : request.getRequestGroupIdentifier()));
	}

	/**
	 * Checks an orgunit against the constrained orgunits for the currently logged in user
	 *
	 * @param orgUnit orgunit being checkes
	 * @return true if allowed, false if not
	 */
	private boolean isAuthorizedForOrgUnit(final OrgUnit orgUnit, final User user) {
		final RequestAuthorizedRoleService.LimitedToOrgUnits limitedToOrgUnits = requestAuthorizedRoleService.accessibleOrgUnits(user);
		if (limitedToOrgUnits.type() == RequestAuthorizedRoleService.LimitedToType.ALL) {
			return true;
		} else if (limitedToOrgUnits.type() == RequestAuthorizedRoleService.LimitedToType.NONE) {
			return false;
		}
		final Set<String> constrainedOrgUnitsUuids = limitedToOrgUnits.orgUnits();
		return orgUnit == null || (constrainedOrgUnitsUuids != null && constrainedOrgUnitsUuids.contains(orgUnit.getUuid()));
	}

	/**
	 * Checks an it system against the constrained systems for the currently logged in user
	 *
	 * @param itSystem an it-system to check
	 * @return true if allowed, false if not
	 */
	private boolean isAuthorizedForITSystem(final ItSystem itSystem, final User user) {
		RequestAuthorizedRoleService.LimitedToItSystems accessibleItsSystems = requestAuthorizedRoleService.accessibleItsSystems(user);
		if (accessibleItsSystems.type() == RequestAuthorizedRoleService.LimitedToType.ALL) {
			return true;
		} else if (accessibleItsSystems.type() == RequestAuthorizedRoleService.LimitedToType.NONE) {
			return false;
		}
		return accessibleItsSystems.itSystems().contains(itSystem.getId());
	}

	/**
	 * Checks if a role assignment authorizes access to the target org unit.
	 * Returns true if the assignment is through ORGUNIT or TITLE and the assignment's
	 * org unit is equal to or a parent of the target org unit.
	 */
	private boolean isRoleAssignmentAuthorizedForOrgUnit(RoleAssignedToUserDTO assignment, OrgUnit targetOrgUnit) {
		// Check if assigned through ORGUNIT or TITLE
		if (assignment.getAssignedThrough() != AssignedThrough.ORGUNIT
			&& assignment.getAssignedThrough() != AssignedThrough.TITLE) {
			return false;
		}

		// Check if assignment has an org unit
		if (assignment.getOrgUnitUuid() == null) {
			return false;
		}

		OrgUnit assignmentOrgUnit = orgUnitService.getByUuid(assignment.getOrgUnitUuid());
		if (assignmentOrgUnit == null) {
			return false;
		}

		// Walk up from target orgUnit to see if we reach the assignment orgUnit
		return isOrgUnitInHierarchy(targetOrgUnit, assignmentOrgUnit);
	}

	/**
	 * Checks if targetOrgUnit is equal to or a descendant of ancestorOrgUnit
	 * by walking up the parent chain from targetOrgUnit.
	 */
	private boolean isOrgUnitInHierarchy(OrgUnit targetOrgUnit, OrgUnit ancestorOrgUnit) {
		OrgUnit current = targetOrgUnit;
		while (current != null) {
			if (current.getUuid().equals(ancestorOrgUnit.getUuid())) {
				return true;
			}
			current = current.getParent();
		}
		return false;
	}

	/**
	 * Approves a request and notifies the relevant users
	 *
	 * @param request the request being approved
	 * @return responseentity
	 */
	public ResponseEntity<String> approveRequest(RoleRequest request) {
		if (!settingsService.isRequestApproveEnabled()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		User receiver = request.getReceiver();
		if (receiver == null) {
			return new ResponseEntity<>("Der er ikke valgt en modtager af rollen", HttpStatus.BAD_REQUEST);
		}

		if (receiver.isDeleted()) {
			return new ResponseEntity<>("Der er valgt en inaktiv modtager af rollen", HttpStatus.BAD_REQUEST);
		}

		boolean manualItSystem = false;
		String roleName = "";
		if (request.getUserRole() != null) {
			//userrole
			UserRole userRole = request.getUserRole();
			roleName = userRole.getName();
			if (Objects.equals(request.getRequestAction(), RequestAction.REMOVE)) {
				userService.removeUserRole(receiver, userRole);
			} else {
				userService.addUserRole(receiver, userRole, request.getStartDate(), request.getEndDate(), mapRequestPostponedConstraint(request.getRequestPostponedConstraints()), request.getOrgUnit(), true, null);
				manualItSystem = userRole.getItSystem().getSystemType().equals(ItSystemType.MANUAL);
			}


		} else if (request.getRoleGroup() != null) {
			//Rolegroup
			RoleGroup roleGroup = request.getRoleGroup();
			if (Objects.equals(request.getRequestAction(), RequestAction.REMOVE)) {
				userService.removeRoleGroup(receiver, roleGroup);
			} else {
				userService.addRoleGroup(receiver, roleGroup, null, null, request.getOrgUnit());
			}
			roleName = roleGroup.getName();

			for (RoleGroupUserRoleAssignment userRoleAssignment : roleGroup.getUserRoleAssignments()) {
				if (userRoleAssignment.getUserRole().getItSystem().getSystemType().equals(ItSystemType.MANUAL)) {
					manualItSystem = true;
					break;
				}
			}

		} else {
			//No userrole or rolegroup
			return new ResponseEntity<>("No role attached to request", HttpStatus.BAD_REQUEST);
		}

		boolean receiverNotified = requestNotifierService.notifyReceiverOnRequestApproval(receiver, request.getRequestAction(), roleName, manualItSystem, request.getStartDate(), request.getEndDate(), request.getReason());
		boolean managerNotified = requestNotifierService.notifyManagerOnRequestapproval(request, manualItSystem, roleName, request.getReason());

		request.setStatus(RequestApproveStatus.ASSIGNED);

		return new ResponseEntity<>(HttpStatus.OK);

	}

	/**
	 * Maps the requestconstraints to regular PostponedConstraints
	 *
	 * @param constraints list of RequestPostponedConstraints
	 * @return list of PostponedConstraints
	 */
	private List<PostponedConstraint> mapRequestPostponedConstraint(List<RequestPostponedConstraint> constraints) {
		List<PostponedConstraint> postponedConstraintsForAssignment = new ArrayList<>();
		for (RequestPostponedConstraint requestConstraint : constraints) {
			SystemRole systemRole = requestConstraint.getSystemRole();
			ConstraintType constraintType = requestConstraint.getConstraintType();
			if (systemRole == null || constraintType == null) {
				continue;
			}

			PostponedConstraint postponedConstraint = new PostponedConstraint();
			postponedConstraint.setConstraintType(constraintType);
			postponedConstraint.setSystemRole(systemRole);
			postponedConstraint.setValue(requestConstraint.getValue());

			postponedConstraintsForAssignment.add(postponedConstraint);
		}
		return postponedConstraintsForAssignment;
	}

	/**
	 * Rejects a pending request and notifies relevant users
	 *
	 * @param request the pending request
	 * @param reason the reason the request is rejected
	 * @return responseentity
	 */
	public ResponseEntity<String> rejectRequest(RoleRequest request, String reason) {
		if (!settingsService.isRequestApproveEnabled()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (!settingsService.isRequestApproveEnabled()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		request.setStatus(RequestApproveStatus.REJECTED);
        request.setRejectReason(reason);
		request = roleRequestDao.save(request);

		String roleName = "";
		if (request.getUserRole() != null) {
			roleName = request.getUserRole().getName();
		} else if (request.getRoleGroup() != null) {
			roleName = request.getRoleGroup().getName();
		} else {
			log.error("Unknown roleType:");
		}

		// notifying manager and authorizationManager
		requestNotifierService.notifyManagerOnRejectedRequest(request, request.getOrgUnit(), roleName, request.getRejectReason());
		return new ResponseEntity<>(HttpStatus.OK);
	}

	public boolean canRequestFor(User requester, User receiver) {
		if (SecurityUtil.isAdmin()) {
			log.info("Admin can request for anybody");
			return true;
		}

		//User is allowed to request for self...?
		if (requester.equals(receiver)) {
			return true;
		}

		// bemyndiget
		if (SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED)) {
			return true;
		}

		//Is requester manager or substitute for receiver?
		boolean isManagerOrSubstituteFor = userService.getManager(receiver).stream()
			.anyMatch(manager -> manager.getUuid().equals(requester.getUuid()) ||
				manager.getManagerSubstitutes().stream()
					.anyMatch(managerSubstitute ->
						managerSubstitute.getManager().getUuid().equals(requester.getUuid())
						|| managerSubstitute.getSubstitute().getUuid().equals(requester.getUuid())));
		if (isManagerOrSubstituteFor) {
			return true;
		} else {
			log.info("{} is not manager for {}", requester.getUuid(), receiver.getUuid());
		}

		//is requester authmanager in an orgunit with the receiver?
		boolean isAuthorizationManagerFor = orgUnitService.getOrgUnitsForUser(receiver).stream()
			.anyMatch(orgUnit -> orgUnit.getAuthorizationManagers().stream()
				.anyMatch(authorizationManager -> authorizationManager.getUser().getUuid().equals(requester.getUuid())));
		if (!isAuthorizationManagerFor) {
			log.info("{} is not authorized for {}", requester.getUuid(), receiver.getUuid());
		}
		// autorisationsansvarlig
		return isAuthorizationManagerFor;
	}

	public List<UserRole> getAllAutomaticUserRoles() {
		List<UserRole> result = new ArrayList<>();
		List<ApprovableBy> approverOptions = new ArrayList<>();
		approverOptions.add(ApprovableBy.AUTOMATIC);
		approverOptions.add(ApprovableBy.INHERIT);
		for (UserRole userRole : userRoleService.getUserRolesWithApproverPermissions(approverOptions)) {
			if (userRole.getApproverPermission().contains(ApprovableBy.AUTOMATIC)) {
				result.add(userRole);
			} else if (userRole.getApproverPermission().contains(ApprovableBy.INHERIT)) {
				if (userRole.getItSystem().getApproverPermission() != null && userRole.getItSystem().getApproverPermission().contains(ApprovableBy.AUTOMATIC)) {
					result.add(userRole);
				} else if (userRole.getItSystem().getApproverPermission() != null && userRole.getItSystem().getApproverPermission().contains(ApprovableBy.INHERIT)) {
					if (settingsService.getRolerequestApprover().contains(ApprovableBy.AUTOMATIC)) {
						result.add(userRole);
					}
				}
			}
		}
		return result;
	}

	public List<RoleGroup> getAllAutomaticRoleGroups() {
		List<RoleGroup> result = new ArrayList<>();
		List<ApprovableBy> approverOptions = new ArrayList<>();
		approverOptions.add(ApprovableBy.AUTOMATIC);
		approverOptions.add(ApprovableBy.INHERIT);
		for (RoleGroup roleGroup : roleGroupService.getRoleGroupsWithApproverPermissions(approverOptions)) {
			if (roleGroup.getApproverPermission().contains(ApprovableBy.AUTOMATIC)) {
				result.add(roleGroup);
			} else if (roleGroup.getApproverPermission().contains(ApprovableBy.INHERIT)) {
				if (settingsService.getRolerequestApprover().contains(ApprovableBy.AUTOMATIC)) {
					result.add(roleGroup);
				}
			}
		}
		return result;
	}

	public List<RoleRequest> getPendingForReceiver(User user) {
		return roleRequestDao.findByReceiver_UuidAndStatus(user.getUuid(), RequestApproveStatus.REQUESTED);
	}

	/**
	 * Determines the set of {@link RequestableBy} permissions applicable for a specific request scenario.
	 * <p>
	 * This method evaluates the relationship between the {@code requestingUser} and the {@code receivingUser},
	 * as well as the {@code requestingUser}'s global roles (e.g., Admin, Manager, AuthResponsible),
	 * to generate a list of all request types they are permitted to perform.
	 */
	private List<RequestableBy> getPermittedSettings(User requestingUser, User receivingUser) {
		boolean isAdmin = SecurityUtil.isAdmin();
		boolean isRequestingForSelf = requestingUser.equals(receivingUser); //anmodende bruger er brugeren som er logget ind
		boolean isManagerOrSubstitute = userService.isManagerOrSubstituteManagerFor(requestingUser, receivingUser);

		boolean isAuthResponsible = !orgUnitService.getByAuthorizationManagerMatchingUser(requestingUser).isEmpty(); // autorisationsansvarlig
		boolean isRequestAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED); // bemyndiget
		List<RequestableBy> permittedSettings = new ArrayList<>();
		for (RequestableBy permission : RequestableBy.values()) {
			if (isPermissionMatchingRights(permission, isRequestingForSelf, isManagerOrSubstitute, isAuthResponsible, isAdmin, isRequestAuthorized)) {
				permittedSettings.add(permission);
			}
		}
		return permittedSettings;
	}


	/**
	 * Builds a specification containing constraints based on the requesting user's authorized access levels.
	 * <p>
	 * This method retrieves the specific Organizational Units and IT Systems the {@code requestingUser} is authorized
	 * to access via {@link RequestAuthorizedRoleService}. It constructs a {@link Specification} that filters
	 * {@link UserRoleView} entities to ensure only allowed roles are returned.
	 * <ul>
	 *     <li>If access type is {@code NONE}, a "not authorized" specification is returned.</li>
	 *     <li>If access to OrgUnits is {@code CONSTRAINED}, the specification limits results to the allowed OrgUnits.</li>
	 *     <li>If access to IT Systems is {@code CONSTRAINED}, the specification limits results to the allowed IT Systems.</li>
	 * </ul>
	 */
	private Specification<UserRoleView> buildAuthorizedConstraints(final User requestingUser, final OrgUnit receiversOrgUnit) {
		final RequestAuthorizedRoleService.LimitedToOrgUnits accessibleOrgUnits = requestAuthorizedRoleService.accessibleOrgUnits(requestingUser);
		final RequestAuthorizedRoleService.LimitedToItSystems accessibleItSystems = requestAuthorizedRoleService.accessibleItsSystems(requestingUser);
		SpecificationBuilder<UserRoleView> constraints = SpecificationBuilder.create(UserRoleView.class);
		// Hvis bruger ikke har adgang til noget, returner spec der aldrig matcher
		constraints.and(UserRoleViewDatatableDao.requesterPermissionIn(Collections.singletonList(RequestableBy.AUTHORIZED)));
		if (accessibleOrgUnits.type() == RequestAuthorizedRoleService.LimitedToType.CONSTRAINED) {
			constraints = constraints.and(
				UserRoleViewDatatableDao.authorizedRolesLimitedToOrgUnits(receiversOrgUnit.getUuid(), accessibleOrgUnits.orgUnits())
			);
		}
		if (accessibleItSystems.type() == RequestAuthorizedRoleService.LimitedToType.CONSTRAINED) {
			constraints = constraints.and(
				UserRoleViewDatatableDao.authorizedRolesLimitedToItSystems(accessibleItSystems.itSystems())
			);
		}
		return constraints.build();
	}

	/**
	 * Retrieves a paginated and filtered list of user roles that the requesting user is allowed to request for the receiving user.
	 * <p>
	 * This method determines the applicable permissions using {@link #getPermittedSettings(User, User)}.
	 * If the user has {@code AUTHORIZED} permission, additional constraints regarding accessible OrgUnits and IT Systems
	 * are applied via {@link #buildAuthorizedConstraints(User, OrgUnit)}.
	 * <p>
	 * The result is formatted for use with DataTables.
	 */
	public DataTablesOutput<UserRoleView> getRequestableUserRolesAsDatatable(final DataTablesInput input, final User requestingUser, final User receivingUser, final OrgUnit orgUnit) {
		final List<RequestableBy> permittedSettings = getPermittedSettings(requestingUser, receivingUser);

		// No permissions = no results
		if (permittedSettings.isEmpty()) {
			return userRoleDatatableDao.findAll(input, (Specification<UserRoleView>) (root, query, cb) -> cb.disjunction());
		}

		boolean hasAuthorized = permittedSettings.remove(RequestableBy.AUTHORIZED);

		// Build list of all ancestor orgUnit UUIDs (including the orgUnit itself)
		List<String> ancestorOuUuids = new ArrayList<>();
		OrgUnit current = orgUnit;
		while (current != null) {
			ancestorOuUuids.add(current.getUuid());
			current = current.getParent();
		}

		final var spec = SpecificationBuilder.create(UserRoleView.class)
			.and(UserRoleViewDatatableDao.isNotReadOnly())
			.and(
				UserRoleViewDatatableDao.orgUnitFilterMatchesOrEmpty(ancestorOuUuids)
			)
			.andOrGroup(group -> {
				group.orIf(hasAuthorized, buildAuthorizedConstraints(requestingUser, orgUnit));
				group.orIf(!permittedSettings.isEmpty(), UserRoleViewDatatableDao.requesterPermissionIn(permittedSettings));
			})
			.build();
		return userRoleDatatableDao.findAll(input, spec);
	}

	public DataTablesOutput<CombinedRoleView> getRequestableCombinedRolesAsDatatable(
		final DataTablesInput input,
		final User requestingUser,
		final User receivingUser,
		final OrgUnit orgUnit,
		final boolean hideAlreadyAssigned) {

		final List<RequestableBy> permittedSettings = getPermittedSettings(requestingUser, receivingUser);

		if (permittedSettings.isEmpty()) {
			DataTablesOutput<CombinedRoleView> emptyOutput = new DataTablesOutput<>();
			emptyOutput.setDraw(input.getDraw());
			emptyOutput.setRecordsTotal(0L);
			emptyOutput.setRecordsFiltered(0L);
			emptyOutput.setData(Collections.emptyList());
			return emptyOutput;
		}

		boolean hasAuthorized = permittedSettings.contains(RequestableBy.AUTHORIZED);

		List<Long> assignedUserRoleIds = userService.getAllUserRolesAssignedToUser(receivingUser, null).stream()
			.map(UserRoleAssignedToUser::getRoleId)
			.toList();

		List<Long> assignedRoleGroupIds = userService.getAllRoleGroupsAssignedToUser(receivingUser).stream()
			.map(roleGroupAssignedToUser -> roleGroupAssignedToUser.getRoleGroup().getId())
			.toList();

		// Build list of all ancestor orgUnit UUIDs (including the orgUnit itself)
		List<String> ancestorOuUuids = new ArrayList<>();
		OrgUnit current = orgUnit;
		while (current != null) {
			ancestorOuUuids.add(current.getUuid());
			current = current.getParent();
		}

		Set<Long> userRoleIdsInRoleGroups = calculateUserRoleIdsInRoleGroups(
			requestingUser, orgUnit, ancestorOuUuids, hideAlreadyAssigned,
			permittedSettings, hasAuthorized, assignedUserRoleIds, assignedRoleGroupIds
		);

		final var spec = SpecificationBuilder.create(CombinedRoleView.class)
			.and(CombinedRoleViewDatatableDao.isNotReadOnly())
			.and(CombinedRoleViewDatatableDao.orgUnitFilterMatchesOrEmpty(ancestorOuUuids))
			.and(CombinedRoleViewDatatableDao.excludeUserRolesById(userRoleIdsInRoleGroups))
			.andIf(hasAuthorized, buildAuthorizedConstraintsForCombined(requestingUser, orgUnit))
			.andIf(!permittedSettings.isEmpty(), CombinedRoleViewDatatableDao.requesterPermissionIn(permittedSettings))
			.andIf(hideAlreadyAssigned, CombinedRoleViewDatatableDao.excludeAlreadyAssigned(assignedUserRoleIds, assignedRoleGroupIds))
			.build();

		return combinedRoleViewDao.findAll(input, spec);
	}

	private Specification<CombinedRoleView> buildAuthorizedConstraintsForCombined(User requestingUser, OrgUnit orgUnit) {
		return (root, query, cb) -> {
			List<RoleAssignedToUserDTO> userAssignments = userService.getAllUserRoleAndRoleGroupAssignments(requestingUser);

			List<Long> authorizedUserRoleIds = userAssignments.stream()
				.filter(a -> a.getType() == RoleAssignmentType.USERROLE)
				.filter(a -> isRoleAssignmentAuthorizedForOrgUnit(a, orgUnit))
				.map(RoleAssignedToUserDTO::getRoleId)
				.distinct()
				.toList();

			List<Long> authorizedRoleGroupIds = userAssignments.stream()
				.filter(a -> a.getType() == RoleAssignmentType.ROLEGROUP)
				.filter(a -> isRoleAssignmentAuthorizedForOrgUnit(a, orgUnit))
				.map(RoleAssignedToUserDTO::getRoleId)
				.distinct()
				.toList();

			// Build predicate: (type = 'userRole' AND id IN userRoleIds) OR (type = 'roleGroup' AND id IN roleGroupIds)
			Predicate userRolePredicate = cb.and(
				cb.equal(root.get("type"), "userRole"),
				root.get("id").in(authorizedUserRoleIds.isEmpty() ? java.util.Collections.singletonList(-1L) : authorizedUserRoleIds)
			);

			Predicate roleGroupPredicate = cb.and(
				cb.equal(root.get("type"), "roleGroup"),
				root.get("id").in(authorizedRoleGroupIds.isEmpty() ? java.util.Collections.singletonList(-1L) : authorizedRoleGroupIds)
			);

			return cb.or(userRolePredicate, roleGroupPredicate);
		};
	}

	public List<RoleGroup> getRequestableRoleGroupsAsDatatable(final User requestingUser, final User receivingUser, final OrgUnit orgUnit) {
		List<RequestableBy> permittedSettings = getPermittedSettings(requestingUser, receivingUser);
		List<RequestableBy> globalPermission = settingsService.getRolerequestRequester();
		boolean isRequestAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED);

		// Add INHERIT if global permission allows it
		if (isPermissionMatchingRights(globalPermission,
			requestingUser.equals(receivingUser),
			!userService.getSubstitutesManager(requestingUser).isEmpty() || userService.isManager(requestingUser),
			!orgUnitService.getByAuthorizationManagerMatchingUser(requestingUser).isEmpty(),
			SecurityUtil.isAdmin(),
			isRequestAuthorized)) {
			permittedSettings = new ArrayList<>(permittedSettings);
			permittedSettings.add(RequestableBy.INHERIT);
		}

		Set<Long> accessibleItSystemIds = null;
		// Check AUTHORIZED constraints and remove AUTHORIZED from permissions if constraints not met
		if (isRequestAuthorized && !SecurityUtil.isAdmin() && permittedSettings.contains(RequestableBy.AUTHORIZED)) {
			final RequestAuthorizedRoleService.LimitedToOrgUnits accessibleOrgUnits = requestAuthorizedRoleService.accessibleOrgUnits(requestingUser);
			final RequestAuthorizedRoleService.LimitedToItSystems accessibleItSystems = requestAuthorizedRoleService.accessibleItsSystems(requestingUser);

			// Check if requesting user has AUTHORIZED access to the receiving user's orgUnit
			boolean hasOrgUnitAccess = accessibleOrgUnits.type() == RequestAuthorizedRoleService.LimitedToType.ALL
				|| (orgUnit != null && accessibleOrgUnits.orgUnits().contains(orgUnit.getUuid()));

			// If user doesn't meet requirements for AUTHORIZED, remove it from permitted settings
			if (!hasOrgUnitAccess) {
				permittedSettings = permittedSettings.stream()
					.filter(setting -> setting != RequestableBy.AUTHORIZED)
					.collect(Collectors.toList());
			} else if (accessibleItSystems.type() == RequestAuthorizedRoleService.LimitedToType.CONSTRAINED) {
				// Store accessible IT system IDs for filtering
				accessibleItSystemIds = accessibleItSystems.itSystems();
			}
		}

		// Build base specification - fetch all role groups matching user's (possibly filtered) permissions
		Specification<RoleGroup> specification = RolegroupDatatableDao.requesterPermissionIn(permittedSettings);

		List<RoleGroup> roleGroups = rolegroupDatatableDao.findAll(specification);

		// Filter role groups based on accessible IT systems if LIMITED access
		if (accessibleItSystemIds != null) {
			final Set<Long> finalAccessibleItSystemIds = accessibleItSystemIds;
			roleGroups = roleGroups.stream()
				.filter(roleGroup -> roleGroup.getUserRoleAssignments() != null
					&& !roleGroup.getUserRoleAssignments().isEmpty()
					&& roleGroup.getUserRoleAssignments().stream()
					.allMatch(assignment -> assignment.getUserRole() != null
						&& assignment.getUserRole().getItSystem() != null
						&& finalAccessibleItSystemIds.contains(assignment.getUserRole().getItSystem().getId())))
				.collect(Collectors.toList());
		}

		return roleGroups;
	}

	public DataTablesOutput<User> getRequestForUsersAsDatatable(DataTablesInput input, User requestingUser) {
		Set<OrgUnit> orgUnits = new HashSet<>(orgUnitService.getActiveByAuthorizationManagerOrManagerMatchingUser(requestingUser));

		// Now add all the users the user have the authorization role for.
		boolean limitedToOUs = true; // Admins are never limited
		if (requestAuthorizedRoleService.requestAuthorizedRoleCanRequest() && !SecurityUtil.isAdmin()) {
			final RequestAuthorizedRoleService.LimitedToOrgUnits limitedToOrgUnits =
				requestAuthorizedRoleService.accessibleOrgUnits(requestingUser);
			log.info("LimitedToOrgUnits: {} {}", limitedToOrgUnits.type().name(), String.join(",", limitedToOrgUnits.orgUnits()));
			if (limitedToOrgUnits.type() == RequestAuthorizedRoleService.LimitedToType.ALL) {
				limitedToOUs = false;
			} else {
				orgUnits.addAll(limitedToOrgUnits.orgUnits().stream()
					.map(orgUnitService::getByUuid)
					.filter(Objects::nonNull)
					.toList());
			}
		}
		if (SecurityUtil.isAdmin()) {
			limitedToOUs = false;
		}

		DataTablesOutput<User> outputDatatable;
		if (limitedToOUs) {
			outputDatatable = userDatatableDao.findAll(input, Specification
				.where(UserDatatableDao.requesterPositionOrgUnitIn(orgUnits)));
		} else {
			outputDatatable = userDatatableDao.findAll(input, Specification
				.where(UserDatatableDao.notDeletedOrDisabled()));
		}

		Stream<User> users = outputDatatable.getData().stream();

		outputDatatable.setData(users.toList());
		return outputDatatable;
	}

	public long getRequestCount() {
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		return getRequestableUserRoles(loggedInUser).count();
	}

	public static <T> DataTablesOutput<T> toDatatablesOutput(DataTablesOutput<?> originalOutput, final List<T> data) {
		DataTablesOutput<T> output = new DataTablesOutput<>();
		output.setRecordsFiltered(originalOutput.getRecordsFiltered());
		output.setError(originalOutput.getError());
		output.setSearchPanes(originalOutput.getSearchPanes());
		output.setDraw(originalOutput.getDraw());
		output.setRecordsTotal(originalOutput.getRecordsTotal());
		output.setData(data);
		return output;
	}

	@Transactional
	public void setCount(HttpServletRequest request) {
		long count = getRequestCount();
		if (count > 0) {
			request.getSession().setAttribute(SessionConstants.SESSION_REQUEST_COUNT, count);
		}
	}

	@Transactional
	public void deleteOld() {
		LocalDateTime cutOffDate = LocalDateTime.now().minusMonths(13);
		List<RequestApproveStatus> stati = new ArrayList<>();
		stati.add(RequestApproveStatus.ASSIGNED);
		stati.add(RequestApproveStatus.REJECTED);

		roleRequestDao.deleteByStatusInAndStatusTimestampBefore(stati, Date.from(cutOffDate.atZone(ZoneId.systemDefault()).toInstant()));
	}

	@Cacheable(value = CACHE_PREFIX + "isManagerAnywhere", key="#user.uuid")
	public boolean isManagerAnywhere(final User user) {
		return !userService.getSubstitutesManager(user).isEmpty() || !orgUnitService.getByAuthorizationManagerMatchingUser(user).isEmpty() || !orgUnitService.getByManagerMatchingUser(user).isEmpty();
	}

	@Cacheable(value = CACHE_PREFIX + "isSystemResponsibleAnywhere", key="#user.uuid")
	public boolean isSystemResponsibleAnywhere(User user) {
		return itSystemService.systemResponsibleCount(user) > 0;
	}

	public boolean isRequestAuthorizedAnywhere () {
		return  SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED);
	}

	@Cacheable(value = CACHE_PREFIX + "isAuthorizationResponsibleAnywhere", key="#user.uuid")
	public boolean isAuthorizationResponsibleAnywhere(User user) {
		return !orgUnitService.getByAuthorizationManagerMatchingUser(user).isEmpty();
	}

	public List<RoleRequest> getAll() {
		return roleRequestDao.findAll();
	}

	private Set<Long> calculateUserRoleIdsInRoleGroups(
		User requestingUser,
		OrgUnit orgUnit,
		List<String> ancestorOuUuids,
		boolean hideAlreadyAssigned,
		List<RequestableBy> permittedSettings,
		boolean hasAuthorized,
		List<Long> assignedUserRoleIds,
		List<Long> assignedRoleGroupIds) {

		final var roleGroupSpec = SpecificationBuilder.create(CombinedRoleView.class)
			.and(CombinedRoleViewDatatableDao.isNotReadOnly())
			.and(CombinedRoleViewDatatableDao.orgUnitFilterMatchesOrEmpty(ancestorOuUuids))
			.and((root, query, cb) -> cb.equal(root.get("type"), "roleGroup")) // Only roleGroups
			.andIf(hasAuthorized, buildAuthorizedConstraintsForCombined(requestingUser, orgUnit))
			.andIf(!permittedSettings.isEmpty(), CombinedRoleViewDatatableDao.requesterPermissionIn(permittedSettings))
			.andIf(hideAlreadyAssigned, CombinedRoleViewDatatableDao.excludeAlreadyAssigned(assignedUserRoleIds, assignedRoleGroupIds))
			.build();

		List<Long> roleGroupIds = combinedRoleViewDao.findAll(roleGroupSpec).stream()
			.map(CombinedRoleView::getId)
			.toList();

		if (roleGroupIds.isEmpty()) {
			return Collections.emptySet();
		}

		return roleGroupIds.stream()
			.map(roleGroupService::getById)
			.filter(Objects::nonNull)
			.filter(roleGroup -> roleGroup.getUserRoleAssignments() != null)
			.flatMap(roleGroup -> roleGroup.getUserRoleAssignments().stream())
			.map(assignment -> assignment.getUserRole().getId())
			.collect(Collectors.toSet());
	}
}
