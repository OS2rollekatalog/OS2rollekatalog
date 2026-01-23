package dk.digitalidentity.rc.rolerequest.controller.rest;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.CombinedRoleView;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleView;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.dto.CombinedRoleDTO;
import dk.digitalidentity.rc.rolerequest.model.entity.OrgUnitRoleGroupCache;
import dk.digitalidentity.rc.rolerequest.model.entity.OrgUnitUserRoleCache;
import dk.digitalidentity.rc.rolerequest.service.ApproverOptionService;
import dk.digitalidentity.rc.rolerequest.service.OrgUnitRoleCacheService;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.RoleGroupAssignedToUser;
import dk.digitalidentity.rc.service.model.UserRoleAssignedToUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("rest/rolerequest/wizard")
@RequiredArgsConstructor
public class WizardRestController {
	private final UserService userService;
	private final RequestService rolerequestService;
	private final ApproverOptionService approverOptionService;
	private final OrgUnitRoleCacheService orgUnitRoleCacheService;
	private final SettingsService settingsService;
	private final UserRoleService userRoleService;
	private final RoleGroupService roleGroupService;

	public record RoleGroupDTO(long id, String name, String description, String approver, boolean alreadyAssigned) {
	}

	public record UserRoleDTO(long id, String itSystemName, String name, String description, String approver,
							  boolean alreadyAssigned, boolean hasConstraints) {
	}

	@Transactional(readOnly = true)
	@GetMapping("recommendedrolegroups/{receiverId}")
	public List<RoleGroupDTO> recommendedRoleGroups(@PathVariable final String receiverId,
													@RequestParam long position, @RequestParam boolean hideAlreadyAssigned) {
		final User requestForUser = userService.getOptionalByUuid(receiverId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + receiverId + " not found"));
		final Position matchPostion = requestForUser.getPositions().stream().filter(p -> p.getId() == position).findAny()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Position with id " + position + " not assigned to user"));
		final OrgUnit orgUnit = matchPostion.getOrgUnit();
		List<OrgUnitRoleGroupCache> recommendedRoleGroups = orgUnitRoleCacheService.getRoleGroups(orgUnit);

		List<RoleGroupAssignedToUser> assignedRoleGroups = userService.getAllRoleGroupsAssignedToUser(requestForUser);
		Set<Long> assignedRoleGroupIds = assignedRoleGroups.stream().map(RoleGroupAssignedToUser::getRoleId).collect(Collectors.toSet());

		List<RoleGroupDTO> recommendedRoleGroupDTOs = new ArrayList<>();
		for (OrgUnitRoleGroupCache recommendedRoleGroup : recommendedRoleGroups) {
			RoleGroup currentRoleGroup = recommendedRoleGroup.getRoleGroup();
			if (!(hideAlreadyAssigned && assignedRoleGroupIds.contains(currentRoleGroup.getId()))
				&& rolerequestService.canRequest(requestForUser, currentRoleGroup, requestForUser, orgUnit)) {
				recommendedRoleGroupDTOs.add(new RoleGroupDTO(currentRoleGroup.getId(), currentRoleGroup.getName(), currentRoleGroup.getDescription(),
					approverOptionService.getApproverOptionsAsString(currentRoleGroup.getApproverPermission()), assignedRoleGroupIds.contains(currentRoleGroup.getId())));
			}
		}

		return recommendedRoleGroupDTOs;
	}

	@Transactional(readOnly = true)
	@GetMapping("recommendeduserroles/{receiverId}")
	public List<UserRoleDTO> recommendedUserRoles(@PathVariable final String receiverId,
														   @RequestParam long position,
														   @RequestParam boolean hideAlreadyAssigned) {
		final User requestForUser = userService.getOptionalByUuid(receiverId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + receiverId + " not found"));
		final Position matchPostion = requestForUser.getPositions().stream().filter(p -> p.getId() == position).findAny()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Position with id " + position + " not assigned to user"));
		final OrgUnit orgUnit = matchPostion.getOrgUnit();
		final List<OrgUnitUserRoleCache> recommendedUserRoles = orgUnitRoleCacheService.getUserRoles(orgUnit);
		final List<UserRoleAssignedToUser> assignedUserRoles = userService.getAllUserRolesAssignedToUser(requestForUser, null);
		final Set<Long> assignedUserRoleIds = assignedUserRoles.stream().map(UserRoleAssignedToUser::getRoleId).collect(Collectors.toSet());
		final List<UserRoleDTO> recommendedUserRolesDTOs = new ArrayList<>();
		for (OrgUnitUserRoleCache recommendedUserRole : recommendedUserRoles) {
			final UserRole currentUserRole = recommendedUserRole.getUserRole();
			if (!(hideAlreadyAssigned && assignedUserRoleIds.contains(currentUserRole.getId()))
				&& rolerequestService.canRequest(currentUserRole, requestForUser, orgUnit, settingsService.getRolerequestRequester())) {
				recommendedUserRolesDTOs.add(
					new UserRoleDTO(
						currentUserRole.getId(),
						currentUserRole.getItSystem().getName(),
						currentUserRole.getName(),
						currentUserRole.getDescription(),
						approverOptionService.getApproverOptionsAsString(currentUserRole.getApproverPermission()),
						assignedUserRoleIds.contains(currentUserRole.getId()),
						hasPostponedConstraints(currentUserRole.getId())
					)
				);
			}
		}
		return recommendedUserRolesDTOs;
	}

	@Transactional(readOnly = true)
	@PostMapping("alluserroles/{receiverId}")
	public DataTablesOutput<UserRoleDTO> allUserRolesFragment(@RequestBody DataTablesInput input, @PathVariable String receiverId, @RequestParam long position, @RequestParam boolean hideAlreadyAssigned)  {
		final User requestingUser = userService.getOptionalByUserId(SecurityUtil.getUserId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with id " + SecurityUtil.getUserId() + " not found"));

		final User receiver = userService.getOptionalByUuid(receiverId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + receiverId + " not found"));
		final Position matchPostion = receiver.getPositions().stream().filter(p -> p.getId() == position).findAny()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Position with id " + position + " not assigned to user"));

		List<Long> assignedUserRoleIds = userService.getAllUserRolesAssignedToUser(receiver, null).stream()
			.map(UserRoleAssignedToUser::getRoleId).toList();

		DataTablesOutput<UserRoleView> userroleOutput = rolerequestService.getRequestableUserRolesAsDatatable(input, requestingUser, receiver, matchPostion.getOrgUnit());
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
			allUserRolesDTOs = allUserRolesDTOs.stream().filter( ur -> !ur.alreadyAssigned).toList();
		}

		return RequestService.toDatatablesOutput(userroleOutput, allUserRolesDTOs);
	}

	@Transactional(readOnly = true)
	@PostMapping("allcombined/{receiverId}")
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
			.filter(p -> p.getId() == position)
			.findAny()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Position with id " + position + " not assigned to user"));

		DataTablesOutput<CombinedRoleView> output = rolerequestService.getRequestableCombinedRolesAsDatatable(
			input, requestingUser, receiver, matchPosition.getOrgUnit(), hideAlreadyAssigned
		);

		// Get assigned roles (for DTO mapping only, not filtering)
		List<Long> assignedUserRoleIds = userService.getAllUserRolesAssignedToUser(receiver, null).stream()
			.map(UserRoleAssignedToUser::getRoleId)
			.toList();

		List<Long> assignedRoleGroupIds = userService.getAllRoleGroupsAssignedToUser(receiver).stream()
			.map(roleGroupAssignedToUser -> roleGroupAssignedToUser.getRoleGroup().getId())
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
					role.getApproverPermission(),
					alreadyAssigned,
					hasConstraints,
					""
				);
			})
			.toList();

		return RequestService.toDatatablesOutput(output, dtos);
	}

	@Transactional(readOnly = true)
	@GetMapping("recommendedcombined/{receiverId}")
	public List<CombinedRoleDTO> recommendedCombined(
		@PathVariable final String receiverId,
		@RequestParam long position,
		@RequestParam boolean hideAlreadyAssigned) {

		final User requestingUser = userService.getOptionalByUserId(SecurityUtil.getUserId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with id " + SecurityUtil.getUserId() + " not found"));

		final User requestForUser = userService.getOptionalByUuid(receiverId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + receiverId + " not found"));

		final Position matchPosition = requestForUser.getPositions().stream()
			.filter(p -> p.getId() == position)
			.findAny()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Position with id " + position + " not assigned to user"));

		final OrgUnit orgUnit = matchPosition.getOrgUnit();

		final Set<Long> assignedUserRoleIds = userService.getAllUserRolesAssignedToUser(requestForUser, null).stream()
			.map(UserRoleAssignedToUser::getRoleId)
			.collect(Collectors.toSet());

		final Set<Long> assignedRoleGroupIds = userService.getAllRoleGroupsAssignedToUser(requestForUser).stream()
			.map(roleGroupAssignedToUser -> roleGroupAssignedToUser.getRoleGroup().getId())
			.collect(Collectors.toSet());

		final List<CombinedRoleDTO> combinedRoles = new ArrayList<>();

		final List<OrgUnitRoleGroupCache> recommendedRoleGroups = orgUnitRoleCacheService.getRoleGroups(orgUnit);
		final Set<Long> userRoleIdsInRoleGroups = new HashSet<>();

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
				&& rolerequestService.canRequest(requestingUser, currentRoleGroup, requestForUser, orgUnit)) {
				combinedRoles.add(
					new CombinedRoleDTO(
						currentRoleGroup.getId(),
						"roleGroup",
						null,
						currentRoleGroup.getName(),
						currentRoleGroup.getDescription(),
						approverOptionService.getApproverOptionsAsString(currentRoleGroup.getApproverPermission()),
						isAlreadyAssigned,
						false,
						roleGroupUserRoles
					)
				);
			}
		}

		final List<OrgUnitUserRoleCache> recommendedUserRoles = orgUnitRoleCacheService.getUserRoles(orgUnit);
		for (OrgUnitUserRoleCache recommendedUserRole : recommendedUserRoles) {
			final UserRole currentUserRole = recommendedUserRole.getUserRole();

			if (userRoleIdsInRoleGroups.contains(currentUserRole.getId())) {
				log.debug("Excluding user role '{}' (id: {}) as it is already part of a recommended role group", currentUserRole.getName(), currentUserRole.getId());
				continue;
			}

			boolean isAlreadyAssigned = assignedUserRoleIds.contains(currentUserRole.getId());

			if (!(hideAlreadyAssigned && isAlreadyAssigned)
				&& rolerequestService.canRequest(currentUserRole, requestForUser, orgUnit, settingsService.getRolerequestRequester())) {
				combinedRoles.add(
					new CombinedRoleDTO(
						currentUserRole.getId(),
						"userRole",
						currentUserRole.getItSystem().getName(),
						currentUserRole.getName(),
						currentUserRole.getDescription(),
						approverOptionService.getApproverOptionsAsString(currentUserRole.getApproverPermission()),
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
	@GetMapping("allrolegroups/{receiverId}")
	public List<RoleGroupDTO> allRoleGroupsFragment(@PathVariable String receiverId,
													@RequestParam long position,
													@RequestParam boolean hideAlreadyAssigned) {
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (loggedInUser == null) {
			throw new UsernameNotFoundException("Could not find logged in user");
		}
		User receiver = userService.getOptionalByUuid(receiverId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + receiverId + " not found"));
		final Position matchPostion = receiver.getPositions().stream().filter(p -> p.getId() == position).findAny()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Position with id " + position + " not assigned to user"));


		List<Long> assignedRoleGroupIds = userService.getAllRoleGroupsAssignedToUser(receiver).stream()
			.map(RoleGroupAssignedToUser::getRoleId).toList();
		List<RoleGroup> rolegroupOutput = rolerequestService.getRequestableRoleGroupsAsDatatable(loggedInUser, receiver, matchPostion.getOrgUnit());
		List<RoleGroupDTO> allRoleGroupDTOs = rolegroupOutput.stream()
			.filter(role -> rolerequestService.canRequest(loggedInUser, role, receiver, matchPostion.getOrgUnit()))
			.map(roleGroup -> new RoleGroupDTO(
				roleGroup.getId(),
				roleGroup.getName(),
				roleGroup.getDescription(),
				approverOptionService.getApproverOptionsAsString(roleGroup.getApproverPermission()),
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

}
