package dk.digitalidentity.rc.rolerequest.controller.rest;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.controller.mvc.RequestController;
import dk.digitalidentity.rc.rolerequest.model.entity.OrgUnitRoleGroupCache;
import dk.digitalidentity.rc.rolerequest.model.entity.OrgUnitUserRoleCache;
import dk.digitalidentity.rc.rolerequest.service.ApproverOptionService;
import dk.digitalidentity.rc.rolerequest.service.OrgUnitRoleCacheService;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
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
	private final UserRoleService userRoleService;
	private final ItSystemService itSystemService;

	public record RoleGroupDTO(long id, String name, String description, String approver, boolean alreadyAssigned) {
	}

	public record UserRoleDTO(long id, UserRoleITSystemDTO itSystem, String name, String description, String approver,
							  boolean alreadyAssigned, boolean hasConstraints) {
	}
	public record UserRoleITSystemDTO(String name) {
	}

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
			if (checkIfRoleGroupAllowed(currentRoleGroup, orgUnit)) {
				recommendedRoleGroupDTOs.add(new RoleGroupDTO(currentRoleGroup.getId(), currentRoleGroup.getName(), currentRoleGroup.getDescription(), approverOptionService.getApproverOptionsAsString(currentRoleGroup.getApproverPermission(), null), assignedRoleGroupIds.contains(currentRoleGroup.getId())));
			}
		}

		if (hideAlreadyAssigned) {
			recommendedRoleGroupDTOs = recommendedRoleGroupDTOs.stream().filter( rg -> !rg.alreadyAssigned).toList();
		}

		return recommendedRoleGroupDTOs;
	}

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
			if (checkIfAllowed(currentUserRole, orgUnit) && !(hideAlreadyAssigned && assignedUserRoleIds.contains(currentUserRole.getId()))) {
				recommendedUserRolesDTOs.add(
					new UserRoleDTO(
						currentUserRole.getId(),
						new UserRoleITSystemDTO(currentUserRole.getItSystem().getName()),
						currentUserRole.getName(),
						currentUserRole.getDescription(),
						approverOptionService.getApproverOptionsAsString(currentUserRole.getApproverPermission(), currentUserRole.getItSystem()),
						assignedUserRoleIds.contains(currentUserRole.getId()),
						hasPostponedConstraints(currentUserRole)
					)
				);
			}
		}
		return recommendedUserRolesDTOs;
	}

	@PostMapping("alluserroles/{recieverId}")
	public DataTablesOutput<UserRoleDTO> allUserRolesFragment(@RequestBody DataTablesInput input, @PathVariable String recieverId, @RequestParam boolean hideAlreadyAssigned)  {
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (loggedInUser == null) {
			throw new UsernameNotFoundException("Could not find logged in user");
		}

		User reciever = userService.getOptionalByUuid(recieverId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + recieverId + " not found"));

		List<Long> assignedUserRoleIds = userService.getAllUserRolesAssignedToUser(reciever, null).stream()
			.map(UserRoleAssignedToUser::getRoleId).toList();

		DataTablesOutput<UserRole> userroleOutput = rolerequestService.getRequestableUserRolesAsDatatable(input, reciever);
		List<UserRoleDTO> allUserRolesDTOs = userroleOutput.getData().stream()
			.map(userRole -> new UserRoleDTO(
				userRole.getId(),
				new UserRoleITSystemDTO(userRole.getItSystem().getName()),
				userRole.getName(),
				userRole.getDescription(),
				approverOptionService.getApproverOptionsAsString(userRole.getApproverPermission(), userRole.getItSystem()),
				assignedUserRoleIds.contains(userRole.getId()),
				hasPostponedConstraints(userRole))
			)
			.toList();

		if (hideAlreadyAssigned) {
			allUserRolesDTOs = allUserRolesDTOs.stream().filter( ur -> !ur.alreadyAssigned).toList();
		}

		return RequestService.toDatatablesOutput(userroleOutput, allUserRolesDTOs);
	}


	@PostMapping("allrolegroups/{recieverId}")
	public DataTablesOutput<RoleGroupDTO> allRoleGroupsFragment(@RequestBody DataTablesInput input, @PathVariable String recieverId, @RequestParam boolean hideAlreadyAssigned) {
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (loggedInUser == null) {
			throw new UsernameNotFoundException("Could not find logged in user");
		}

		User reciever = userService.getOptionalByUuid(recieverId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + recieverId + " not found"));

		List<Long> assignedRoleGroupIds = userService.getAllRoleGroupsAssignedToUser(reciever).stream()
			.map(RoleGroupAssignedToUser::getRoleId).toList();
		DataTablesOutput<RoleGroup> rolegroupOutput = rolerequestService.getRequestableRoleGroupsAsDatatable(input, reciever);
		List<RoleGroupDTO> allRoleGroupDTOs = rolegroupOutput.getData().stream()
			.filter(role -> rolerequestService.canRequest(role, loggedInUser))
			.map(roleGroup -> new RoleGroupDTO(
				roleGroup.getId(),
				roleGroup.getName(),
				roleGroup.getDescription(),
				approverOptionService.getApproverOptionsAsString(roleGroup.getApproverPermission(), null),
				assignedRoleGroupIds.contains(roleGroup.getId())
			))
			.toList();

		if (hideAlreadyAssigned) {
			allRoleGroupDTOs = allRoleGroupDTOs.stream().filter( r -> !r.alreadyAssigned).toList();
		}

		return RequestService.toDatatablesOutput(rolegroupOutput, allRoleGroupDTOs);
	}

	private boolean hasPostponedConstraints(UserRole currentUserRole) {
		boolean hasPostponedConstraints = false;
		for (SystemRoleAssignment systemRoleAssignment : currentUserRole.getSystemRoleAssignments()) {
			boolean anyMatch = systemRoleAssignment.getConstraintValues().stream().anyMatch(SystemRoleAssignmentConstraintValue::isPostponed);
			if (anyMatch) {
				hasPostponedConstraints = true;
				break;
			}
		}
		return hasPostponedConstraints;
	}


	private boolean checkIfAllowed(UserRole userRole, OrgUnit orgUnit) {
		if (userRole.isOuFilterEnabled()) {
			List<String> uuids = userRoleService.getOUFilterUuidsWithChildren(userRole);
			return uuids.contains(orgUnit.getUuid());
		} else if (userRole.getItSystem().isOuFilterEnabled()) {
			List<String> uuids = itSystemService.getOUFilterUuidsWithChildren(userRole.getItSystem());
			return uuids.contains(orgUnit.getUuid());
		}

		return true;
	}

	private boolean checkIfRoleGroupAllowed(RoleGroup currentRoleGroup, OrgUnit orgUnit) {
		for (RoleGroupUserRoleAssignment assignment : currentRoleGroup.getUserRoleAssignments()) {
			if (!checkIfAllowed(assignment.getUserRole(), orgUnit)) {
				return false;
			}
		}

		return true;
	}
}
