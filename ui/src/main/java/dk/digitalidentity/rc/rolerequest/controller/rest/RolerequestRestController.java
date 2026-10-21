package dk.digitalidentity.rc.rolerequest.controller.rest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.PNumberDao;
import dk.digitalidentity.rc.dao.SENumberDao;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.rolerequest.RequestConstants;
import dk.digitalidentity.rc.rolerequest.log.RequestLogEvent;
import dk.digitalidentity.rc.rolerequest.log.RequestLoggable;
import dk.digitalidentity.rc.rolerequest.model.dto.RequestConstraintDTO;
import dk.digitalidentity.rc.rolerequest.model.dto.RequestedRole;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestPostponedConstraint;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.service.permission.ApprovableRequestService;
import dk.digitalidentity.rc.rolerequest.service.permission.RequestableRoleService;
import dk.digitalidentity.rc.rolerequest.service.RequestConstraintService;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import dk.digitalidentity.rc.security.RequireNoRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.PositionService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.util.Failure;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireNoRole
@RestController
public class RolerequestRestController {

	@Autowired
	private RequestService requestService;

	@Autowired
	private UserService userService;

	@Autowired
	private PositionService positionService;

	@Autowired
	private ConstraintTypeService constraintTypeService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private RequestableRoleService requestableRoleService;

	@Autowired
	private ApprovableRequestService approvableRequestService;

	@Autowired
	private AssignmentService assignmentService;

	@Autowired
	private PNumberDao pNumberDao;

	@Autowired
	private SENumberDao seNumberDao;

	@Autowired
	private RequestConstraintService requestConstraintService;

	@PostMapping("/rest/rolerequest/constraint/create")
	public ResponseEntity<?> createConstraint(@RequestBody RequestConstraintDTO constraintDTO) {
		dk.digitalidentity.rc.rolerequest.model.entity.RequestConstraint constraint = requestConstraintService.save(
			dk.digitalidentity.rc.rolerequest.model.entity.RequestConstraint.builder()
				.value(constraintDTO.getValue())
				.build());

		return new ResponseEntity<>(RequestConstraintDTO.builder()
			.id(constraint.getId())
			.value(constraint.getValue())
			.build(), HttpStatus.OK);
	}

	@DeleteMapping("/rest/rolerequest/constraint/delete")
	public ResponseEntity<?> deleteConstraints(@RequestParam List<Long> constraintIds) {
		constraintIds.forEach(requestConstraintService::deleteConstraint);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequestLoggable(logEvent = RequestLogEvent.APPROVE)
	@PostMapping("/rest/rolerequest/{requestId}/approve")
	public ResponseEntity<String> approveRequest(@PathVariable long requestId, @RequestBody(required = false) LocalDate newEndDate) {
		RoleRequest request = requestService.getRoleRequestById(requestId)
			.orElseThrow();

		if (!approvableRequestService.canApprove(request, userService.getByUserId(SecurityUtil.getUserId()))) {
			return ResponseEntity.badRequest().body("User not allowed to approve this request");
		}
		if (newEndDate != null) {
			request.setEndDate(newEndDate);
			requestService.saveNoLog(request);
		}

		return requestService.approveRequest(request);
	}

	@PostMapping("/rest/rolerequest/tag/request/{requestId}")
	public ResponseEntity<?> assignRequest(@PathVariable long requestId) {
		String userId = SecurityUtil.getUserId();
		requestService.assignRequestToUser(requestId, userId);
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequestLoggable(logEvent = RequestLogEvent.DENY)
	@PostMapping("/rest/rolerequest/{requestId}/deny")
	public ResponseEntity<?> denyRequest(@PathVariable long requestId, @RequestBody(required = false) String reason) {
		RoleRequest request = requestService.getRoleRequestById(requestId)
			.orElseThrow();

		if (!approvableRequestService.canApprove(request, userService.getByUserId(SecurityUtil.getUserId()))) {
			return ResponseEntity.badRequest().body("User not allowed to deny this request");
		}

		return requestService.rejectRequest(request, reason);
	}

	private static void requireValidReason(String reason) {
		if (reason != null && reason.length() > RequestConstants.REASON_MAX_LENGTH) {
			throw Failure.illegalArgument(log, "Reason is {} characters, the maximum is {}", reason.length(), RequestConstants.REASON_MAX_LENGTH);
		}
	}

	private record RequestDTO(String userUuid, Long positionId, List<Long> userRoles, List<Long> roleGroups,
							  String reason,
							  List<RequestConstraint> constraints, LocalDate startDate, LocalDate stopDate) {
	}

	private record RequestConstraint(Long userRoleId, List<RoleConstraint> roleConstraints) {
	}

	private record RoleConstraint(Long systemRoleId, String typeUuid, String value) {
	}

	@PostMapping("/rest/rolerequest/wizard/save")
	public ResponseEntity<?> saveRequest(@RequestBody RequestDTO requestDTO) {
		requireValidReason(requestDTO.reason);
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

		List<RequestedRole> requestedRoles = Stream.concat(
				requestDTO.userRoles.stream().map(id -> RequestedRole.userRole(id, requestDTO.startDate, requestDTO.stopDate)),
				requestDTO.roleGroups.stream().map(id -> RequestedRole.roleGroup(id, requestDTO.startDate, requestDTO.stopDate)))
			.toList();
		List<RoleRequest> requestGroup = requestService.buildPermittedRoleRequests(
			requester, receiver, orgUnit, groupUuid, requestDTO.reason, requestedRoles, RequestAction.ADD);

		//Map the constraints to RequestPostponedConstraints and set them for the user role requests
		requestGroup.stream()
			.filter(request -> request.getUserRole() != null)
			.forEach(request -> request.setRequestPostponedConstraints(
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
			));

		requestService.saveRequestGroupWithLogAndAutoApprove(requestGroup, requester);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	private record RemoveRequestDTO(String userUuid, List<Long> userRoles, List<Long> roleGroups, String reason) {}
	@PostMapping("/rest/rolerequest/remove/wizard/save")
	public ResponseEntity<?> saveRemoveRequestFromWizard(@RequestBody RemoveRequestDTO requestDTO) {
		requireValidReason(requestDTO.reason);
		//Find users
		User requester = userService.getByUserId(SecurityUtil.getUserId());
		if (requester == null) {
			throw new NoSuchElementException("Logged in user does not exist");
		}

		User receiver = userService.getOptionalByUuid(requestDTO.userUuid).orElseThrow(() -> new NoSuchElementException("receiving user does not exist"));

		UUID groupUuid = UUID.randomUUID();

		List<RequestedRole> requestedRoles = Stream.concat(
				requestDTO.userRoles.stream().map(id -> RequestedRole.userRole(id, null, null)),
				requestDTO.roleGroups.stream().map(id -> RequestedRole.roleGroup(id, null, null)))
			.toList();
		List<RoleRequest> requestGroup = requestService.buildPermittedRoleRequests(
			requester, receiver, null, groupUuid, requestDTO.reason, requestedRoles, RequestAction.REMOVE);

		requestService.saveRequestGroupWithLogAndAutoApprove(requestGroup, requester);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequestLoggable(logEvent = RequestLogEvent.CANCEL)
	@DeleteMapping("/rest/rolerequest/{id}/cancel")
	public ResponseEntity<?> cancelRequest(@PathVariable Long id) {
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		RoleRequest request = requestService.getRoleRequestById(id)
			.orElseThrow();
		if (request.getUserRole() != null && !requestableRoleService.canRequest(loggedInUser, loggedInUser, request.getOrgUnit(), request.getUserRole())) {
			throw new SecurityException("User not allowed to cancel this request");
		} else if (request.getRoleGroup() != null && !requestableRoleService.canRequest(loggedInUser, loggedInUser, request.getOrgUnit(), request.getRoleGroup())) {
			throw new SecurityException("User not allowed to cancel this request");
		}

		requestService.deleteRolerequest(id);

		return new ResponseEntity<>(HttpStatus.OK);
	}


	private record RemovalRequestDTO(Long userRoleId, Long roleGroupId, String reason) {
	}

	@PostMapping("/rest/rolerequest/remove")
	public ResponseEntity<?> removeRequestForSelf(@RequestBody RemovalRequestDTO removalRequestDTO) {
		requireValidReason(removalRequestDTO.reason);
		//find current user
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (loggedInUser == null) {
			throw new NoSuchElementException("Logged in user does not exist");
		}

		List<RequestedRole> requestedRoles = new ArrayList<>();
		if (removalRequestDTO.userRoleId != null) {
			requestedRoles.add(RequestedRole.userRole(removalRequestDTO.userRoleId, null, null));
		}
		if (removalRequestDTO.roleGroupId != null) {
			requestedRoles.add(RequestedRole.roleGroup(removalRequestDTO.roleGroupId, null, null));
		}
		List<RoleRequest> requestGroup = requestService.buildPermittedRoleRequests(
			loggedInUser, loggedInUser, null, UUID.randomUUID(), removalRequestDTO.reason, requestedRoles, RequestAction.REMOVE);
		requestService.saveRequestGroupWithLogAndAutoApprove(requestGroup, loggedInUser);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	record EmployeeDTO(String uuid, String userId, String name, Set<String> positions, boolean hasRoles, boolean canRequestRemoval, boolean disabled) {
	}
	@PostMapping("/rest/rolerequest/employees")
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
				calculateCanRequestRemoval(loggedInUser, user),
				user.isDisabled()
			))
			.toList();

		return RequestService.toDatatablesOutput(userroleOutput, allUserRolesDTOs);
	}

	private boolean calculateCanRequestRemoval(User requester, User user) {
		// Only directly assigned roles/role groups can be requested removed
		final Set<CurrentAssignment> directAssignments = assignmentService.getByUserIncludingInactive(user).stream()
			.filter(assignment -> assignmentService.getAssignedThrough(assignment) == AssignedThrough.DIRECT)
			.collect(Collectors.toSet());
		final Set<Long> roleGroupIds = directAssignments.stream()
			.filter(assignment -> assignment.getRoleGroup() != null)
			.map(assignment -> assignment.getRoleGroup().getId())
			.collect(Collectors.toSet());
		final Set<Long> userRoleIds = directAssignments.stream()
			.filter(assignment -> assignment.getRoleGroup() == null && assignment.getUserRole() != null)
			.map(assignment -> assignment.getUserRole().getId())
			.collect(Collectors.toSet());

		return !requestableRoleService.removableRoleGroupIds(requester, user, roleGroupIds).isEmpty()
			|| !requestableRoleService.removableUserRoleIds(requester, user, userRoleIds).isEmpty();
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
