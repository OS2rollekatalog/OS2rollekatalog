package dk.digitalidentity.rc.rolerequest.controller.mvc;

import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleAssignmentConstraintValueDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleAssignmentDTO;
import dk.digitalidentity.rc.dao.model.AuthorizationManager;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestConstraint;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.rolerequest.service.RequestAuthorizedRoleService;
import dk.digitalidentity.rc.rolerequest.service.RequestConstraintService;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PNumberService;
import dk.digitalidentity.rc.service.SENumberService;
import dk.digitalidentity.rc.service.Select2Service;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
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

@RequiredArgsConstructor
@Slf4j
@Controller
@RequestMapping("/ui/request")
public class RequestController {
	private final UserService userService;
	private final SettingsService settingsService;
	private final UserRoleService userRoleService;
	private final OrgUnitService orgUnitService;
	private final Select2Service select2Service;
	private final SENumberService seNumberService;
	private final PNumberService pNumberService;
	private final RequestConstraintService constraintService;
	private final RequestService rolerequestService;
	private final RequestAuthorizedRoleService requestAuthorizedRoleService;
	private final ManagerSubstituteService managerSubstituteService;
	private final RequestService requestService;
	private final AssignmentService assignmentService;

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

		Set<CurrentAssignment> currentAssignments = assignmentService.getByUserIncludingInactive(user);
		Set<CurrentAssignment> uniqueRoleGroupAssignments = assignmentService.getUniqueRoleGroupAssignments(currentAssignments);

		List<RoleGroupListEntry> roleGroups = new ArrayList<>();
		for (CurrentAssignment currentAssignment : uniqueRoleGroupAssignments) {
			boolean removalPending = rolerequestService.hasPendingRemovalRequestForRolegroup(
				currentAssignment.getRoleGroup().getId(),
				user.getUuid()
			);
			final AssignedThrough assignedThrough = assignmentService.getAssignedThroughForRoleGroup(currentAssignment);
			boolean requestRemovalPossible = assignedThrough.equals(AssignedThrough.DIRECT)
				&& !removalPending
				&& rolerequestService.canRequest(user, currentAssignment.getRoleGroup(), user, currentAssignment.getResponsibleOrgUnit());

			roleGroups.add(new RoleGroupListEntry(
				currentAssignment.getRoleGroup().getId(),
				currentAssignment.getAssignmentId(),
				currentAssignment.getRoleGroup().getName(),
				currentAssignment.getRoleGroup().getDescription(),
				assignedThrough.getMessage(),
				assignmentService.getAssignedThroughName(currentAssignment, assignedThrough),
				requestRemovalPossible,
				removalPending
			));
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
		for (CurrentAssignment currentAssignment : currentAssignments) {
			if (currentAssignment.getRoleGroup() != null) {
				continue;
			}
			final AssignedThrough assignedThrough = assignmentService.getAssignedThrough(currentAssignment);
			final UserRole userRole = currentAssignment.getUserRole();
			boolean removalPending = rolerequestService.hasPendingRemovalRequestForUserrole(userRole.getId(), user.getUuid());
			List<RequestableBy> globalRequesterSetting = settingsService.getRolerequestRequester();
			boolean requestRemovalPossible = assignedThrough.equals(AssignedThrough.DIRECT)
				&& !removalPending
				&& rolerequestService.canRequest(userRole, user, currentAssignment.getResponsibleOrgUnit(), globalRequesterSetting);

			userRoles.add(new UserRoleListEntry(userRole.getId(),
				currentAssignment.getAssignmentId(),
				userRole.getItSystem().getName(),
				userRole.getName(),
				userRole.getDescription(),
				assignedThrough.getMessage(),
				assignmentService.getAssignedThroughName(currentAssignment, assignedThrough),
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
	@Transactional(readOnly = true)
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

		final Set<CurrentAssignment> currentAssignments = assignmentService.getByUserIncludingInactive(requestForUser);
		for (var assignment : currentAssignments) {
			final AssignedThrough assignedThrough = assignmentService.getAssignedThrough(assignment);
			if (assignment.getRoleGroup() != null) {
				final RoleGroup roleGroup = assignment.getRoleGroup();
				boolean requestRemovalPossible = assignedThrough.equals(AssignedThrough.DIRECT)
					&& rolerequestService.canRequest(loggedInUser, roleGroup, requestForUser, assignment.getOrgUnit());
				roleGroups.add(new RoleForUser(roleGroup.getId(), assignment.getAssignmentId(), "", roleGroup.getName(), roleGroup.getDescription(), requestRemovalPossible));
			} else {
				final UserRole userRole = assignment.getUserRole();
				boolean requestRemovalPossible = assignedThrough.equals(AssignedThrough.DIRECT)
					&& rolerequestService.canRequest(userRole, requestForUser, assignment.getOrgUnit(), settingsService.getRolerequestRequester());
				userRoles.add(new RoleForUser(userRole.getId(), assignment.getAssignmentId(), userRole.getItSystem().getName(), userRole.getName(), userRole.getDescription(), requestRemovalPossible));
			}
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
						SecurityUtil.hasDirectAdminRole()
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
		Position matchPosition = requestForUser.getPositions().stream().filter(p -> p.getId() == position).findAny().orElse(null);
		if (matchPosition == null) {
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
}
