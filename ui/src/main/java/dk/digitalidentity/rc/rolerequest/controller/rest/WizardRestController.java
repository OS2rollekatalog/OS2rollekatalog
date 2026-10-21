package dk.digitalidentity.rc.rolerequest.controller.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.CombinedRoleView;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleView;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.rolerequest.model.dto.CombinedRoleDTO;
import dk.digitalidentity.rc.rolerequest.model.entity.OrgUnitRoleGroupCache;
import dk.digitalidentity.rc.rolerequest.model.entity.OrgUnitUserRoleCache;
import dk.digitalidentity.rc.rolerequest.service.ApproverOptionService;
import dk.digitalidentity.rc.rolerequest.service.OrgUnitRoleCacheService;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import dk.digitalidentity.rc.rolerequest.service.permission.RequestableRoleService;
import dk.digitalidentity.rc.rolerequest.service.permission.ResolvedPermissionService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.security.RequireNoRole;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireNoRole
@RestController
@RequiredArgsConstructor
public class WizardRestController {
	private final UserService userService;
	private final RequestService rolerequestService;
	private final RequestableRoleService requestableRoleService;
	private final ResolvedPermissionService resolvedPermissionService;
	private final ApproverOptionService approverOptionService;
	private final OrgUnitRoleCacheService orgUnitRoleCacheService;
	private final UserRoleService userRoleService;
	private final AssignmentService assignmentService;

	public record RoleGroupDTO(long id, String name, String description, String approver, boolean alreadyAssigned) {
	}

	public record UserRoleDTO(long id, String itSystemName, String name, String description, String approver,
							  boolean alreadyAssigned, boolean hasConstraints) {
	}

	@Transactional(readOnly = true)
	@GetMapping("/rest/rolerequest/wizard/existingroles/{receiverId}")
	public List<CombinedRoleDTO> existingRoles(@PathVariable final String receiverId) {
		final User requestForUser = userService.getOptionalByUuid(receiverId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + receiverId + " not found"));

		final Set<CurrentAssignment> assignments = assignmentService.getByUserIncludingInactive(requestForUser);
		final Map<Long, String> roleGroupApprovers = roleGroupApproverTexts(assignments.stream().filter(a -> a.getRoleGroup() != null).map(a -> a.getRoleGroup().getId()).toList());
		final Map<Long, String> userRoleApprovers = userRoleApproverTexts(assignments.stream().filter(a -> a.getUserRole() != null).map(a -> a.getUserRole().getId()).toList());

		final List<CombinedRoleDTO> result = new ArrayList<>();
		final Set<Long> userRoleIdsInRoleGroups = new HashSet<>();
		final Set<Long> seenRoleGroupIds = new HashSet<>();

		for (CurrentAssignment assignment : assignments) {
			if (assignment.getRoleGroup() == null) {
				continue;
			}

			final RoleGroup roleGroup = assignment.getRoleGroup();
			if (!seenRoleGroupIds.add(roleGroup.getId())) {
				continue;
			}

			String roleWithinRoleGroup = "";
			if (roleGroup.getUserRoleAssignments() != null) {
				userRoleIdsInRoleGroups.addAll(
					roleGroup.getUserRoleAssignments().stream()
						.map(rgAssignment -> rgAssignment.getUserRole().getId())
						.collect(Collectors.toSet())
				);

				roleWithinRoleGroup = roleGroup.getUserRoleAssignments().stream()
					.map(rgAssignment -> rgAssignment.getUserRole().getName())
					.collect(Collectors.joining(", "));
			}

			result.add(new CombinedRoleDTO(
				roleGroup.getId(),
				"roleGroup",
				null,
				roleGroup.getName(),
				roleGroup.getDescription(),
				roleGroupApprovers.get(roleGroup.getId()),
				true,
				false,
				roleWithinRoleGroup
			));
		}

		final Set<Long> seenUserRoleIds = new HashSet<>();
		for (CurrentAssignment assignment : assignments) {
			if (assignment.getUserRole() == null) {
				continue;
			}

			final UserRole userRole = assignment.getUserRole();
			if (userRoleIdsInRoleGroups.contains(userRole.getId()) || !seenUserRoleIds.add(userRole.getId())) {
				continue;
			}

			result.add(new CombinedRoleDTO(
				userRole.getId(),
				"userRole",
				userRole.getItSystem().getName(),
				userRole.getName(),
				userRole.getDescription(),
				userRoleApprovers.get(userRole.getId()),
				true,
				hasPostponedConstraints(userRole.getId()),
				""
			));
		}

		return result;
	}

	@Transactional(readOnly = true)
	@GetMapping("/rest/rolerequest/wizard/recommendedrolegroups/{receiverId}")
	public List<RoleGroupDTO> recommendedRoleGroups(@PathVariable final String receiverId,
													@RequestParam long position, @RequestParam boolean hideAlreadyAssigned) {
		final User requestForUser = userService.getOptionalByUuid(receiverId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + receiverId + " not found"));
		final Position matchPosition = requestForUser.getPositions().stream().filter(p -> Objects.equals(p.getId(), position)).findAny()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Position with id " + position + " not assigned to user"));
		final OrgUnit orgUnit = matchPosition.getOrgUnit();
		List<OrgUnitRoleGroupCache> recommendedRoleGroups = orgUnitRoleCacheService.getRoleGroups(orgUnit);

		Set<CurrentAssignment> currentAssignments = assignmentService.getAllRoleGroupAssignmentsByUserIncludingInactive(requestForUser);
		Set<Long> assignedRoleGroupIds = currentAssignments.stream()
			.map(a -> a.getRoleGroup().getId())
			.collect(Collectors.toSet());

		final User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		final Set<Long> requestableRoleGroupIds = requestableRoleService.requestableRoleGroupIds(loggedInUser, requestForUser, orgUnit,
			recommendedRoleGroups.stream().map(cache -> cache.getRoleGroup().getId()).toList());

		final Map<Long, String> roleGroupApprovers = roleGroupApproverTexts(recommendedRoleGroups.stream().map(cache -> cache.getRoleGroup().getId()).toList());
		List<RoleGroupDTO> recommendedRoleGroupDTOs = new ArrayList<>();
		for (OrgUnitRoleGroupCache recommendedRoleGroup : recommendedRoleGroups) {
			RoleGroup currentRoleGroup = recommendedRoleGroup.getRoleGroup();
			if (!(hideAlreadyAssigned && assignedRoleGroupIds.contains(currentRoleGroup.getId()))
				&& requestableRoleGroupIds.contains(currentRoleGroup.getId())) {
				recommendedRoleGroupDTOs.add(new RoleGroupDTO(currentRoleGroup.getId(), currentRoleGroup.getName(), currentRoleGroup.getDescription(),
					roleGroupApprovers.get(currentRoleGroup.getId()), assignedRoleGroupIds.contains(currentRoleGroup.getId())));
			}
		}

		return recommendedRoleGroupDTOs;
	}

	@Transactional(readOnly = true)
	@GetMapping("/rest/rolerequest/wizard/recommendeduserroles/{receiverId}")
	public List<UserRoleDTO> recommendedUserRoles(@PathVariable final String receiverId,
		@RequestParam long position,
		@RequestParam boolean hideAlreadyAssigned) {
		final User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		final User requestForUser = userService.getOptionalByUuid(receiverId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + receiverId + " not found"));
		final Position matchPosition = requestForUser.getPositions().stream().filter(p -> Objects.equals(p.getId(), position)).findAny()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Position with id " + position + " not assigned to user"));
		final OrgUnit orgUnit = matchPosition.getOrgUnit();
		final List<OrgUnitUserRoleCache> recommendedUserRoles = orgUnitRoleCacheService.getUserRoles(orgUnit);

		// Get all assigned userRoles
		Set<CurrentAssignment> assignments = assignmentService.getByUserIncludingInactive(requestForUser);
		final Set<Long> assignedUserRoleIds = assignments.stream()
			.filter(a -> a.getUserRole() != null)
			.map(a -> a.getUserRole().getId())
			.collect(Collectors.toSet());

		final Set<Long> requestableUserRoleIds = requestableRoleService.requestableUserRoleIds(loggedInUser, requestForUser, orgUnit,
			recommendedUserRoles.stream().map(cache -> cache.getUserRole().getId()).toList());
		final Map<Long, String> userRoleApprovers = userRoleApproverTexts(recommendedUserRoles.stream().map(cache -> cache.getUserRole().getId()).toList());
		final List<UserRoleDTO> recommendedUserRolesDTOs = new ArrayList<>();
		for (OrgUnitUserRoleCache recommendedUserRole : recommendedUserRoles) {
			final UserRole currentUserRole = recommendedUserRole.getUserRole();
			if (!(hideAlreadyAssigned && assignedUserRoleIds.contains(currentUserRole.getId()))
				&& requestableUserRoleIds.contains(currentUserRole.getId())) {
				recommendedUserRolesDTOs.add(
					new UserRoleDTO(
						currentUserRole.getId(),
						currentUserRole.getItSystem().getName(),
						currentUserRole.getName(),
						currentUserRole.getDescription(),
						userRoleApprovers.get(currentUserRole.getId()),
						assignedUserRoleIds.contains(currentUserRole.getId()),
						hasPostponedConstraints(currentUserRole.getId())
					)
				);
			}
		}
		return recommendedUserRolesDTOs;
	}

	@Transactional(readOnly = true)
	@PostMapping("/rest/rolerequest/wizard/alluserroles/{receiverId}")
	public DataTablesOutput<UserRoleDTO> allUserRolesFragment(@RequestBody DataTablesInput input, @PathVariable String receiverId, @RequestParam long position, @RequestParam boolean hideAlreadyAssigned)  {
		final User requestingUser = userService.getOptionalByUserId(SecurityUtil.getUserId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with id " + SecurityUtil.getUserId() + " not found"));

		final User receiver = userService.getOptionalByUuid(receiverId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + receiverId + " not found"));
		final Position matchPosition = receiver.getPositions().stream().filter(p -> Objects.equals(p.getId(), position)).findAny()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Position with id " + position + " not assigned to user"));

		// Get all assigned userRoles
		Set<CurrentAssignment> assignments = assignmentService.getByUserIncludingInactive(receiver);
		List<Long> assignedUserRoleIds = assignments.stream()
			.filter(a -> a.getUserRole() != null)
			.map(a -> a.getUserRole().getId())
			.toList();

		DataTablesOutput<UserRoleView> userroleOutput = rolerequestService.getRequestableUserRolesAsDatatable(input, requestingUser, receiver, matchPosition.getOrgUnit());
		List<UserRoleDTO> allUserRolesDTOs = userroleOutput.getData().stream()
			.map(userRole -> new UserRoleDTO(
				userRole.getId(),
				userRole.getItSystemName(),
				userRole.getName(),
				userRole.getDescription(),
				approverOptionService.getApproverOptionsAsString(userRole.getEffectiveApproverPermission()),
				assignedUserRoleIds.contains(userRole.getId()),
				hasPostponedConstraints(userRole.getId()))
			)
			.toList();

		if (hideAlreadyAssigned) {
			allUserRolesDTOs = allUserRolesDTOs.stream().filter(ur -> !ur.alreadyAssigned).toList();
		}

		return RequestService.toDatatablesOutput(userroleOutput, allUserRolesDTOs);
	}

	@Transactional(readOnly = true)
	@PostMapping("/rest/rolerequest/wizard/allcombined/{receiverId}")
	public DataTablesOutput<CombinedRoleDTO> allCombinedFragment(
		@RequestBody DataTablesInput input,
		@PathVariable String receiverId,
		@RequestParam long position,
		@RequestParam boolean hideAlreadyAssigned) {

		final User requestingUser = userService.getOptionalByUserId(SecurityUtil.getUserId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with id " + SecurityUtil.getUserId() + " not found"));

		final User receiver = userService.getOptionalByUuid(receiverId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + receiverId + " not found"));

		final Position matchPosition = receiver.getPositions().stream()
			.filter(p -> Objects.equals(p.getId(), position))
			.findAny()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Position with id " + position + " not assigned to user"));

		DataTablesOutput<CombinedRoleView> output = rolerequestService.getRequestableCombinedRolesAsDatatable(
			input, requestingUser, receiver, matchPosition.getOrgUnit(), hideAlreadyAssigned
		);

		// Get all assigned userRoles
		Set<CurrentAssignment> assignments = assignmentService.getByUserIncludingInactive(receiver);
		List<Long> assignedUserRoleIds = assignments.stream()
			.filter(a -> a.getUserRole() != null)
			.map(a -> a.getUserRole().getId())
			.toList();

		List<Long> assignedRoleGroupIds = assignments.stream()
			.filter(a -> a.getRoleGroup() != null)
			.map(a -> a.getRoleGroup().getId())
			.toList();

		List<CombinedRoleDTO> dtos = output.getData().stream()
			.map(role -> {
				boolean alreadyAssigned = role.getType().equals("userRole")
					? assignedUserRoleIds.contains(role.getId())
					: assignedRoleGroupIds.contains(role.getId());

				boolean hasConstraints = role.getType().equals("userRole") && hasPostponedConstraints(role.getId());

				return new CombinedRoleDTO(
					role.getId(),
					role.getType(),
					role.getItSystemName(),
					role.getName(),
					role.getDescription(),
					approverOptionService.getApproverOptionsAsString(role.getEffectiveApproverPermission()),
					alreadyAssigned,
					hasConstraints,
					""
				);
			})
			.toList();

		return RequestService.toDatatablesOutput(output, dtos);
	}

	@Transactional(readOnly = true)
	@GetMapping("/rest/rolerequest/wizard/recommendedcombined/{receiverId}")
	public List<CombinedRoleDTO> recommendedCombined(
		@PathVariable final String receiverId,
		@RequestParam long position,
		@RequestParam boolean hideAlreadyAssigned) {

		final User requestingUser = userService.getOptionalByUserId(SecurityUtil.getUserId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with id " + SecurityUtil.getUserId() + " not found"));

		final User requestForUser = userService.getOptionalByUuid(receiverId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + receiverId + " not found"));

		final Position matchPosition = requestForUser.getPositions().stream()
			.filter(p -> Objects.equals(p.getId(), position))
			.findAny()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Position with id " + position + " not assigned to user"));

		final OrgUnit orgUnit = matchPosition.getOrgUnit();

		// Get all assigned userRoles
		final Set<CurrentAssignment> assignments = assignmentService.getByUserIncludingInactive(requestForUser);
		List<Long> assignedUserRoleIds = assignments.stream()
			.filter(a -> a.getUserRole() != null)
			.map(a -> a.getUserRole().getId())
			.toList();

		final List<Long> assignedRoleGroupIds = assignments.stream()
			.filter(a -> a.getRoleGroup() != null)
			.map(a -> a.getRoleGroup().getId())
			.toList();


		final List<CombinedRoleDTO> combinedRoles = new ArrayList<>();

		final List<OrgUnitRoleGroupCache> recommendedRoleGroups = orgUnitRoleCacheService.getRoleGroups(orgUnit);
		final Set<Long> userRoleIdsInRoleGroups = new HashSet<>();
		final Set<Long> requestableRoleGroupIds = requestableRoleService.requestableRoleGroupIds(requestingUser, requestForUser, orgUnit,
			recommendedRoleGroups.stream().map(cache -> cache.getRoleGroup().getId()).toList());
		final Map<Long, String> roleGroupApprovers = roleGroupApproverTexts(recommendedRoleGroups.stream().map(cache -> cache.getRoleGroup().getId()).toList());

		for (OrgUnitRoleGroupCache recommendedRoleGroup : recommendedRoleGroups) {
			final RoleGroup currentRoleGroup = recommendedRoleGroup.getRoleGroup();
			String roleGroupUserRoles = "";
			if (currentRoleGroup.getUserRoleAssignments() != null) {
				userRoleIdsInRoleGroups.addAll(
					currentRoleGroup.getUserRoleAssignments().stream()
						.map(assignment -> assignment.getUserRole().getId())
						.collect(Collectors.toSet())
				);

				roleGroupUserRoles = currentRoleGroup.getUserRoleAssignments().stream()
					.map(assignment -> assignment.getUserRole().getName())
					.collect(Collectors.joining(", "));
			}

			boolean isAlreadyAssigned = assignedRoleGroupIds.contains(currentRoleGroup.getId());

			if (!(hideAlreadyAssigned && isAlreadyAssigned)
				&& requestableRoleGroupIds.contains(currentRoleGroup.getId())) {
				combinedRoles.add(
					new CombinedRoleDTO(
						currentRoleGroup.getId(),
						"roleGroup",
						null,
						currentRoleGroup.getName(),
						currentRoleGroup.getDescription(),
						roleGroupApprovers.get(currentRoleGroup.getId()),
						isAlreadyAssigned,
						false,
						roleGroupUserRoles
					)
				);
			}
		}

		final List<OrgUnitUserRoleCache> recommendedUserRoles = orgUnitRoleCacheService.getUserRoles(orgUnit);
		final Set<Long> requestableUserRoleIds = requestableRoleService.requestableUserRoleIds(requestingUser, requestForUser, orgUnit,
			recommendedUserRoles.stream().map(cache -> cache.getUserRole().getId()).toList());
		final Map<Long, String> userRoleApprovers = userRoleApproverTexts(recommendedUserRoles.stream().map(cache -> cache.getUserRole().getId()).toList());
		for (OrgUnitUserRoleCache recommendedUserRole : recommendedUserRoles) {
			final UserRole currentUserRole = recommendedUserRole.getUserRole();

			if (userRoleIdsInRoleGroups.contains(currentUserRole.getId())) {
				log.debug("Excluding user role '{}' (id: {}) as it is already part of a recommended role group", currentUserRole.getName(), currentUserRole.getId());
				continue;
			}

			boolean isAlreadyAssigned = assignedUserRoleIds.contains(currentUserRole.getId());

			if (!(hideAlreadyAssigned && isAlreadyAssigned)
				&& requestableUserRoleIds.contains(currentUserRole.getId())) {
				combinedRoles.add(
					new CombinedRoleDTO(
						currentUserRole.getId(),
						"userRole",
						currentUserRole.getItSystem().getName(),
						currentUserRole.getName(),
						currentUserRole.getDescription(),
						userRoleApprovers.get(currentUserRole.getId()),
						isAlreadyAssigned,
						hasPostponedConstraints(currentUserRole.getId()),
						""
					)
				);
			}
		}

		return combinedRoles;
	}

	@Transactional(readOnly = true)
	@GetMapping("/rest/rolerequest/wizard/allrolegroups/{receiverId}")
	public List<RoleGroupDTO> allRoleGroupsFragment(@PathVariable String receiverId,
													@RequestParam long position,
													@RequestParam boolean hideAlreadyAssigned) {
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (loggedInUser == null) {
			throw new UsernameNotFoundException("Could not find logged in user");
		}
		User receiver = userService.getOptionalByUuid(receiverId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + receiverId + " not found"));
		final Position matchPosition = receiver.getPositions().stream().filter(p -> Objects.equals(p.getId(), position)).findAny()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Position with id " + position + " not assigned to user"));


		Set<CurrentAssignment> currentAssignments = assignmentService.getAllRoleGroupAssignmentsByUserIncludingInactive(receiver);
		List<Long> assignedRoleGroupIds = currentAssignments.stream()
			.map(a -> a.getRoleGroup().getId())
			.toList();
		List<RoleGroup> rolegroupOutput = rolerequestService.getRequestableRoleGroupsAsDatatable(loggedInUser, receiver, matchPosition.getOrgUnit());
		final Map<Long, String> roleGroupApprovers = roleGroupApproverTexts(rolegroupOutput.stream().map(RoleGroup::getId).toList());
		List<RoleGroupDTO> allRoleGroupDTOs = rolegroupOutput.stream()
			.map(roleGroup -> new RoleGroupDTO(
				roleGroup.getId(),
				roleGroup.getName(),
				roleGroup.getDescription(),
				roleGroupApprovers.get(roleGroup.getId()),
				assignedRoleGroupIds.contains(roleGroup.getId())
			))
			.toList();

		if (hideAlreadyAssigned) {
			allRoleGroupDTOs = allRoleGroupDTOs.stream().filter( r -> !r.alreadyAssigned).toList();
		}

		return allRoleGroupDTOs;
	}

	private boolean hasPostponedConstraints(Long userRoleId) {
		final UserRole userRole = userRoleService.getById(userRoleId);
		boolean hasPostponedConstraints = false;
		for (SystemRoleAssignment systemRoleAssignment : userRole.getSystemRoleAssignments()) {
			boolean anyMatch = systemRoleAssignment.getConstraintValues().stream().anyMatch(SystemRoleAssignmentConstraintValue::isPostponed);
			if (anyMatch) {
				hasPostponedConstraints = true;
				break;
			}
		}
		return hasPostponedConstraints;
	}


	private Map<Long, String> userRoleApproverTexts(Collection<Long> userRoleIds) {
		return resolvedPermissionService.userRoleApproverPermissions(userRoleIds).entrySet().stream()
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> approverOptionService.getApproverOptionsAsString(entry.getValue())));
	}

	private Map<Long, String> roleGroupApproverTexts(Collection<Long> roleGroupIds) {
		return resolvedPermissionService.roleGroupApproverPermissions(roleGroupIds).entrySet().stream()
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> approverOptionService.getApproverOptionsAsString(entry.getValue())));
	}
}
