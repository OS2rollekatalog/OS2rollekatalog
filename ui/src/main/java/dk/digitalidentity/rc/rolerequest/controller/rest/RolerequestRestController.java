package dk.digitalidentity.rc.rolerequest.controller.rest;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.rolerequest.controller.mvc.RequestRoleListController;
import dk.digitalidentity.rc.rolerequest.controller.mvc.enums.RoleType;
import dk.digitalidentity.rc.rolerequest.log.RequestAutditLogger;
import dk.digitalidentity.rc.rolerequest.log.RequestLogEvent;
import dk.digitalidentity.rc.rolerequest.log.RequestLoggable;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestPostponedConstraint;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.ApproverOption;
import dk.digitalidentity.rc.rolerequest.model.enums.RequesterOption;
import dk.digitalidentity.rc.rolerequest.service.ApproverOptionService;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.PositionService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.RoleGroupAssignedToUser;
import dk.digitalidentity.rc.service.model.UserRoleAssignedToUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("rest/rolerequest")
public class RolerequestRestController {

	@Autowired
	private RequestService requestService;

	@Autowired
	private UserService userService;

	@Autowired
	private PositionService positionService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;

	@Autowired
	private ConstraintTypeService constraintTypeService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private SettingsService settingsService;

	@Autowired
	private ApproverOptionService approverOptionService;

	@Autowired
	private RequestAutditLogger requestLogger;

	@Autowired
	private MessageSource messageSource;

	@RequestLoggable(logEvent = RequestLogEvent.APPROVE)
	@PostMapping("/{requestId}/approve")
	public ResponseEntity<?> approveRequest(@PathVariable long requestId) {
		RoleRequest request = requestService.getRoleRequestById(requestId)
			.orElseThrow();

		if (!requestService.canApprove(request)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not allowed to deny this request");
		}

		requestService.approveRequest(request);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequestLoggable(logEvent = RequestLogEvent.DENY)
	@PostMapping("/{requestId}/deny")
	public ResponseEntity<?> denyRequest(@PathVariable long requestId) {
		RoleRequest request = requestService.getRoleRequestById(requestId)
			.orElseThrow();

		if (!requestService.canApprove(request)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not allowed to deny this request");
		}

		requestService.rejectRequest(requestId);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	private record RequestDTO(String userUuid, Long positionId, List<Long> userRoles, List<Long> roleGroups,
							  String reason,
							  List<RequestConstraint> constraints) {
	}

	private record RequestConstraint(Long userRoleId, List<RoleConstraint> roleConstraints) {
	}

	private record RoleConstraint(Long systemRoleId, String typeUuid, String value) {
	}

	@PostMapping("/wizard/save")
	public ResponseEntity<?> saveRequest(@RequestBody RequestDTO requestDTO) {
		UUID groupUuid = UUID.randomUUID();

		//Find users
		User requester = userService.getByUserId(SecurityUtil.getUserId());
		if (requester == null) {
			throw new NoSuchElementException("Logged in user does not exist");
		}

		User receiver = userService.getOptionalByUuid(requestDTO.userUuid).orElseThrow(() -> new NoSuchElementException("receiving user does not exist"));
		OrgUnit orgUnit = positionService.getById(requestDTO.positionId).getOrgUnit();
		if (orgUnit == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find organisation unit for request");
		}

		List<RoleRequest> requestGroup = new ArrayList<>();

		//Create requests for UserRoles
		for (Long userRoleId : requestDTO.userRoles) {
			final UserRole userRole = userRoleService.getOptionalById(userRoleId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find requested user role"));
			final RoleRequest request = toBasicRoleRequest(receiver, requester, requestDTO.reason, orgUnit, groupUuid,
				approverOptionService.getInheritedApproverOption(userRole));
			request.setUserRole(userRole);

			//check if allowed to request
			RequesterOption globalRequesterSetting = settingsService.getRolerequestRequester();
			// TODO Check bemyndiget constraints
			boolean isAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED); // bemyndiget
			if (!requestService.canRequest(request.getUserRole(), receiver, globalRequesterSetting, isAuthorized)) {
				log.error("User did not have permission to request one of the requested userroles");
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not allowed to request this");
			}

			//Map the constraints to RequestPostponedConstraints and set them for the request
			request.setRequestPostponedConstraints(
				requestDTO.constraints.stream()
					.filter(constr ->
						constr.userRoleId == request.getUserRole().getId())
					.flatMap(requestConstraint ->
						requestConstraint.roleConstraints().stream().map(roleConstraint ->
							RequestPostponedConstraint.builder()
								.constraintType(constraintTypeService.getByUuid(roleConstraint.typeUuid))
								.systemRole(systemRoleService.getById(roleConstraint.systemRoleId))
								.value(roleConstraint.value)
								.roleRequest(request)
								.build()
						)
					).toList()
			);

			requestGroup.add(request);
		}

		//Create requests for Rolegroups
		for (Long roleGroupId : requestDTO.roleGroups) {
			RoleGroup roleGroup = roleGroupService.getOptionalById(roleGroupId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find requested role group"));
			RoleRequest request = toBasicRoleRequest(receiver, requester, requestDTO.reason, orgUnit, groupUuid,
				approverOptionService.getInheritedApproverOption(roleGroup));
			request.setRoleGroup(roleGroup);

			//check if allowed to request
			if (!requestService.canRequest(request.getRoleGroup(), receiver)) {
				log.error("User did not have permission to request one of the requested rolegroups");
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not allowed to request this");
			}

			requestGroup.add(request);
		}

		//Save all new requests
		for (RoleRequest request : requestGroup) {
			requestService.saveNewRequest(request);
		}


		//Handle automatic approval of those with the corresponding setting
		requestGroup.stream()
			.filter(request -> request.getUserRole() == null ?
				approverOptionService.getInheritedApproverOption(request.getRoleGroup()) == ApproverOption.AUTOMATIC
				: approverOptionService.getInheritedApproverOption(request.getUserRole()) == ApproverOption.AUTOMATIC
			)
			.forEach((request) -> {
				requestService.approveRequest(request);
				//Manually log automatic approval
				requestLogger.logRequest(RequestLogEvent.APPROVE, request, messageSource.getMessage("requestmodule.log.event.request.automaticapprove", null, Locale.getDefault()));
			});


		return new ResponseEntity<>(HttpStatus.OK);
	}

	private record RemoveRequestDTO(String userUuid, List<Long> userRoles, List<Long> roleGroups, String reason) {}
	@PostMapping("/remove/wizard/save")
	public ResponseEntity<?> saveRemoveRequestFromWizard(@RequestBody RemoveRequestDTO requestDTO) {
		//Find users
		User requester = userService.getByUserId(SecurityUtil.getUserId());
		if (requester == null) {
			throw new NoSuchElementException("Logged in user does not exist");
		}

		User receiver = userService.getOptionalByUuid(requestDTO.userUuid).orElseThrow(() -> new NoSuchElementException("receiving user does not exist"));

		UUID groupUuid = UUID.randomUUID();

		for (Long userRoleAssignmentId : requestDTO.userRoles) {
			RoleRequest request = handleCreateRemovalRequest(requester, receiver, userRoleAssignmentId, null, requestDTO.reason, groupUuid);
			requestLogger.logRequest(RequestLogEvent.REMOVE, request, request.getReason());
		}

		for (Long roleGroupAssignmentId : requestDTO.roleGroups) {
			RoleRequest request = handleCreateRemovalRequest(requester, receiver, null, roleGroupAssignmentId, requestDTO.reason, groupUuid);
			requestLogger.logRequest(RequestLogEvent.REMOVE, request, request.getReason());
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequestLoggable(logEvent = RequestLogEvent.CANCEL)
	@DeleteMapping("{id}/cancel")
	public ResponseEntity<?> cancelRequest(@PathVariable Long id) {
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		RoleRequest request = requestService.getRoleRequestById(id)
			.orElseThrow();

		RequesterOption globalRequesterSetting = settingsService.getRolerequestRequester();
		boolean isAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED); // bemyndiget
		if (request.getUserRole() != null && !requestService.canRequest(request.getUserRole(), loggedInUser, globalRequesterSetting, isAuthorized)) {
			throw new SecurityException("User not allowed to cancel this request");
		} else if (request.getRoleGroup() != null && !requestService.canRequest(request.getRoleGroup(), loggedInUser)) {
			throw new SecurityException("User not allowed to cancel this request");
		}

		requestService.deleteRolerequest(id);

		return new ResponseEntity<>(HttpStatus.OK);
	}


	private record RemovalRequestDTO(Long userRoleAssignmentId, Long roleGroupAssignmentId, String reason) {
	}

	@RequestLoggable(logEvent = RequestLogEvent.REMOVE)
	@PostMapping("remove")
	public ResponseEntity<?> removeRequestForSelf(@RequestBody RemovalRequestDTO removalRequestDTO) {
		//find current user
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (loggedInUser == null) {
			throw new NoSuchElementException("Logged in user does not exist");
		}

		handleCreateRemovalRequest(loggedInUser, loggedInUser, removalRequestDTO.userRoleAssignmentId, removalRequestDTO.roleGroupAssignmentId, removalRequestDTO.reason, UUID.randomUUID());

		return new ResponseEntity<>(HttpStatus.OK);
	}

	private RoleRequest toBasicRoleRequest(User receiver, User requester, String reason, OrgUnit orgUnit, UUID groupIdentifier, ApproverOption whoCanApprove) {
		return RoleRequest.builder()
			.requester(requester)
			.receiver(receiver)
			.reason(reason)
			.requestAction(RequestAction.ADD)
			.status(RequestApproveStatus.REQUESTED)
			.orgUnit(orgUnit)
			.requestGroupIdentifier(groupIdentifier.toString())
			.approverOption(whoCanApprove)
			.statusTimestamp(new Date())
			.requestTimestamp(new Date())
			.build();
	}

	record EmployeeDTO(String uuid, String userId, String name, Set<String> positions, boolean hasRoles, boolean canRequestRemoval) {
	}
	@PostMapping("employees")
	public DataTablesOutput<EmployeeDTO> getEmployeesDatatable(@RequestBody DataTablesInput input)  {
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (loggedInUser == null) {
			throw new UsernameNotFoundException("Could not find logged in user");
		}

		DataTablesOutput<User> userroleOutput = requestService.getRequestForUsersAsDatatable(input, loggedInUser);
		List<EmployeeDTO> allUserRolesDTOs = userroleOutput.getData().stream()
			.map(user -> new EmployeeDTO(
				user.getUuid(),
				user.getUserId(),
				user.getName(),
				user.getPositions().stream()
					.map(p -> p.getName() + " i " + p.getOrgUnit().getName())
					.collect(Collectors.toSet()),
				!(user.getUserRoleAssignments().isEmpty() && user.getRoleGroupAssignments().isEmpty()),
				calculateCanRequestRemoval(user)
			))
			.toList();

		return RequestService.toDatatablesOutput(userroleOutput, allUserRolesDTOs);
	}

	private boolean calculateCanRequestRemoval(User user) {
		for (RoleGroupAssignedToUser roleGroupAssignment : userService.getAllRoleGroupsAssignedToUser(user)) {
			if (roleGroupAssignment.getAssignedThrough().equals(AssignedThrough.DIRECT)) {
				return true;
			}
		}

		for (UserRoleAssignedToUser userRoleAssignment : userService.getAllUserRolesAssignedToUserExemptingRoleGroups(user, null)) {
			if (userRoleAssignment.getAssignedThrough().equals(AssignedThrough.DIRECT)) {
				return true;
			}
		}

		return false;
	}

	private RoleRequest handleCreateRemovalRequest(User requester, User receiver, Long userRoleAssignmentId, Long roleGroupAssignmentId, String reason, UUID groupUuid) {

		// set userrole-specifics
		OrgUnit orgUnit = null;
		ApproverOption approverOption = null;
		UserRole userRole;
		if (userRoleAssignmentId != null) {
			UserUserRoleAssignment assignment = receiver.getUserRoleAssignments().stream()
				.filter(u -> u.getId() == userRoleAssignmentId).findFirst()
				.orElseThrow(() -> new NoSuchElementException("receiving user does not have the userUserRoleAssignment with id " + userRoleAssignmentId));
			userRole = assignment.getUserRole();
			approverOption = approverOptionService.getInheritedApproverOption(userRole);

			// check if allowed to request
			RequesterOption globalRequesterSetting = settingsService.getRolerequestRequester();
			boolean isAuthorized = SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED); // bemyndiget
			if (!requestService.canRequest(userRole, requester, globalRequesterSetting, isAuthorized)) {
				throw new SecurityException("User not allowed to request this");
			}

			orgUnit = assignment.getOrgUnit();
		} else {
			userRole = null;
		}

		// set rolegroup specifics
		RoleGroup roleGroup;
		if (roleGroupAssignmentId != null) {
			UserRoleGroupAssignment assignment = receiver.getRoleGroupAssignments().stream()
				.filter(r -> r.getId() == roleGroupAssignmentId).findFirst()
				.orElseThrow(() -> new NoSuchElementException("receiving user does not have the userRoleGroupAssignment with id " + userRoleAssignmentId));
			roleGroup = assignment.getRoleGroup();
			approverOption = approverOptionService.getInheritedApproverOption(roleGroup);

			// check if allowed to request
			if (!requestService.canRequest(roleGroup, requester)) {
				throw new SecurityException("User not allowed to request this");
			}

			orgUnit = assignment.getOrgUnit();

		} else {
			roleGroup = null;
		}

		// create 'remove' request
		RoleRequest request = toBasicRoleRequest(receiver, requester, reason, orgUnit, groupUuid, approverOption);
		request.setUserRole(userRole);
		request.setRoleGroup(roleGroup);
		request.setRequestAction(RequestAction.REMOVE);

		request = requestService.saveNewRequest(request);

		// handle automatic approval if enabled
		boolean automaticApproval = request.getUserRole() == null ?
			approverOptionService.getInheritedApproverOption(request.getRoleGroup()) == ApproverOption.AUTOMATIC
			: approverOptionService.getInheritedApproverOption(request.getUserRole()) == ApproverOption.AUTOMATIC;

		if (automaticApproval) {
			requestService.approveRequest(request);
			// manually log automatic approval
			requestLogger.logRequest(RequestLogEvent.APPROVE, request, messageSource.getMessage("requestmodule.log.event.request.automaticapprove", null, Locale.getDefault()));
		}

		return request;
	}

}
