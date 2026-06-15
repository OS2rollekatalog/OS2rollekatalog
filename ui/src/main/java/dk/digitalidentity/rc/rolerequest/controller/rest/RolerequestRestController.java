package dk.digitalidentity.rc.rolerequest.controller.rest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.PNumberDao;
import dk.digitalidentity.rc.dao.SENumberDao;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.rolerequest.log.RequestAuditLogger;
import dk.digitalidentity.rc.rolerequest.log.RequestLogEvent;
import dk.digitalidentity.rc.rolerequest.log.RequestLoggable;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestPostponedConstraint;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.rolerequest.service.ApproverOptionService;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.PositionService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import lombok.extern.slf4j.Slf4j;

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
	private RequestAuditLogger requestLogger;

	@Autowired
	private MessageSource messageSource;

	@Autowired
	private AssignmentService assignmentService;

	@Autowired
	private PNumberDao pNumberDao;

	@Autowired
	private SENumberDao seNumberDao;

	@RequestLoggable(logEvent = RequestLogEvent.APPROVE)
	@PostMapping("/{requestId}/approve")
	public ResponseEntity<?> approveRequest(@PathVariable long requestId, @RequestBody(required = false) LocalDate newEndDate) {
		RoleRequest request = requestService.getRoleRequestById(requestId)
			.orElseThrow();

		if (!requestService.canApprove(request)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not allowed to deny this request");
		}
		if (newEndDate != null) {
			request.setEndDate(newEndDate);
			requestService.saveNoLog(request);
		}

		requestService.approveRequest(request);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping("/tag/request/{requestId}")
	public ResponseEntity<?> assignRequest(@PathVariable long requestId) {
		String userId = SecurityUtil.getUserId();
		requestService.assignRequestToUser(requestId, userId);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequestLoggable(logEvent = RequestLogEvent.DENY)
	@PostMapping("/{requestId}/deny")
	public ResponseEntity<?> denyRequest(@PathVariable long requestId, @RequestBody(required = false) String reason) {
		RoleRequest request = requestService.getRoleRequestById(requestId)
			.orElseThrow();

		if (!requestService.canApprove(request)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not allowed to deny this request");
		}

		requestService.rejectRequest(request, reason);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	private record RequestDTO(String userUuid, Long positionId, List<Long> userRoles, List<Long> roleGroups,
							  String reason,
							  List<RequestConstraint> constraints, LocalDate startDate, LocalDate stopDate) {
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

		if (requestDTO.startDate() != null && requestDTO.stopDate() != null) {
			if (requestDTO.startDate().isAfter(requestDTO.stopDate())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Start date must be before or equal to stop date");
			}
			if (requestDTO.stopDate.isBefore(LocalDate.now())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Stop date cannot be set in the past");
			}
		}

		User receiver = userService.getOptionalByUuid(requestDTO.userUuid).orElseThrow(() -> new NoSuchElementException("receiving user does not exist"));
		OrgUnit orgUnit = positionService.getById(requestDTO.positionId).getOrgUnit();
		if (orgUnit == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find organisation unit for request");
		}

		List<RoleRequest> requestGroup = new ArrayList<>();

		// Batch fetch all constraint-related data upfront (max 4 queries total)
		List<RoleConstraint> allRoleConstraints = requestDTO.constraints.stream()
			.flatMap(c -> c.roleConstraints().stream())
			.toList();

		Map<String, ConstraintType> constraintTypeMap = constraintTypeService.getByUuidsAsMap(
			allRoleConstraints.stream().map(RoleConstraint::typeUuid).distinct().toList()
		);
		Map<Long, SystemRole> systemRoleMap = systemRoleService.getByIdsAsMap(
			allRoleConstraints.stream().map(RoleConstraint::systemRoleId).distinct().toList()
		);
		Map<String, String> constraintLabels = buildConstraintLabelMap(allRoleConstraints, constraintTypeMap);

		//Create requests for UserRoles
		for (Long userRoleId : requestDTO.userRoles) {
			final UserRole userRole = userRoleService.getOptionalById(userRoleId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find requested user role"));
			final RoleRequest request = toBasicRoleRequest(receiver, requester, requestDTO.reason, orgUnit, groupUuid,
				approverOptionService.getInheritedApproverOption(userRole), requestDTO.startDate, requestDTO.stopDate);
			request.setUserRole(userRole);

			//check if allowed to request
			List<RequestableBy> globalRequesterSetting = settingsService.getRolerequestRequester();
			if (!requestService.canRequest(request.getUserRole(), receiver, orgUnit, globalRequesterSetting)) {
				log.error("User {} did not have permission to request the userrole: {}", requester.getUserId(), userRole.getName());
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not allowed to request this");
			}

			//Map the constraints to RequestPostponedConstraints and set them for the request
			request.setRequestPostponedConstraints(
				requestDTO.constraints.stream()
					.filter(constr ->
						constr.userRoleId == request.getUserRole().getId())
					.flatMap(requestConstraint ->
						requestConstraint.roleConstraints().stream().map(roleConstraint -> {
							ConstraintType constraintType = constraintTypeMap.get(roleConstraint.typeUuid());
							return RequestPostponedConstraint.builder()
								.constraintType(constraintType)
								.systemRole(systemRoleMap.get(roleConstraint.systemRoleId()))
								.value(roleConstraint.value())
								.label(resolveConstraintLabel(roleConstraint, constraintType, constraintLabels))
								.roleRequest(request)
								.build();
						})
					).toList()
			);

			requestGroup.add(request);
		}

		//Create requests for Rolegroups
		for (Long roleGroupId : requestDTO.roleGroups) {
			RoleGroup roleGroup = roleGroupService.getOptionalById(roleGroupId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find requested role group"));
			RoleRequest request = toBasicRoleRequest(receiver, requester, requestDTO.reason, orgUnit, groupUuid,
				approverOptionService.getInheritedApproverOption(roleGroup), requestDTO.startDate, requestDTO.stopDate);
			request.setRoleGroup(roleGroup);

			//check if allowed to request
			if (!requestService.canRequest(requester, request.getRoleGroup(), receiver, orgUnit)) {
				log.warn("User did not have permission to request one of the requested rolegroups");
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User not allowed to request this");
			}

			requestGroup.add(request);
		}

		//Save all new requests
		for (RoleRequest request : requestGroup) {
			requestService.saveNewRequestWithLog(request);
		}


		//Handle automatic approval of those with the corresponding setting
		requestGroup.stream()
			.filter(request -> request.getUserRole() == null ?
				approverOptionService.getInheritedApproverOption(request.getRoleGroup()).contains(ApprovableBy.AUTOMATIC)
				: approverOptionService.getInheritedApproverOption(request.getUserRole()).contains(ApprovableBy.AUTOMATIC)
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
		List<RequestableBy> globalRequesterSetting = settingsService.getRolerequestRequester();
		if (request.getUserRole() != null && !requestService.canRequest(request.getUserRole(), loggedInUser, request.getOrgUnit(), globalRequesterSetting)) {
			throw new SecurityException("User not allowed to cancel this request");
		} else if (request.getRoleGroup() != null && !requestService.canRequest(loggedInUser, request.getRoleGroup(), loggedInUser, request.getOrgUnit())) {
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

	private RoleRequest toBasicRoleRequest(User receiver, User requester, String reason, OrgUnit orgUnit, UUID groupIdentifier, List<ApprovableBy> whoCanApprove, LocalDate startDate, LocalDate endDate) {
		return RoleRequest.builder()
			.requester(requester)
			.receiver(receiver)
			.reason(reason)
			.startDate(startDate)
			.endDate(endDate)
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
	@Transactional(readOnly = true)
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
				calculateCanRequestRemoval(loggedInUser, user)
			))
			.toList();

		return RequestService.toDatatablesOutput(userroleOutput, allUserRolesDTOs);
	}

	private boolean calculateCanRequestRemoval(User requester, User user) {
		final Set<CurrentAssignment> currentAssignments = assignmentService.getByUserIncludingInactive(user);
		for (var assignment : currentAssignments) {
			final AssignedThrough assignedThrough = assignmentService.getAssignedThrough(assignment);
			if (assignment.getRoleGroup() != null) {
				final var roleGroup = assignment.getRoleGroup();
				if (assignedThrough == AssignedThrough.DIRECT
					&& requestService.canRequest(requester, roleGroup, user, assignment.getOrgUnit())) {
					return true;
				}
			} else {
				final var userRole =  assignment.getUserRole();
				if (assignedThrough == AssignedThrough.DIRECT
					&& requestService.canRequest(userRole, user, assignment.getOrgUnit(), settingsService.getRolerequestRequester())) {
					return true;
				}
			}
		}

		return false;
	}

	private RoleRequest handleCreateRemovalRequest(User requester, User receiver, Long userRoleAssignmentId, Long roleGroupAssignmentId, String reason, UUID groupUuid) {

		// set userrole-specifics
		OrgUnit orgUnit = null;
		List<ApprovableBy> approverOption = null;
		UserRole userRole;
		if (userRoleAssignmentId != null) {
			UserUserRoleAssignment assignment = receiver.getUserRoleAssignments().stream()
				.filter(u -> u.getId() == userRoleAssignmentId).findFirst()
				.orElseThrow(() -> new NoSuchElementException("receiving user does not have the userUserRoleAssignment with id " + userRoleAssignmentId));
			userRole = assignment.getUserRole();
			approverOption = approverOptionService.getInheritedApproverOption(userRole);

			// check if allowed to request
			List<RequestableBy> globalRequesterSetting = settingsService.getRolerequestRequester();
			if (!requestService.canRequest(userRole, requester, assignment.getOrgUnit(), globalRequesterSetting)) {
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
			if (!requestService.canRequest(requester, roleGroup, requester, assignment.getOrgUnit())) {
				throw new SecurityException("User not allowed to request this");
			}

			orgUnit = assignment.getOrgUnit();

		} else {
			roleGroup = null;
		}

		// create 'remove' request
		RoleRequest request = toBasicRoleRequest(receiver, requester, reason, orgUnit, groupUuid, approverOption, null, null);
		request.setUserRole(userRole);
		request.setRoleGroup(roleGroup);
		request.setRequestAction(RequestAction.REMOVE);

		request = requestService.saveNoLog(request);

		// handle automatic approval if enabled
		boolean automaticApproval = request.getUserRole() == null ?
			approverOptionService.getInheritedApproverOption(request.getRoleGroup()).contains(ApprovableBy.AUTOMATIC)
			: approverOptionService.getInheritedApproverOption(request.getUserRole()).contains(ApprovableBy.AUTOMATIC);

		if (automaticApproval) {
			requestService.approveRequest(request);
			// manually log automatic approval
			requestLogger.logRequest(RequestLogEvent.APPROVE, request, messageSource.getMessage("requestmodule.log.event.request.automaticapprove", null, Locale.getDefault()));
		}

		return request;
	}

	private Map<String, String> buildConstraintLabelMap(List<RoleConstraint> constraints, Map<String, ConstraintType> constraintTypeMap) {
		// Collect P-number and SE-number values by checking the constraint type's entity ID
		List<String> pNumberCodes = constraints.stream()
			.filter(c -> {
				ConstraintType type = constraintTypeMap.get(c.typeUuid());
				return type != null && Constants.PNUMBER_CONSTRAINT_ENTITY_ID.equals(type.getEntityId());
			})
			.map(RoleConstraint::value)
			.distinct()
			.toList();

		List<String> seNumberCodes = constraints.stream()
			.filter(c -> {
				ConstraintType type = constraintTypeMap.get(c.typeUuid());
				return type != null && Constants.SENUMBER_CONSTRAINT_ENTITY_ID.equals(type.getEntityId());
			})
			.map(RoleConstraint::value)
			.distinct()
			.toList();

		// Batch fetch labels to avoid excessive number of queries
		Map<String, String> labels = new java.util.HashMap<>();

		if (!pNumberCodes.isEmpty()) {
			pNumberDao.findByCodeIn(pNumberCodes)
				.forEach(p -> labels.put(Constants.PNUMBER_CONSTRAINT_ENTITY_ID + ":" + p.getCode(), p.getName()));
		}

		if (!seNumberCodes.isEmpty()) {
			seNumberDao.findByCodeIn(seNumberCodes)
				.forEach(s -> labels.put(Constants.SENUMBER_CONSTRAINT_ENTITY_ID + ":" + s.getCode(), s.getName()));
		}

		// Add labels for COMBO-type constraints from their value sets
		constraints.forEach(c -> {
			ConstraintType type = constraintTypeMap.get(c.typeUuid());
			if (type == null) {
				return;
			}
			if (Constants.PNUMBER_CONSTRAINT_ENTITY_ID.equals(type.getEntityId()) ||
				Constants.SENUMBER_CONSTRAINT_ENTITY_ID.equals(type.getEntityId())) {
				return;
			}
			if (type.getUiType() == ConstraintUIType.COMBO_SINGLE || type.getUiType() == ConstraintUIType.COMBO_MULTI) {
				type.getValueSet().stream()
					.filter(vs -> vs.getConstraintKey().equals(c.value()))
					.findFirst()
					.ifPresent(vs -> labels.put(type.getEntityId() + ":" + c.value(), vs.getConstraintValue()));
			}
		});

		return labels;
	}

	/**
	 * Retrieves the fetched label for this constraint from the map
	 */
	private String resolveConstraintLabel(RoleConstraint constraint, ConstraintType constraintType, Map<String, String> labelMap) {
		if (constraintType == null) {
			return null;
		}
		return labelMap.get(constraintType.getEntityId() + ":" + constraint.value());
	}

}
