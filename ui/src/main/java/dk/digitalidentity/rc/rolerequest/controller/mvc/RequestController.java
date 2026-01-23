package dk.digitalidentity.rc.rolerequest.controller.mvc;

import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleAssignmentConstraintValueDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleAssignmentDTO;
import dk.digitalidentity.rc.dao.model.AuthorizationManager;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestConstraint;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.rolerequest.service.RequestAuthorizedRoleService;
import dk.digitalidentity.rc.rolerequest.service.RequestConstraintService;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PNumberService;
import dk.digitalidentity.rc.service.SENumberService;
import dk.digitalidentity.rc.service.Select2Service;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.RoleAssignedToUserDTO;
import dk.digitalidentity.rc.service.model.RoleGroupAssignedToUser;
import dk.digitalidentity.rc.service.model.UserRoleAssignedToUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.service.model.RoleAssignmentType.NEGATIVE_ROLEGROUP;
import static dk.digitalidentity.rc.service.model.RoleAssignmentType.ROLEGROUP;

@RequiredArgsConstructor
@Slf4j
@Controller
@RequestMapping("/ui/request")
public class RequestController {
	private final UserService userService;
	private final SettingsService settingsService;
	private final UserRoleService userRoleService;
	private final ItSystemService itSystemService;
	private final OrgUnitService orgUnitService;
	private final Select2Service select2Service;
	private final SENumberService seNumberService;
	private final PNumberService pNumberService;
	private final RequestConstraintService constraintService;
	private final RequestService rolerequestService;
	private final RequestAuthorizedRoleService requestAuthorizedRoleService;
	private final ManagerSubstituteService managerSubstituteService;
	private final RequestService requestService;

	record RoleGroupListEntry(long id, long assignmentId, String name, String description, String status,
							  String assignedThroughName, boolean removable, boolean pendingRemoval) {
	}

	record UserRoleListEntry(long id, long assignmentId, String itSystemName, String name, String description,
							 String status, String assignedThroughName, boolean removable, boolean pendingRemoval) {
	}

	record PendingRequest(long roleId, long requestId, String itSystemName, String name, String description,
						  String status, boolean cancelable) {
	}

	@GetMapping
	public String index(Model model) {
		if (!settingsService.isRequestApproveEnabled()) {
			return "redirect:/error";
		}

		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return "requestmodule/error";
		}

		List<RoleGroupListEntry> roleGroups = new ArrayList<>();
		for (RoleGroupAssignedToUser roleGroupAssignment : userService.getAllRoleGroupsAssignedToUser(user)) {
			boolean removalPending = rolerequestService.hasPendingRemovalRequestForRolegroup(roleGroupAssignment.getRoleGroup().getId(), user.getUuid());
			boolean requestRemovalPossible = roleGroupAssignment.getAssignedThrough().equals(AssignedThrough.DIRECT) && !removalPending
				&& rolerequestService.canRequest(user, roleGroupAssignment.getRoleGroup(), user, roleGroupAssignment.getOrgUnit());
			roleGroups.add(new RoleGroupListEntry(roleGroupAssignment.getRoleGroup().getId(), roleGroupAssignment.getAssignmentId(), roleGroupAssignment.getRoleGroup().getName(), roleGroupAssignment.getRoleGroup().getDescription(), roleGroupAssignment.getAssignedThrough().getMessage(), roleGroupAssignment.getTitleOrOrgUnitName(), requestRemovalPossible, removalPending));
		}

		List<RoleRequest> pendingrequests = rolerequestService.getPendingForReceiver(user);

		List<PendingRequest> pendingRolegroups = new ArrayList<>();
		for (RoleRequest request : pendingrequests.stream().filter(req -> req.getRoleGroup() != null && req.getRequestAction() == RequestAction.ADD).toList()) {
			RoleGroup rolegroup = request.getRoleGroup();

			pendingRolegroups.add(new PendingRequest(
				rolegroup.getId(),
				request.getId(),
				null,
				rolegroup.getName(),
				rolegroup.getDescription(),
				request.getRequestAction() == RequestAction.ADD ? "requestmodule.html.pending.add" : "requestmodule.html.pending.remove",
				request.getRequester() == user));
		}


		List<UserRoleListEntry> userRoles = new ArrayList<>();
		// getAllUserRoleAndRoleGroupAssignments
		for (RoleAssignedToUserDTO userRoleAssignment : userService.getAllUserRoleAndRoleGroupAssignments(user)) {
			if (userRoleAssignment.getType() == ROLEGROUP || userRoleAssignment.getType() == NEGATIVE_ROLEGROUP) {
				continue;
			}
			final OrgUnit userRoleUnit = orgUnitService.getByUuid(userRoleAssignment.getOrgUnitUuid());
			final UserRole userRole = userRoleService.getById(userRoleAssignment.getRoleId());
			boolean removalPending = rolerequestService.hasPendingRemovalRequestForUserrole(userRoleAssignment.getRoleId(), user.getUuid());
			List<RequestableBy> globalRequesterSetting = settingsService.getRolerequestRequester();
			boolean requestRemovalPossible = userRoleAssignment.getAssignedThrough().equals(AssignedThrough.DIRECT)
				&& !removalPending
				&& rolerequestService.canRequest(userRole, user, userRoleUnit, globalRequesterSetting);

			userRoles.add(new UserRoleListEntry(userRoleAssignment.getRoleId(),
				userRoleAssignment.getAssignmentId(),
				userRole.getItSystem().getName(),
				userRole.getName(),
				userRole.getDescription(),
				userRoleAssignment.getAssignedThrough().getMessage(),
				userRoleAssignment.getAssignedThroughName(),
				requestRemovalPossible,
				removalPending));
		}

		List<PendingRequest> pendingUserRoles = new ArrayList<>();
		for (RoleRequest request : pendingrequests.stream().filter(req -> req.getUserRole() != null && req.getRequestAction() == RequestAction.ADD).toList()) {
			UserRole userRole = request.getUserRole();

			pendingUserRoles.add(new PendingRequest(
				userRole.getId(),
				request.getId(),
				userRole.getItSystem().getName(),
				userRole.getName(),
				userRole.getDescription(),
				request.getRequestAction() == RequestAction.ADD ? "requestmodule.html.pending.add" : "requestmodule.html.pending.remove",
				request.getRequester() == user));
		}

		//get setting for reason requirement
		model.addAttribute("reasonRequirement", settingsService.getRolerequestReason().toString());
		model.addAttribute("pendingRolegroups", pendingRolegroups);
		model.addAttribute("pendingUserRoles", pendingUserRoles);

		model.addAttribute("roleGroups", roleGroups);
		model.addAttribute("userRoles", userRoles);

		model.addAttribute("canRequestRoles", requestService.canRequestAnyRoles(user));
		return "requestmodule/index";
	}

	record RequestEmployee(String uuid, String userId, String name, Set<String> positions, boolean hasRoles) {
	}

	@GetMapping(value = "employees")
	public String requestForEmployee(Model model) {
		if (!settingsService.isRequestApproveEnabled()) {
			return "redirect:/error";
		}

		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (loggedInUser == null) {
			return "requestmodule/error";
		}

		return "requestmodule/wizard/employees";
	}

	record RoleForUser(long id, long assignmentId, String itSystemName, String name, String description, boolean removable) {}
	@GetMapping(value = "remove/wizard")
	public String requestRemoveWizard(Model model, @RequestParam(required = false) String uuid) {
		if (!settingsService.isRequestApproveEnabled()) {
			return "redirect:/error";
		}

		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (loggedInUser == null) {
			return "requestmodule/error";
		}

		User requestForUser = userService.getOptionalByUuid(uuid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + uuid + " not found"));

		if (!rolerequestService.canRequestFor(loggedInUser, requestForUser)) {
			return "redirect:/error";
		}

		List<RoleForUser> roleGroups = new ArrayList<>();
		List<RoleForUser> userRoles = new ArrayList<>();

		for (RoleGroupAssignedToUser roleGroupAssignment : userService.getAllRoleGroupsAssignedToUser(requestForUser)) {
			boolean requestRemovalPossible = roleGroupAssignment.getAssignedThrough().equals(AssignedThrough.DIRECT)
				&& rolerequestService.canRequest(loggedInUser, roleGroupAssignment.getRoleGroup(), requestForUser, roleGroupAssignment.getOrgUnit());
			roleGroups.add(new RoleForUser(roleGroupAssignment.getRoleGroup().getId(), roleGroupAssignment.getAssignmentId(), "", roleGroupAssignment.getRoleGroup().getName(), roleGroupAssignment.getRoleGroup().getDescription(), requestRemovalPossible));
		}

		for (UserRoleAssignedToUser userRoleAssignment : userService.getAllUserRolesAssignedToUserExemptingRoleGroups(requestForUser, null)) {
			boolean requestRemovalPossible = userRoleAssignment.getAssignedThrough().equals(AssignedThrough.DIRECT)
				&& rolerequestService.canRequest(userRoleAssignment.getUserRole(), requestForUser, userRoleAssignment.getOrgUnit(), settingsService.getRolerequestRequester());
			userRoles.add(new RoleForUser(userRoleAssignment.getUserRole().getId(), userRoleAssignment.getAssignmentId(), userRoleAssignment.getUserRole().getItSystem().getName(), userRoleAssignment.getUserRole().getName(), userRoleAssignment.getUserRole().getDescription(), requestRemovalPossible));
		}

		model.addAttribute("reasonSetting", settingsService.getRolerequestReason());
		model.addAttribute("roleGroups", roleGroups);
		model.addAttribute("userRoles", userRoles);
		model.addAttribute("titleAddition", " fra " + requestForUser.getEntityName());
		model.addAttribute("userUuid", requestForUser.getUuid());
		model.addAttribute("isCombinedEnabled", settingsService.isShowSingleTableInRequestApproveEnabled());


		return "requestmodule/wizard/remove/request";
	}

	record PositionDTO(long id, String position, String orgUnitName) {
	}

	@GetMapping(value = "wizard")
	public String requestWizard(Model model, @RequestParam(required = false) String uuid) {
		if (!settingsService.isRequestApproveEnabled()) {
			return "redirect:/error";
		}

		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (loggedInUser == null) {
			return "requestmodule/error";
		}

		if (uuid == null) {
			// request for self
			model.addAttribute("employments", loggedInUser.getPositions().stream().map(p -> new PositionDTO(p.getId(), p.getName(), p.getOrgUnit().getName())).toList());
			model.addAttribute("userUuid", loggedInUser.getUuid());
		} else {
			// request for other user
			User requestForUser = userService.getOptionalByUuid(uuid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + uuid + " not found"));

			if (!rolerequestService.canRequestFor(loggedInUser, requestForUser)) {
				return "redirect:/error";
			}

			RequestAuthorizedRoleService.LimitedToOrgUnits limitedToOrgUnits = requestAuthorizedRoleService.accessibleOrgUnits(loggedInUser);
			List<PositionDTO> positions = requestForUser.getPositions().stream()
				.filter(p ->
						SecurityUtil.isAdmin()
						|| managerSubstituteService.isManagerForOrgUnit(p.getOrgUnit())
						|| managerSubstituteService.isSubstituteforOrgUnit(p.getOrgUnit())
						|| p.getOrgUnit().getAuthorizationManagers().stream().map(AuthorizationManager::getUser).toList().contains(loggedInUser) // User is authorizationmanager
							|| limitedToOrgUnits.type().equals(RequestAuthorizedRoleService.LimitedToType.ALL)
							|| limitedToOrgUnits.orgUnits().contains(p.getOrgUnit().getUuid()) // If current user is requestauthorized only for some units, we should filter by those
				)
				.map(p -> new PositionDTO(p.getId(), p.getName(), p.getOrgUnit().getName()))
				.toList();

			model.addAttribute("employments", positions);
			model.addAttribute("userUuid", requestForUser.getUuid());
			model.addAttribute("titleAddition", " til " + requestForUser.getEntityName());
		}
		model.addAttribute("isCombinedEnabled", settingsService.isShowSingleTableInRequestApproveEnabled());
		model.addAttribute("reasonSetting", settingsService.getRolerequestReason());
		model.addAttribute("onlyRecommendRoles", settingsService.getOnlyRecommendRoles());

		List<OUListForm> allOUs = orgUnitService.getAllCached()
			.stream()
			.map(ou -> new OUListForm(ou, true))
			.sorted(Comparator.comparing(OUListForm::getText))
			.collect(Collectors.toList());
		model.addAttribute("treeOUs", allOUs);

		return "requestmodule/wizard/request";
	}

	@GetMapping(value = "wizard/roles")
	public String requestWizardRoles(Model model, @RequestParam String user, @RequestParam long position) {
		if (!settingsService.isRequestApproveEnabled()) {
			throw new IllegalArgumentException("Request/Approve module is not enabled");
		}

		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (loggedInUser == null) {
			throw new SecurityException("No logged in user");
		}

		User requestForUser = userService.getOptionalByUuid(user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + user + " not found"));


		if (!rolerequestService.canRequestFor(loggedInUser, requestForUser)) {
			throw new IllegalArgumentException("Logged in user cannot request role for target user");
		}

		// check that the user has the given position
		Position matchPostion = requestForUser.getPositions().stream().filter(p -> p.getId() == position).findAny().orElse(null);
		if (matchPostion == null) {
			return "requestmodule/error";
		}

		model.addAttribute("onlyRecommendRoles", settingsService.getOnlyRecommendRoles());
		model.addAttribute("showCombinedTable", settingsService.isShowSingleTableInRequestApproveEnabled());
		return "requestmodule/wizard/fragments/roles :: rolesForWizard";
	}

	@GetMapping(value = "wizard/constraintfragment")
	public String userroleConstraintModal(Model model, @RequestParam long roleId) {
		List<SystemRoleAssignmentDTO> systemRoleAssignmentsDTOs = new ArrayList<>();

		UserRole role = userRoleService.getById(roleId);
		if (role == null) {
			log.warn("Attempting to get a fragment for a role that does not exist: " + roleId);

			model.addAttribute("systemRoleAssignments", systemRoleAssignmentsDTOs);
			model.addAttribute("postponingAllowed", false);
			model.addAttribute("itSystemList", select2Service.getItSystemList());

			return "users/fragments/assign_user_role_postponed_data_constraints :: postponedConstraints";
		}

		//Get restricted values
		List<String> globalConstraints = constraintService.getAllConstraints().stream().map(RequestConstraint::getValue).toList();

		if (role.isAllowPostponing()) {
			systemRoleAssignmentsDTOs = role.getSystemRoleAssignments().stream().map(systemRoleAssignment -> {
					List<SystemRoleAssignmentConstraintValueDTO> postponedConstraintValues = systemRoleAssignment.getConstraintValues().stream()
						.filter(constraintValue ->
							constraintValue.isPostponed() && !globalConstraints.contains(constraintValue.getConstraintValue()) // Filter value if it contains a global constraint
						).map(SystemRoleAssignmentConstraintValueDTO::new)
						.toList();

					SystemRoleAssignmentDTO systemRoleAssignmentDTO = new SystemRoleAssignmentDTO();
					systemRoleAssignmentDTO.setSystemRole(systemRoleAssignment.getSystemRole());
					systemRoleAssignmentDTO.setPostponedConstraints(postponedConstraintValues);
					return systemRoleAssignmentDTO;
				})
				.filter(systemRoleAssignmentDTO -> !systemRoleAssignmentDTO.getPostponedConstraints().isEmpty())
				.toList();
		}

		model.addAttribute("systemRoleAssignments", systemRoleAssignmentsDTOs);
		model.addAttribute("postponingAllowed", role.isAllowPostponing());
		model.addAttribute("itSystemList", select2Service.getItSystemList());

		if (role.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN)) {
			model.addAttribute("pNumberList", pNumberService.getAll());
			model.addAttribute("sENumberList", seNumberService.getAll());
		}

		userService.addPostponedListsToModel(model);

		return "requestmodule/wizard/fragments/userrole_constraint_modal :: UserroleConstraintModal";
	}



	private boolean checkIfRoleGroupAllowed(RoleGroup currentRoleGroup, OrgUnit orgUnit) {
		for (RoleGroupUserRoleAssignment assignment : currentRoleGroup.getUserRoleAssignments()) {
			if (!checkIfAllowed(assignment.getUserRole(), orgUnit)) {
				return false;
			}
		}

		return true;
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

}

