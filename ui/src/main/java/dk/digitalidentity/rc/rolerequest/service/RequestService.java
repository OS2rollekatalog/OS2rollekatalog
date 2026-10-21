package dk.digitalidentity.rc.rolerequest.service;

import static dk.digitalidentity.rc.rolerequest.RequestConstants.CACHE_PREFIX;
import static java.util.stream.Collectors.groupingBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.MessageSource;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.SessionConstants;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.CombinedRoleViewDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.RolegroupDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.UserRoleViewDatatableDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.CombinedRoleView;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleView;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ManualAssignmentNotificationMap;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.exceptions.NotFoundException;
import dk.digitalidentity.rc.rolerequest.dao.RoleRequestDao;
import dk.digitalidentity.rc.rolerequest.log.RequestAuditLogger;
import dk.digitalidentity.rc.rolerequest.log.RequestLogEvent;
import dk.digitalidentity.rc.rolerequest.log.RequestPersister;
import dk.digitalidentity.rc.rolerequest.model.dto.RequestedRole;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestPostponedConstraint;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.permission.ApproverPermission;
import dk.digitalidentity.rc.rolerequest.service.permission.ApprovableRequestService;
import dk.digitalidentity.rc.rolerequest.service.permission.PermittedRoleIds;
import dk.digitalidentity.rc.rolerequest.service.permission.RequestAuthority;
import dk.digitalidentity.rc.rolerequest.service.permission.RequestableRoleService;
import dk.digitalidentity.rc.rolerequest.service.permission.RequesterContext;
import dk.digitalidentity.rc.rolerequest.service.permission.ResolvedPermissionService;
import dk.digitalidentity.rc.rolerequest.service.scope.LimitedToOrgUnits;
import dk.digitalidentity.rc.rolerequest.service.scope.LimitedToType;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.ManualAssignmentNotificationMapService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.util.Failure;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestService {

	private final RequestPersister requestPersister;
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
	private final RequestAuthorizedRoleService requestAuthorizedRoleService;
	private final ManualAssignmentNotificationMapService manualAssignmentNotificationMapService;
	private final UserDatatableDao userDatatableDao;
	private final CombinedRoleViewDatatableDao combinedRoleViewDao;
	private final AssignmentService assignmentService;
	private final ApprovableRequestService approvableRequestService;
	private final RequestAuditLogger requestAuditLogger;
	private final MessageSource messageSource;
	private final RequestableRoleService requestableRoleService;
	private final ResolvedPermissionService resolvedPermissionService;

	public boolean hasPendingRemovalRequestForUserrole(Long userRoleId, String recieverUuid) {
		return !roleRequestDao.findByRequestActionAndReceiver_UuidAndUserRole_IdAndStatus(
			RequestAction.REMOVE, recieverUuid, userRoleId, RequestApproveStatus.REQUESTED).isEmpty();
	}

	public boolean hasPendingRemovalRequestForRolegroup(Long roleGroupId, String recieverUuid) {
		return !roleRequestDao.findByRequestActionAndReceiver_UuidAndRoleGroup_IdAndStatus(
			RequestAction.REMOVE, recieverUuid, roleGroupId, RequestApproveStatus.REQUESTED).isEmpty();
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

	// TODO: move to RequestPersister once other RoleRequest persistence methods are consolidated there
	public Optional<RoleRequest> getRoleRequestById(Long id) {
		return roleRequestDao.findById(id);
	}

	// TODO: move to RequestPersister once other RoleRequest persistence methods are consolidated there
	public void deleteRolerequest(Long rolerequestId) {
		roleRequestDao.deleteById(rolerequestId);
	}

	// TODO: move to RequestPersister once other RoleRequest persistence methods are consolidated there
	public RoleRequest saveNoLog(RoleRequest roleRequest) {
		return roleRequestDao.save(roleRequest);
	}

	@Transactional
	public void saveRequestGroupWithLogAndAutoApprove(List<RoleRequest> requests, User actingUser) {
		for (RoleRequest request : requests) {
			if (request.getRequestAction() == RequestAction.REMOVE) {
				requestPersister.saveRemoveRequestWithLog(request);
			} else {
				requestPersister.saveNewRequestWithLog(request);
			}
			autoApproveIfAutomatic(request, actingUser);
		}
	}

	private void autoApproveIfAutomatic(RoleRequest request, User actingUser) {
		boolean automaticByRoleSetting = request.getApproverOption() != null && request.getApproverOption().isAutomatic();
		boolean automaticByRemovalSetting = request.getRequestAction() == RequestAction.REMOVE
			&& settingsService.isAutoApproveRemovalEnabled();

		if (automaticByRoleSetting || automaticByRemovalSetting) {
			ResponseEntity<String> result = approveRequest(request);
			if (result.getStatusCode().is2xxSuccessful()) {
				requestAuditLogger.logRequest(
					RequestLogEvent.APPROVE, request,
					messageSource.getMessage(
						"requestmodule.log.event.request.automaticapprove", null, Locale.getDefault()), actingUser
				);
			} else {
				log.warn("Auto-approval failed for request id={}: {}", request.getId(), result.getBody());
			}
		}
	}

	public void validateDates(LocalDate startDate, LocalDate stopDate) {
		if (stopDate != null && stopDate.isBefore(LocalDate.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stopDate cannot be in the past");
		}
		if (startDate != null && stopDate != null && startDate.isAfter(stopDate)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before or equal to stopDate");
		}
	}

	private RoleRequest buildRoleRequest(
		User requester, User receiver, String reason, OrgUnit orgUnit, UUID groupUuid,
		ApproverPermission whoCanApprove, LocalDate startDate, LocalDate stopDate, RequestAction action
	) {
		return RoleRequest.builder()
			.requester(requester)
			.receiver(receiver)
			.reason(reason)
			.startDate(startDate)
			.endDate(stopDate)
			.requestAction(action)
			.status(RequestApproveStatus.REQUESTED)
			.orgUnit(orgUnit)
			.requestGroupIdentifier(groupUuid.toString())
			.requestTimestamp(new Date())
			.statusTimestamp(new Date())
			.approverOption(whoCanApprove)
			.build();
	}

	/**
	 * The only place role requests are built, so no request skips the permission check.
	 */
	@Transactional(readOnly = true)
	public List<RoleRequest> buildPermittedRoleRequests(
		User requester,
		User receiver,
		OrgUnit orgUnit,
		UUID groupUuid,
		String reason,
		List<RequestedRole> requestedRoles,
		RequestAction requestAction
	) {
		requestedRoles.forEach(requestedRole -> validateDates(requestedRole.startDate(), requestedRole.stopDate()));

		Map<Long, UserRole> userRoles = loadRequestedUserRoles(requestedRoles);
		Map<Long, RoleGroup> roleGroups = loadRequestedRoleGroups(requestedRoles);
		PermittedRoleIds permitted = requestAction == RequestAction.REMOVE
			? requestableRoleService.removableAmong(requester, receiver, userRoles.keySet(), roleGroups.keySet())
			: requestableRoleService.requestableAmong(requester, receiver, orgUnit, userRoles.keySet(), roleGroups.keySet());
		Map<Long, ApproverPermission> userRoleApprovers = resolvedPermissionService.userRoleApproverPermissions(userRoles.keySet());
		Map<Long, ApproverPermission> roleGroupApprovers = resolvedPermissionService.roleGroupApproverPermissions(roleGroups.keySet());

		return requestedRoles.stream()
			.map(requestedRole -> {
				RoleRequest request;
				if (requestedRole.userRoleId() != null) {
					UserRole userRole = userRoles.get(requestedRole.userRoleId());
					denyUnlessPermitted(permitted.userRoleIds(), userRole.getId(), requestAction);
					OrgUnit resolvedOrgUnit = requestAction == RequestAction.REMOVE ? findDirectAssignmentOrgUnit(receiver, userRole) : orgUnit;
					request = buildRoleRequest(requester, receiver, reason, resolvedOrgUnit, groupUuid, userRoleApprovers.get(userRole.getId()),
						requestedRole.startDate(), requestedRole.stopDate(), requestAction);
					request.setUserRole(userRole);
				}
				else {
					RoleGroup roleGroup = roleGroups.get(requestedRole.roleGroupId());
					denyUnlessPermitted(permitted.roleGroupIds(), roleGroup.getId(), requestAction);
					OrgUnit resolvedOrgUnit = requestAction == RequestAction.REMOVE ? findDirectAssignmentOrgUnit(receiver, roleGroup) : orgUnit;
					request = buildRoleRequest(requester, receiver, reason, resolvedOrgUnit, groupUuid, roleGroupApprovers.get(roleGroup.getId()),
						requestedRole.startDate(), requestedRole.stopDate(), requestAction);
					request.setRoleGroup(roleGroup);
				}
				return request;
			})
			.toList();
	}

	private Map<Long, UserRole> loadRequestedUserRoles(List<RequestedRole> requestedRoles) {
		Set<Long> ids = requestedRoles.stream().map(RequestedRole::userRoleId).filter(Objects::nonNull).collect(Collectors.toSet());
		Map<Long, UserRole> found = ids.isEmpty() ? Map.of() : userRoleService.findAllByIdIn(ids).stream()
			.collect(Collectors.toMap(UserRole::getId, Function.identity()));
		ids.stream().filter(id -> !found.containsKey(id)).findFirst().ifPresent(id -> {
			throw Failure.notFound(log, "UserRole with id {} was not found", id);
		});
		return found;
	}

	private Map<Long, RoleGroup> loadRequestedRoleGroups(List<RequestedRole> requestedRoles) {
		Set<Long> ids = requestedRoles.stream().map(RequestedRole::roleGroupId).filter(Objects::nonNull).collect(Collectors.toSet());
		Map<Long, RoleGroup> found = roleGroupService.findAllByIdIn(ids).stream()
			.collect(Collectors.toMap(RoleGroup::getId, Function.identity()));
		ids.stream().filter(id -> !found.containsKey(id)).findFirst().ifPresent(id -> {
			throw Failure.notFound(log, "Role group with id {} was not found", id);
		});
		return found;
	}

	private static void denyUnlessPermitted(Set<Long> permittedIds, long id, RequestAction requestAction) {
		if (!permittedIds.contains(id)) {
			throw requestAction == RequestAction.REMOVE
				? Failure.accessDenied(log, "User is not allowed to request removal of role with id {}", id)
				: Failure.accessDenied(log, "User is not allowed to request assignment of role with id {}", id);
		}
	}

	// CurrentAssignment carries the OU only for inherited assignments, and removals target direct ones;
	// a role assigned twice resolves to the active assignment, falling back to one that starts later
	private OrgUnit findDirectAssignmentOrgUnit(User receiver, UserRole userRole) {
		return receiver.getUserRoleAssignments().stream()
			.filter(assignment -> assignment.getUserRole().getId() == userRole.getId())
			.filter(assignment -> assignment.getOrgUnit() != null)
			.min(Comparator.comparing(UserUserRoleAssignment::isInactive))
			.map(UserUserRoleAssignment::getOrgUnit)
			.orElse(null);
	}

	private OrgUnit findDirectAssignmentOrgUnit(User receiver, RoleGroup roleGroup) {
		return receiver.getRoleGroupAssignments().stream()
			.filter(assignment -> assignment.getRoleGroup().getId() == roleGroup.getId())
			.filter(assignment -> assignment.getOrgUnit() != null)
			.min(Comparator.comparing(UserRoleGroupAssignment::isInactive))
			.map(UserRoleGroupAssignment::getOrgUnit)
			.orElse(null);
	}

	/**
	 * Gets all pending request the currently logged in user has the rights to approve.
	 * The order is the one established by the query (newest request first) - it must stay
	 * deterministic, since the list is rendered directly into the pending requests table.
	 * @return a list of pending requests which can be approved by the current user
	 */
	@Transactional(readOnly = true)
	public List<RoleRequest> getPendingApprovableRequests() {
		final User user = userService.getOptionalByUserId(SecurityUtil.getUserId())
			.orElseThrow(() -> new NotFoundException("Unable to find user for user id: " + SecurityUtil.getUserId()));
		return getPendingApprovableRequestsForUser(user.getUuid());
	}

	@Transactional(readOnly = true)
	public List<RoleRequest> getPendingApprovableRequestsForUser(String userUuid) {
		final User user = userService.getByUuid(userUuid);
		if (user == null) {
			throw new NotFoundException("Unable to find user with uuid: " + userUuid);
		}
		return approvableRequestService.pendingApprovableRequests(user);
	}

	/**
	 * Gets all requests with the currently logged in user as Requester, grouped by RequestGroup
	 * @return a map with the group id as key. All ungrouped requests have the "ungrouped" key
	 */
	public Map<String, List<RoleRequest>> getRequestsForUserByGroup(User user) {
		return roleRequestDao.findByRequester_Uuid(user.getUuid()).stream()
			.collect(groupingBy(request -> request.getRequestGroupIdentifier() == null
				? "ungrouped"
				: request.getRequestGroupIdentifier()));
	}

	/**
	 * Approves a request and notifies the relevant users
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
		if (receiver.isDeleted() && !Objects.equals(request.getRequestAction(), RequestAction.REMOVE)) {
			return new ResponseEntity<>("Der er valgt en inaktiv modtager af rollen", HttpStatus.BAD_REQUEST);
		}

		boolean manualItSystem = false;
		String roleName = "";
		String itSystemName = "";
		if (request.getUserRole() != null) {
			//userrole
			UserRole userRole = request.getUserRole();
			roleName = userRole.getName();
			itSystemName = userRole.getItSystem().getName();
			if (Objects.equals(request.getRequestAction(), RequestAction.REMOVE)) {
				setRemovalReasonOnNotificationMap(receiver, Set.of(userRole.getId()), request.getReason());
				userService.removeUserRole(receiver, userRole);
			} else {
				userService.addUserRole(
					receiver, userRole, request.getStartDate(), request.getEndDate(),
					mapRequestPostponedConstraint(request.getRequestPostponedConstraints()), request.getOrgUnit(),
					true,
					null,
					request.getReason()
				);
				manualItSystem = userRole.getItSystem().getSystemType().equals(ItSystemType.MANUAL);
			}
			// add/removeUserRole only mutate the in-memory collection; persist explicitly so the queued recalculation sees the change
			userService.save(receiver);
			requestNotifierService.notifyReceiverOnRequestApproval(
				request, roleName, manualItSystem, itSystemName, resolveRequestAuthorityMessage(request));
		} else {
			if (request.getRoleGroup() != null) {
				//Rolegroup
				RoleGroup roleGroup = request.getRoleGroup();
				if (Objects.equals(request.getRequestAction(), RequestAction.REMOVE)) {
					Set<Long> roleGroupUserRoleIds = roleGroup.getUserRoleAssignments().stream()
						.map(ura -> ura.getUserRole().getId())
						.collect(Collectors.toSet());
					setRemovalReasonOnNotificationMap(receiver, roleGroupUserRoleIds, request.getReason());
					userService.removeRoleGroup(receiver, roleGroup);
				} else {
					userService.addRoleGroup(receiver, roleGroup, null, null, request.getOrgUnit(), null, request.getReason());
				}
				roleName = roleGroup.getName();
				// add/removeRoleGroup only mutate the in-memory collection; persist explicitly so the queued recalculation sees the change
				userService.save(receiver);
				for (RoleGroupUserRoleAssignment userRoleAssignment : roleGroup.getUserRoleAssignments()) {
					if (userRoleAssignment.getUserRole().getItSystem().getSystemType().equals(ItSystemType.MANUAL)) {
						manualItSystem = true;
						break;
					}
				}
				requestNotifierService.notifyReceiverOnRequestApproval(
					request, roleName, manualItSystem, itSystemName, resolveRequestAuthorityMessage(request));
			} else {
				//No userrole or rolegroup
				return new ResponseEntity<>("No role attached to request", HttpStatus.BAD_REQUEST);
			}
		}

		requestNotifierService.notifyManagerOnRequestapproval(
			request, manualItSystem, roleName, request.getReason(), resolveRequestAuthorityMessage(request));
		request.setStatus(RequestApproveStatus.ASSIGNED);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	// the assignment row is gone by the time a removal is detected, so the reason must live on the map row instead
	private void setRemovalReasonOnNotificationMap(User receiver, Set<Long> userRoleIds, String reason) {
		long domainId = receiver.getDomain().getId();
		List<ManualAssignmentNotificationMap> maps = manualAssignmentNotificationMapService.getForUser(domainId, receiver.getUserId());
		for (ManualAssignmentNotificationMap map : maps) {
			if (userRoleIds.contains(map.getUserRoleId())) {
				map.setRemovalReason(reason);
				manualAssignmentNotificationMapService.save(map);
			}
		}
	}

	private String resolveRequestAuthorityMessage(RoleRequest request) {
		RequestAuthority requesterAuthority = request.getRoleGroup() != null
			? requestableRoleService.requestAuthority(request.getRequester(), request.getReceiver(), request.getOrgUnit(), request.getRoleGroup())
			: requestableRoleService.requestAuthority(request.getRequester(), request.getReceiver(), request.getOrgUnit(), request.getUserRole());
		return requesterAuthority.getMessage();
	}

	/**
	 * Maps the requestconstraints to regular PostponedConstraints
	 * @param constraints list of RequestPostponedConstraints
	 * @return list of PostponedConstraints
	 */
	private List<PostponedConstraint> mapRequestPostponedConstraint(List<RequestPostponedConstraint> constraints) {
		List<PostponedConstraint> postponedConstraintsForAssignment = new ArrayList<>();
		if (constraints == null) {
			return postponedConstraintsForAssignment;
		}
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
	 * @param request the pending request
	 * @param reason  the reason the request is rejected
	 * @return responseentity
	 */
	public ResponseEntity<String> rejectRequest(RoleRequest request, String reason) {
		if (!settingsService.isRequestApproveEnabled()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		request.setStatus(RequestApproveStatus.REJECTED);
		request.setRejectReason(reason);
		request = roleRequestDao.save(request);

		String roleName = "";
		if (request.getUserRole() != null) {
			roleName = request.getUserRole().getName();
		} else {
			if (request.getRoleGroup() != null) {
				roleName = request.getRoleGroup().getName();
			} else {
				log.error("Unknown roleType:");
			}
		}

		// notifying manager and authorizationManager
		requestNotifierService.notifyManagerOnRejectedRequest(
			request, request.getOrgUnit(), roleName, request.getRejectReason(),
			resolveRequestAuthorityMessage(request)
		);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	public boolean canRequestFor(User requester, User receiver) {
		if (SecurityUtil.hasDirectAdminRole()) {
			log.debug("Admin can request for anybody");
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

		//Is requester manager or substitute for receiver? Also matches the receiver's nearest
		//leader in the hierarchy, so a department chief can request for their team leaders.
		boolean isManagerOrSubstituteFor = userService.getManager(receiver).stream()
			.anyMatch(manager -> manager.getUuid().equals(requester.getUuid()) ||
				manager.getManagerSubstitutes().stream()
					.anyMatch(managerSubstitute ->
								  managerSubstitute.getManager().getUuid().equals(requester.getUuid())
									  || managerSubstitute.getSubstitute().getUuid().equals(requester.getUuid())))
			|| userService.isEffectiveManagerOrSubstituteFor(requester, receiver);
		if (isManagerOrSubstituteFor) {
			return true;
		} else {
			log.info("{} is not manager for {}", requester.getUuid(), receiver.getUuid());
		}

		//is requester authmanager in an orgunit with the receiver?
		boolean isAuthorizationManagerFor = orgUnitService.getOrgUnitsForUser(receiver).stream()
			.anyMatch(orgUnit -> orgUnit.getAuthorizationManagers().stream()
				.anyMatch(
					authorizationManager -> authorizationManager.getUser().getUuid().equals(requester.getUuid())));
		if (!isAuthorizationManagerFor) {
			log.info("{} is not authorized for {}", requester.getUuid(), receiver.getUuid());
		}
		// autorisationsansvarlig
		return isAuthorizationManagerFor;
	}

	public List<RoleRequest> getPendingForReceiver(User user) {
		return roleRequestDao.findByReceiver_UuidAndStatus(user.getUuid(), RequestApproveStatus.REQUESTED);
	}

	public DataTablesOutput<UserRoleView> getRequestableUserRolesAsDatatable(
		final DataTablesInput input,
		final User requestingUser,
		final User receivingUser,
		final OrgUnit orgUnit
	) {
		RequesterContext context = requestableRoleService.context(requestingUser, receivingUser, orgUnit);
		return userRoleDatatableDao.findAll(input, requestableRoleService.userRoles(context));
	}

	public DataTablesOutput<CombinedRoleView> getRequestableCombinedRolesAsDatatable(
		final DataTablesInput input,
		final User requestingUser,
		final User receivingUser,
		final OrgUnit orgUnit,
		final boolean hideAlreadyAssigned
	) {
		RequesterContext context = requestableRoleService.context(requestingUser, receivingUser, orgUnit);

		Set<CurrentAssignment> assignments = assignmentService.getByUserIncludingInactive(receivingUser);
		List<Long> assignedUserRoleIds = assignments.stream()
			.filter(a -> a.getUserRole() != null)
			.map(a -> a.getUserRole().getId())
			.toList();
		List<Long> assignedRoleGroupIds = assignments.stream()
			.filter(a -> a.getRoleGroup() != null)
			.map(a -> a.getRoleGroup().getId())
			.toList();

		Specification<CombinedRoleView> alreadyAssignedFilter = hideAlreadyAssigned
			? CombinedRoleViewDatatableDao.excludeAlreadyAssigned(assignedUserRoleIds, assignedRoleGroupIds)
			: (_, _, cb) -> cb.conjunction();

		Set<Long> userRoleIdsInRoleGroups = userRoleIdsInRoleGroups(
			requestableRoleService.roleGroups(context).and(alreadyAssignedFilter));

		Specification<CombinedRoleView> specification = requestableRoleService.combinedRoles(context)
			.and(CombinedRoleViewDatatableDao.excludeUserRolesById(userRoleIdsInRoleGroups))
			.and(alreadyAssignedFilter);
		return combinedRoleViewDao.findAll(input, specification);
	}

	public List<RoleGroup> getRequestableRoleGroupsAsDatatable(
		final User requestingUser,
		final User receivingUser,
		final OrgUnit orgUnit
	) {
		RequesterContext context = requestableRoleService.context(requestingUser, receivingUser, orgUnit);
		Set<Long> roleGroupIds = combinedRoleViewDao.findIds(requestableRoleService.roleGroups(context));
		if (roleGroupIds.isEmpty()) {
			return List.of();
		}
		return rolegroupDatatableDao.findAll((root, _, _) -> root.get("id").in(roleGroupIds));
	}

	public DataTablesOutput<User> getRequestForUsersAsDatatable(DataTablesInput input, User requestingUser) {
		Set<OrgUnit> orgUnits = new HashSet<>(
			orgUnitService.getActiveByAuthorizationManagerOrManagerMatchingUser(requestingUser));

		// As a manager the user can also request for everyone they are the nearest leader for:
		// descendant OUs without a manager of their own, and the managers of sub-OUs (team
		// leaders) — but not the sub-managers' own employees.
		OrgUnitService.ManagerRequestScope managerScope = orgUnitService.getManagerRequestScope(requestingUser);
		orgUnits.addAll(managerScope.orgUnits());
		Set<String> subManagerUserUuids = managerScope.subManagerUserUuids();

		// Now add all the users the user have the authorization role for.
		boolean limitedToOUs = true; // Admins are never limited
		if (requestAuthorizedRoleService.requestAuthorizedRoleCanRequest() && !SecurityUtil.hasDirectAdminRole()) {
			final LimitedToOrgUnits limitedToOrgUnits =
				requestAuthorizedRoleService.accessibleOrgUnits(requestingUser);
			log.info(
				"LimitedToOrgUnits: {} {}", limitedToOrgUnits.type().name(),
				String.join(",", limitedToOrgUnits.orgUnits())
			);
			if (limitedToOrgUnits.type() == LimitedToType.ALL) {
				limitedToOUs = false;
			} else {
				orgUnits.addAll(limitedToOrgUnits.orgUnits().stream()
									.map(orgUnitService::getByUuid)
									.filter(Objects::nonNull)
									.toList());
			}
		}
		if (SecurityUtil.hasDirectAdminRole()) {
			limitedToOUs = false;
		}

		DataTablesOutput<User> outputDatatable;
		if (limitedToOUs) {
			outputDatatable = userDatatableDao.findAll(
				input, UserDatatableDao.requesterPositionOrgUnitInOrUserIn(orgUnits, subManagerUserUuids));
		} else {
			outputDatatable = userDatatableDao.findAll(input, UserDatatableDao.notDeleted());
		}

		Stream<User> users = outputDatatable.getData().stream();

		outputDatatable.setData(users.toList());
		return outputDatatable;
	}

	public long getRequestCount() {
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		return requestableRoleService.requestableUserRoleIds(loggedInUser, loggedInUser).size();
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

		roleRequestDao.deleteByStatusInAndStatusTimestampBefore(
			stati, Date.from(cutOffDate.atZone(ZoneId.systemDefault()).toInstant()));
	}

	@Cacheable(value = CACHE_PREFIX + "isManagerAnywhere", key = "#user.uuid")
	public boolean isManagerAnywhere(final User user) {
		return !userService.getSubstitutesManager(user)
			.isEmpty() || !orgUnitService.getByAuthorizationManagerMatchingUser(user)
			.isEmpty() || !orgUnitService.getByManagerMatchingUser(user).isEmpty();
	}

	@Cacheable(value = CACHE_PREFIX + "isSystemResponsibleAnywhere", key = "#user.uuid")
	public boolean isSystemResponsibleAnywhere(User user) {
		return itSystemService.systemResponsibleCount(user) > 0;
	}

	public boolean isRequestAuthorizedAnywhere() {
		return SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED);
	}

	public boolean isApproveAuthorizedAnywhere() {
		return SecurityUtil.hasRole(Constants.ROLE_APPROVEAUTHORIZED);
	}

	@Cacheable(value = CACHE_PREFIX + "isAuthorizationResponsibleAnywhere", key = "#user.uuid")
	public boolean isAuthorizationResponsibleAnywhere(User user) {
		return !orgUnitService.getByAuthorizationManagerMatchingUser(user).isEmpty();
	}

	// TODO: move to RequestPersister once other RoleRequest persistence methods are consolidated there
	public List<RoleRequest> getAll() {
		return roleRequestDao.findAll();
	}

	private Set<Long> userRoleIdsInRoleGroups(Specification<CombinedRoleView> roleGroupSpecification) {
		return roleGroupService.getUserRoleIds(combinedRoleViewDao.findIds(roleGroupSpecification));
	}

	@Transactional
	public void assignRequestToUser(Long requestId, String userId) {
		RoleRequest request = getRoleRequestById(requestId)
			.orElseThrow(() -> new ResponseStatusException(
				HttpStatus.NOT_FOUND,
				"Could not find role request with id: " + requestId
			));

		User loggedInUser = userService.getByUserId(userId);
		if (loggedInUser == null) {
			log.error("Logged in user {} not found in database", SecurityUtil.getUserId());
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User information unavailable");
		}

		if (!approvableRequestService.canApprove(request, loggedInUser)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not allowed to assign this request");
		}

		request.setAssignedTo(loggedInUser.getName());
		saveNoLog(request);
	}

	public User resolveUserByUserId(String userId, String role) {
		User user = userService.getByUserId(userId);
		if (user == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for " + role + " userId=" + userId);
		}
		return user;
	}
}
