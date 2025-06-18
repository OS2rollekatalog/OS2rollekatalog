package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.SessionConstants;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.RolegroupDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserRoleDatatableDao;
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
import dk.digitalidentity.rc.rolerequest.log.RequestLogEvent;
import dk.digitalidentity.rc.rolerequest.log.RequestLoggable;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestPostponedConstraint;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.ApproverOption;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequesterOption;
import dk.digitalidentity.rc.security.AccessConstraintService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dk.digitalidentity.rc.rolerequest.RequestConstants.CACHE_PREFIX;
import static java.util.stream.Collectors.groupingBy;
import static org.hibernate.internal.util.collections.CollectionHelper.listOf;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestService {
	private final UserRoleDatatableDao userRoleDatatableDao;
	private final RoleRequestDao roleRequestDao;
	private final UserService userService;
	private final OrgUnitService orgUnitService;
	private final SettingsService settingsService;
	private final UserRoleService userRoleService;
	private final RoleGroupService roleGroupService;
	private final AccessConstraintService accessConstraintService;
	private final RolegroupDatatableDao rolegroupDatatableDao;
	private final ItSystemService itSystemService;
	private final RequestNotifierService requestNotifierService;
	private final ApproverOptionService approverOptionService;
	private final RequestAuthorizedRoleService requestAuthorizedRoleService;
	private final UserDatatableDao userDatatableDao;

	public boolean hasPendingRemovalRequestForUserrole(Long userRoleId, String recieverUuid) {
		return !roleRequestDao.findByRequestActionAndReceiver_UuidAndUserRole_Id(RequestAction.REMOVE, recieverUuid, userRoleId).isEmpty();
	}

	public boolean hasPendingRemovalRequestForRolegroup(Long roleGroupId, String recieverUuid) {
		return !roleRequestDao.findByRequestActionAndReceiver_UuidAndRoleGroup_Id(RequestAction.REMOVE, recieverUuid, roleGroupId).isEmpty();
	}

	/**
	 * Determines if a role can be requested for a user
	 *
	 * @param recievingUser the user for which the role is being requested
	 * @param role          the UserRole being requested
	 * @return true if user can request the role, false otherwise
	 */
	public boolean canRequest(UserRole role, User recievingUser, RequesterOption globalRequesterSetting, boolean isRequestAuthorized) {

		RequesterOption relevantPermission = role.getRequesterPermission();

		ItSystem itSystem = role.getItSystem();
		//Check ITSystem permission if role is set to inherit
		if (relevantPermission.equals(RequesterOption.INHERIT)) {
			relevantPermission = itSystem.getRequesterPermission();
		}

		//Check global permissions if ITSystem and role is set to inherit
		if (relevantPermission == RequesterOption.INHERIT || relevantPermission == null) {
			relevantPermission = globalRequesterSetting;
		}

		//If permission is still null or inherit at this point, something is wrong
		if (relevantPermission == null || relevantPermission == RequesterOption.INHERIT) {
			throw new IllegalArgumentException("Global settings for requester should never be null.");
		}

		if (SecurityUtil.isAdmin() && relevantPermission.getRequestPermissions().contains(RequestableBy.ADMIN)) {
			return true;
		}

		//Check is user is Authorized and constraints allow approval of authorized

		if (isRequestAuthorized && relevantPermission.getRequestPermissions().contains(RequestableBy.AUTHORIZED)) {
			boolean constraintsMatch = isAuthorizedForITSystem(itSystem);

			for (OrgUnit orgUnit : orgUnitService.getByUserRole(role, false)) {
				//Only return true if authorized for ALL orgunits in the role
				if (!isAuthorizedForOrgUnit(orgUnit)) {
					constraintsMatch = false;
					break;
				}
			}

			//only return true if both orgunits and IT-systems match the constraints
			if (constraintsMatch) {
				return true;
			}
		}

		return determineRequestable(relevantPermission.getRequestPermissions(), recievingUser);
	}

	/**
	 * Determines if a rolegroup can be requested for a user
	 *
	 * @param recievingUser the user for which the role is being requested
	 * @param role          the RoleGroup being requested
	 * @return true if request is allowed, false otherwise
	 */
	public boolean canRequest(RoleGroup role, User recievingUser) {

		RequesterOption relevantPermission = role.getRequesterPermission();

		//Check global permissions if role is set to inherit
		if (relevantPermission == RequesterOption.INHERIT || relevantPermission == null) {
			relevantPermission = settingsService.getRolerequestRequester();
		}

		//If permission is still null or inherit at this point, something is wrong
		if (relevantPermission == null || relevantPermission == RequesterOption.INHERIT) {
			throw new IllegalArgumentException("Global settings for requester should never be null.");
		}

		if (SecurityUtil.isAdmin() && relevantPermission.getRequestPermissions().contains(RequestableBy.ADMIN)) {
			return true;
		}

		//Check is user is Authorized and constraints allow approval of authorized
		boolean isAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED); // bemyndiget
		if (isAuthorized && relevantPermission.getRequestPermissions().contains(RequestableBy.AUTHORIZED)) {
			boolean constraintsMatch = true;

			for (OrgUnit orgUnit : orgUnitService.getByRoleGroup(role, false)) {
				//Only return true if authorized for ALL orgunits in the role
				if (!isAuthorizedForOrgUnit(orgUnit)) {
					constraintsMatch = false;
					break;
				}
			}

			return constraintsMatch;
		}

		return determineRequestable(relevantPermission.getRequestPermissions(), recievingUser);
	}

	public boolean canApprove(RoleRequest request) {
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (SecurityUtil.isAdmin()) {
			return true;
		}
		if (loggedInUser.getUuid().equals(request.getRequester().getUuid())) {
			return false;
		}
		//Check if user can approve this request
		if (request.getUserRole() == null) {
			return canApprove(request.getRoleGroup(), loggedInUser);
		} else {
			return canApprove(request.getUserRole(), loggedInUser);
		}
	}

	private boolean canApprove(UserRole role, User approvingUser) {

		if (SecurityUtil.isAdmin()) {
			return true;
		}

		ApproverOption relevantPermission = role.getApproverPermission();

		ItSystem itSystem = role.getItSystem();
		//Check ITSystem permission if role is set to inherit
		if (relevantPermission.equals(ApproverOption.INHERIT)) {
			relevantPermission = itSystem.getApproverPermission();
		}

		//Check global permissions if ITSystem and role is set to inherit
		if (relevantPermission == ApproverOption.INHERIT || relevantPermission == null) {
			relevantPermission = settingsService.getRolerequestApprover();
		}

		//If permission is still null or inherit at this point, something is wrong
		if (relevantPermission == null || relevantPermission == ApproverOption.INHERIT) {
			throw new IllegalArgumentException("Global settings for approver should never be null.");
		}

		//if approval is automatic return true
		if (relevantPermission.getApproverPermissions().contains(ApprovableBy.AUTOMATIC)) {
			return true;
		}

		//if setting is system responsible, return true
		if (relevantPermission.getApproverPermissions().contains(ApprovableBy.SYSTEMRESPONSIBLE) && itSystem.getAttestationResponsible() == approvingUser) {
			return true;
		}

		//Check is user is Authorized and constraints allow approval of authorized
		boolean isAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED); // bemyndiget
		if (isAuthorized && relevantPermission.getApproverPermissions().contains(ApprovableBy.AUTHORIZED)) {
			boolean constraintsMatch = isAuthorizedForITSystem(itSystem);

			for (OrgUnit orgUnit : orgUnitService.getByUserRole(role, false)) {
				//Only return true if authorized for ALL orgunits in the role
				if (!isAuthorizedForOrgUnit(orgUnit)) {
					constraintsMatch = false;
					break;
				}
			}

			//only return true if both orgunits and IT-systems match the constraints
			if (constraintsMatch) {
				return true;
			}
		}

		return determineApprovable(relevantPermission.getApproverPermissions(), approvingUser);
	}

	private boolean canApprove(RoleGroup role, User approvingUser) {
		if (SecurityUtil.isAdmin()) {
			return true;
		}

		ApproverOption relevantPermission = role.getApproverPermission();

		//Check global permissions if ITSystem and role is set to inherit
		if (relevantPermission == ApproverOption.INHERIT || relevantPermission == null) {
			relevantPermission = settingsService.getRolerequestApprover();
		}

		//If permission is still null or inherit at this point, something is wrong
		if (relevantPermission == null || relevantPermission == ApproverOption.INHERIT) {
			throw new IllegalArgumentException("Global settings for approver should never be null.");
		}

		//if approval is automatic return true
		if (relevantPermission.getApproverPermissions().contains(ApprovableBy.AUTOMATIC)) {
			return true;
		}

		//Check is user is Authorized and constraints allow approval of authorized
		boolean isAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED); // bemyndiget
		if (isAuthorized && relevantPermission.getApproverPermissions().contains(ApprovableBy.AUTHORIZED)) {
			return true;
		}

		return determineApprovable(relevantPermission.getApproverPermissions(), approvingUser);
	}

	/**
	 * Matches a role request permission with a user to determine if the user is allowed to request
	 *
	 * @param permissions   A list of RequestableBy values representing permissions for a role
	 * @param recievingUser the user for which the role is being requested
	 * @return true if request is allowed, false otherwise
	 */
	private boolean determineRequestable(List<RequestableBy> permissions, User recievingUser) {
		User requestingUser = userService.getOptionalByUserId(SecurityUtil.getUserId()).orElseThrow(() -> new NotFoundException("Unable to find user for user id: " + SecurityUtil.getUserId()));

		boolean isRequestingForSelf = requestingUser.equals(recievingUser); //anmodende bruger er brugeren som er logget ind
		if (isRequestingForSelf && permissions.contains(RequestableBy.EMPLOYEE)) {
			return true;
		}

		boolean isManagerOrSubstitute = !userService.getSubstitutesManager(requestingUser).isEmpty() || userService.isManager(requestingUser); // leder eller stedfortræder
		//Shortcircuit, if possible
		if (isManagerOrSubstitute && permissions.contains(RequestableBy.MANAGERORSUBSTITUTE)) {
			return true;
		}

		boolean isAuthResponsible = !orgUnitService.getByAuthorizationManagerMatchingUser(requestingUser).isEmpty(); // autorisationsansvarlig
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
		RequesterOption globalPermission = settingsService.getRolerequestRequester();

		List<RequesterOption> permittedSettings = new ArrayList<>();
		for (RequesterOption permission : RequesterOption.values()) {
			if (
				isPermissionMatchingRights(permission, isRequestingForSelf, isManagerOrSubstitute, isAuthResponsible, isAdmin, isAuthorized)
			) {
				permittedSettings.add(permission);
			}
		}

		//construct allowed settings for inherited ITsystem permissions, adding INHERIT if global settings would allow user request access
		List<RequesterOption> allowedItSettings = new ArrayList<>(permittedSettings);
		if (permittedSettings.contains(globalPermission)) {
			allowedItSettings.add(RequesterOption.INHERIT);
		}

		Stream<UserRole> userRoles = Stream.concat(
			//find all roles with directly matching permissions
			userRoleService.getUserRolesWithRequesterPermissions(permittedSettings).stream(),
			//find all roles with INHERIT, which have IT systems matching permissions
			userRoleService.getUserRolesWithInheritedPermissionsMatching(allowedItSettings).stream());

		if (isAuthorized) {
			//Further filtering by those that can be requested by Authorized, depending on constraints
			// TODO This is not right
			List<String> allowedOrgUnitsIds = accessConstraintService.getConstrainedOrgUnits(true);
			List<Long> allowedItSystems = accessConstraintService.itSystemsUserCanEdit();
			userRoles = userRoles.filter(userRole -> {
				//Check those that can be requested by Authorized for constrained Orgunits or itsystems
				if (approverOptionService.getInheritedRequesterPermission(userRole).getRequestPermissions().contains(RequestableBy.AUTHORIZED)) {
					//Check It system
					boolean constraintsMatch = allowedItSystems == null || allowedItSystems.contains(userRole.getItSystem().getId());


					for (OrgUnit orgUnit : orgUnitService.getByUserRole(userRole, false)) {
						//Only return true if authorized for ALL orgunits in the role

						if (allowedOrgUnitsIds != null && !allowedOrgUnitsIds.contains(orgUnit.getUuid())) {
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
	private boolean isPermissionMatchingRights(RequesterOption permission, boolean isRequestingForSelf, boolean isManagerOrSubstitute, boolean isAuthResponsible, boolean isAdmin, boolean isRequestAuthorized) {
		return (isAdmin && permission.getRequestPermissions().contains(RequestableBy.ADMIN))
			|| (isRequestAuthorized && permission.getRequestPermissions().contains(RequestableBy.AUTHORIZED))
			|| (isRequestingForSelf && permission.getRequestPermissions().contains(RequestableBy.EMPLOYEE))
			|| (isManagerOrSubstitute && permission.getRequestPermissions().contains(RequestableBy.MANAGERORSUBSTITUTE))
			|| (isAuthResponsible && permission.getRequestPermissions().contains(RequestableBy.AUTHRESPONSIBLE));
	}

	/**
	 * Returns a list of RoleGroups that can be requested for the given User, on behalf of the logged in user
	 *
	 * @param recievingUser the target user which the roles can be requested for
	 * @return a list of RoleGroups that are allowed to requests for the user, by the logged in user
	 */
	public List<RoleGroup> getRequestableRoleGroups(User recievingUser) {
		User requestingUser = userService.getOptionalByUserId(SecurityUtil.getUserId()).orElseThrow(() -> new NotFoundException("Unable to find user for user id: " + SecurityUtil.getUserId()));
		boolean isAdmin = SecurityUtil.isAdmin();
		boolean isRequestingForSelf = requestingUser.equals(recievingUser); //anmodende bruger er brugeren som er logget ind
		boolean isManagerOrSubstitute = !userService.getSubstitutesManager(requestingUser).isEmpty() || userService.isManager(requestingUser); // leder eller stedfortræder
		boolean isAuthResponsible = !orgUnitService.getByAuthorizationManagerMatchingUser(requestingUser).isEmpty(); // autorisationsansvarlig
		boolean isAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED); // bemyndiget
		RequesterOption globalPermission = settingsService.getRolerequestRequester();

		List<RequesterOption> permittedSettings = new ArrayList<>();
		for (RequesterOption permission : RequesterOption.values()) {
			if (
				isPermissionMatchingRights(permission, isRequestingForSelf, isManagerOrSubstitute, isAuthResponsible, isAdmin, isAuthorized)
			) {
				permittedSettings.add(permission);
			}
		}
		if (isPermissionMatchingRights(globalPermission, isRequestingForSelf, isManagerOrSubstitute, isAuthResponsible, isAdmin, isAuthorized)) {
			permittedSettings.add(RequesterOption.INHERIT);
		}

		List<RoleGroup> roleGroups = roleGroupService.getRoleGroupsWithRequesterPermissions(permittedSettings);

		if (isAuthorized) {
			//Further filtering by those that can be requested by Authorized, depending on constraints
			// TODO this is not right
			List<String> allowedOrgUnitsIds = accessConstraintService.getConstrainedOrgUnits(true);

			roleGroups = roleGroups.stream().filter(roleGroup -> {
				//Check those that can be requested by Authorized for constrained Orgunits or itsystems
				if (approverOptionService.getInheritedRequesterPermission(roleGroup).getRequestPermissions().contains(RequestableBy.AUTHORIZED)) {
					boolean constraintsMatch = true;

					for (OrgUnit orgUnit : orgUnitService.getByRoleGroup(roleGroup, false)) {
						//Only return true if authorized for ALL orgunits in the role

						if (allowedOrgUnitsIds != null && !allowedOrgUnitsIds.contains(orgUnit.getUuid())) {
							constraintsMatch = false;
							break;
						}
					}

					return constraintsMatch;
				}
				return true;
			}).toList();
		}

		return roleGroups;
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

		final Set<RoleRequest> allRequests = roleRequestDao.findByStatus(RequestApproveStatus.REQUESTED);
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
				if (request.getRequester().getUuid().equals(user.getUuid())) {
					return false;
				}
				final List<ApprovableBy> approvableBy = request.getApproverOption().getApproverPermissions();
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
						|| authorizedForItSystems.itSystems().contains(request.getUserRole().getId());
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
	private boolean isAuthorizedForOrgUnit(OrgUnit orgUnit) {
		List<String> constrainedOrgUnitsUuids = accessConstraintService.getConstrainedOrgUnits(true);
		if (constrainedOrgUnitsUuids == null) {
			//not allowed any it systems;
			return true;
		}
		return constrainedOrgUnitsUuids.contains(orgUnit.getUuid());
	}

	/**
	 * Checks an it system against the constrained systems for the currently logged in user
	 *
	 * @param itSystem an it-system to check
	 * @return true if allowed, false if not
	 */
	private boolean isAuthorizedForITSystem(ItSystem itSystem) {
		List<Long> constrainedITSystemsIds = accessConstraintService.itSystemsUserCanEdit();
		if (constrainedITSystemsIds == null) {
			return true;
		}
		return constrainedITSystemsIds.contains(itSystem.getId());
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

			if (Objects.equals(request.getRequestAction(), RequestAction.REMOVE)) {
				userService.removeUserRole(receiver, userRole);
			} else {
				userService.addUserRole(receiver, userRole, null, null, mapRequestPostponedConstraint(request.getRequestPostponedConstraints()), request.getOrgUnit(), true, null);
				roleName = userRole.getName();
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

		boolean recieverNotified = requestNotifierService.notifyRecieverOnRequestApproval(receiver, request.getRequestAction(), roleName, manualItSystem);
		boolean managerNotified = requestNotifierService.notifyManagerOnRequestapproval(request, manualItSystem, roleName);

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
	 * @param requestId id of the pending request
	 * @return responseentity
	 */
	public ResponseEntity<String> rejectRequest(long requestId) {
		if (!settingsService.isRequestApproveEnabled()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (!settingsService.isRequestApproveEnabled()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		RoleRequest request = roleRequestDao.findById(requestId)
			.orElseThrow();

		request.setStatus(RequestApproveStatus.REJECTED);
//        request.setRejectReason(rejectForm.getReason()); //TODO - Skal der angives en grund?
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
		requestNotifierService.notifyManagerOnRejectedRequest(request, request.getOrgUnit(), roleName);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	public boolean canRequestFor(User requester, User receiver) {
		if (SecurityUtil.isAdmin()) {
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
			.anyMatch(manager -> manager.getManagerSubstitutes().stream()
				.anyMatch(managerSubstitute -> managerSubstitute.getManager() == requester || managerSubstitute.getSubstitute() == requester));
		if (isManagerOrSubstituteFor) {
			return true;
		}

		//is requester authmanager in an orgunit with the receiver?
		boolean isAuthorizationManagerFor = orgUnitService.getOrgUnitsForUser(receiver).stream()
			.anyMatch(orgUnit -> orgUnit.getAuthorizationManagers().stream()
				.anyMatch(authorizationManager -> authorizationManager.getUser() == requester));
		// autorisationsansvarlig
		return isAuthorizationManagerFor;
	}

	public List<UserRole> getAllAutomaticUserRoles() {
		List<UserRole> result = new ArrayList<>();
		List<ApproverOption> approverOptions = new ArrayList<>();
		approverOptions.add(ApproverOption.AUTOMATIC);
		approverOptions.add(ApproverOption.INHERIT);
		for (UserRole userRole : userRoleService.getUserRolesWithApproverPermissions(approverOptions)) {
			if (userRole.getApproverPermission().equals(ApproverOption.AUTOMATIC)) {
				result.add(userRole);
			} else if (userRole.getApproverPermission().equals(ApproverOption.INHERIT)) {
				if (userRole.getItSystem().getApproverPermission() != null && userRole.getItSystem().getApproverPermission().equals(ApproverOption.AUTOMATIC)) {
					result.add(userRole);
				} else if (userRole.getItSystem().getApproverPermission() != null && userRole.getItSystem().getApproverPermission().equals(ApproverOption.INHERIT)) {
					if (settingsService.getRolerequestApprover().equals(ApproverOption.AUTOMATIC)) {
						result.add(userRole);
					}
				}
			}
		}
		return result;
	}

	public List<RoleGroup> getAllAutomaticRoleGroups() {
		List<RoleGroup> result = new ArrayList<>();
		List<ApproverOption> approverOptions = new ArrayList<>();
		approverOptions.add(ApproverOption.AUTOMATIC);
		approverOptions.add(ApproverOption.INHERIT);
		for (RoleGroup roleGroup : roleGroupService.getRoleGroupsWithApproverPermissions(approverOptions)) {
			if (roleGroup.getApproverPermission().equals(ApproverOption.AUTOMATIC)) {
				result.add(roleGroup);
			} else if (roleGroup.getApproverPermission().equals(ApproverOption.INHERIT)) {
				if (settingsService.getRolerequestApprover().equals(ApproverOption.AUTOMATIC)) {
					result.add(roleGroup);
				}
			}
		}
		return result;
	}

	public List<RoleRequest> getPendingForReceiver(User user) {
		return roleRequestDao.findByReceiver_UuidAndStatus(user.getUuid(), RequestApproveStatus.REQUESTED);
	}

	public void deleteRejectedAfter14Days() {
		LocalDateTime cutOffDate = LocalDateTime.now().minusDays(14);
		roleRequestDao.deleteByRequestTimestampBeforeAndStatus(Date.from(cutOffDate.atZone(ZoneId.systemDefault()).toInstant()), RequestApproveStatus.REJECTED);
	}

	public DataTablesOutput<UserRole> getRequestableUserRolesAsDatatable(DataTablesInput input, User recievingUser) {
		User requestingUser = userService.getOptionalByUserId(SecurityUtil.getUserId()).orElseThrow(() -> new NotFoundException("Unable to find user for user id: " + SecurityUtil.getUserId()));
		boolean isAdmin = SecurityUtil.isAdmin();
		boolean isRequestingForSelf = requestingUser.equals(recievingUser); //anmodende bruger er brugeren som er logget ind
		boolean isManagerOrSubstitute = !userService.getSubstitutesManager(requestingUser).isEmpty() || userService.isManager(requestingUser); // leder eller stedfortræder
		boolean isAuthResponsible = !orgUnitService.getByAuthorizationManagerMatchingUser(requestingUser).isEmpty(); // autorisationsansvarlig
		boolean isRequestAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED); // bemyndiget
		RequesterOption globalPermission = settingsService.getRolerequestRequester();

		List<RequesterOption> permittedSettings = new ArrayList<>();
		for (RequesterOption permission : RequesterOption.values()) {
			if (
				isPermissionMatchingRights(permission, isRequestingForSelf, isManagerOrSubstitute, isAuthResponsible, isAdmin, isRequestAuthorized)
			) {
				permittedSettings.add(permission);
			}
		}

		//construct allowed settings for inherited ITsystem permissions, adding INHERIT if global settings would allow user request access
		List<RequesterOption> allowedItSettings = new ArrayList<>(permittedSettings);
		Specification<UserRole> itInheritSpecification;
		if (permittedSettings.contains(globalPermission)) {
			allowedItSettings.add(RequesterOption.INHERIT);
			itInheritSpecification = UserRoleDatatableDao.itSystemRequesterPermissionIn(allowedItSettings).or(UserRoleDatatableDao.itSystemRequesterPermissionNull());
		} else {
			itInheritSpecification = UserRoleDatatableDao.itSystemRequesterPermissionIn(allowedItSettings);
		}

		DataTablesOutput<UserRole> outputDatatable = userRoleDatatableDao.findAll(input, Specification
			.where(UserRoleDatatableDao.requesterPermissionIn(permittedSettings))
			.or(UserRoleDatatableDao.requesterPermissionIn(listOf(RequesterOption.INHERIT)).and(itInheritSpecification)));

		Stream<UserRole> userRoles = outputDatatable.getData().stream();

		if (isRequestAuthorized) {
			//Further filtering by those that can be requested by Authorized, depending on constraints
			List<String> allowedOrgUnitsIds = accessConstraintService.getConstrainedOrgUnits(true);
			List<Long> allowedItSystems = accessConstraintService.itSystemsUserCanEdit();
			userRoles = userRoles.filter(userRole -> {
				//Check those that can be requested by Authorized for constrained Orgunits or itsystems
				if (approverOptionService.getInheritedRequesterPermission(userRole).getRequestPermissions().contains(RequestableBy.AUTHORIZED)) {
					//Check It system
					boolean constraintsMatch = allowedItSystems == null || allowedItSystems.contains(userRole.getItSystem().getId());


					for (OrgUnit orgUnit : orgUnitService.getByUserRole(userRole, false)) {
						//Only return true if authorized for ALL orgunits in the role

						if (allowedOrgUnitsIds != null && !allowedOrgUnitsIds.contains(orgUnit.getUuid())) {
							constraintsMatch = false;
							break;
						}
					}

					return constraintsMatch;
				}
				return true;
			});
		}
		outputDatatable.setData(userRoles.toList());
		return outputDatatable;
	}

	public DataTablesOutput<RoleGroup> getRequestableRoleGroupsAsDatatable(DataTablesInput input, User recievingUser) {
		User requestingUser = userService.getOptionalByUserId(SecurityUtil.getUserId()).orElseThrow(() -> new NotFoundException("Unable to find user for user id: " + SecurityUtil.getUserId()));
		boolean isAdmin = SecurityUtil.isAdmin();
		boolean isRequestingForSelf = requestingUser.equals(recievingUser); //anmodende bruger er brugeren som er logget ind
		boolean isManagerOrSubstitute = !userService.getSubstitutesManager(requestingUser).isEmpty() || userService.isManager(requestingUser); // leder eller stedfortræder
		boolean isAuthResponsible = !orgUnitService.getByAuthorizationManagerMatchingUser(requestingUser).isEmpty(); // autorisationsansvarlig
		boolean isAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED); // bemyndiget
		RequesterOption globalPermission = settingsService.getRolerequestRequester();

		List<RequesterOption> permittedSettings = new ArrayList<>();
		for (RequesterOption permission : RequesterOption.values()) {
			if (
				isPermissionMatchingRights(permission, isRequestingForSelf, isManagerOrSubstitute, isAuthResponsible, isAdmin, isAuthorized)
			) {
				permittedSettings.add(permission);
			}
		}
		if (isPermissionMatchingRights(globalPermission, isRequestingForSelf, isManagerOrSubstitute, isAuthResponsible, isAdmin, isAuthorized)) {
			permittedSettings.add(RequesterOption.INHERIT);
		}

		DataTablesOutput<RoleGroup> output = rolegroupDatatableDao.findAll(input, Specification.where(RolegroupDatatableDao.requesterPermissionIn(permittedSettings)));


		if (isAuthorized) {
			//Further filtering by those that can be requested by Authorized, depending on constraints
			List<String> allowedOrgUnitsIds = accessConstraintService.getConstrainedOrgUnits(true);

			output.setData(output.getData().stream().filter(roleGroup -> {
				//Check those that can be requested by Authorized for constrained Orgunits or itsystems
				if (approverOptionService.getInheritedRequesterPermission(roleGroup).getRequestPermissions().contains(RequestableBy.AUTHORIZED)) {
					boolean constraintsMatch = true;

					for (OrgUnit orgUnit : orgUnitService.getByRoleGroup(roleGroup, false)) {
						//Only return true if authorized for ALL orgunits in the role

						if (allowedOrgUnitsIds != null && !allowedOrgUnitsIds.contains(orgUnit.getUuid())) {
							constraintsMatch = false;
							break;
						}
					}

					return constraintsMatch;
				}
				return true;
			}).toList());
		}

		return output;
	}

	public DataTablesOutput<User> getRequestForUsersAsDatatable(DataTablesInput input, User requestingUser) {
		Set<OrgUnit> orgUnits = new HashSet<>(orgUnitService.getActiveByAuthorizationManagerOrManagerMatchingUser(requestingUser));

		// Now add all the users the user have the authorization role for.
		boolean limitedToOUs = true;
		if (requestAuthorizedRoleService.requestAuthorizedRoleCanRequest()) {
			final RequestAuthorizedRoleService.LimitedToOrgUnits limitedToOrgUnits =
				requestAuthorizedRoleService.accessibleOrgUnits(requestingUser);
			log.info("LimitedToOrgUnits: {} {}", limitedToOrgUnits.type().name(), String.join(",", limitedToOrgUnits.orgUnits()));
			if (limitedToOrgUnits.type() == RequestAuthorizedRoleService.LimitedToType.ALL) {
				limitedToOUs = false;
			} else {
				orgUnits.addAll(limitedToOrgUnits.orgUnits().stream()
					.map(uuid -> orgUnitService.getByUuid(uuid))
					.toList());
			}
		}

		DataTablesOutput<User> outputDatatable;
		if (limitedToOUs) {
			outputDatatable = userDatatableDao.findAll(input, Specification
				.where(UserDatatableDao.requesterPositionOrgUnitIn(orgUnits)));
		} else {
			outputDatatable = userDatatableDao.findAll(input);
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
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, -14);
		Date before = cal.getTime();

		List<RequestApproveStatus> stati = new ArrayList<>();
		stati.add(RequestApproveStatus.ASSIGNED);
		stati.add(RequestApproveStatus.REJECTED);

		roleRequestDao.deleteByStatusInAndStatusTimestampBefore(stati, before);
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
}
