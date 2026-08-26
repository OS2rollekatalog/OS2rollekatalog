package dk.digitalidentity.rc.rolerequest.controller.mvc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

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
import dk.digitalidentity.rc.security.RequireNoRole;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PNumberService;
import dk.digitalidentity.rc.service.PostponedConstraintService;
import dk.digitalidentity.rc.service.SENumberService;
import dk.digitalidentity.rc.service.Select2Service;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@RequireNoRole
@Controller
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
	private final PostponedConstraintService postponedConstraintService;

	record RoleGroupListEntry(long id, long assignmentId, String name, String description, String status,
							  String assignedThroughName, boolean removable, boolean pendingRemoval) {
	}

	record UserRoleListEntry(long id, long assignmentId, String itSystemName, String name, String description,
							 String status, String assignedThroughName, boolean removable, boolean pendingRemoval) {
	}

	record PendingRequest(long roleId, long requestId, String itSystemName, String name, String description,
						  String status, boolean cancelable) {
	}

	@GetMapping("/ui/request")
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
				&& rolerequestService.canRequestRemoval(
					user, currentAssignment.getRoleGroup(), user, settingsService.getRolerequestRequester());

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
			// already processed earlier
			if (currentAssignment.getRoleGroup() != null) {
				continue;
			}

			// empty assignments (no roles) CAN happen, so check for these
			if (currentAssignment.getUserRole() == null) {
				continue;
			}

			final AssignedThrough assignedThrough = assignmentService.getAssignedThrough(currentAssignment);
			final UserRole userRole = currentAssignment.getUserRole();
			boolean removalPending = rolerequestService.hasPendingRemovalRequestForUserrole(userRole.getId(), user.getUuid());
			List<RequestableBy> globalRequesterSetting = settingsService.getRolerequestRequester();
			boolean requestRemovalPossible = assignedThrough.equals(AssignedThrough.DIRECT)
				&& !removalPending
				&& rolerequestService.canRequestRemoval(user, userRole, user, globalRequesterSetting);

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

	@GetMapping("/ui/request/employees")
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

	@GetMapping("/ui/request/remove/wizard")
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

		Map<Long, RoleForUser> roleGroupsById = new LinkedHashMap<>();
		List<RoleForUser> userRoles = new ArrayList<>();

		final Set<CurrentAssignment> currentAssignments = assignmentService.getByUserIncludingInactive(requestForUser);
		for (var assignment : currentAssignments) {
			if (assignment.getRoleGroup() != null) {
				// Collect into a Map so we won't have duplicate entries
				final RoleGroup roleGroup = assignment.getRoleGroup();

				// role-group rows must use getAssignedThroughForRoleGroup: getAssignedThrough always reports
				// ROLEGROUP for them, so a directly-assigned role group would otherwise never be removable here.
				final AssignedThrough assignedThrough = assignmentService.getAssignedThroughForRoleGroup(assignment);
				boolean requestRemovalPossible = assignedThrough.equals(AssignedThrough.DIRECT)
					&& rolerequestService.canRequestRemoval(
						loggedInUser, roleGroup, requestForUser, settingsService.getRolerequestRequester());

				// if already stored, only overwrite when the new assignment is DIRECT and the stored one
				// wasn't, so a DIRECT (removable) assignment is never shadowed by an indirect one (e.g.
				// ORGUNIT/ITSYSTEM) that happened to be encountered first in the set
				if (roleGroupsById.containsKey(roleGroup.getId()) && !requestRemovalPossible) {
					continue;
				}
				roleGroupsById.put(roleGroup.getId(), new RoleForUser(roleGroup.getId(), assignment.getAssignmentId(), "", roleGroup.getName(), roleGroup.getDescription(), requestRemovalPossible));
			} else {
				final AssignedThrough assignedThrough = assignmentService.getAssignedThrough(assignment);
				final UserRole userRole = assignment.getUserRole();
				boolean requestRemovalPossible = assignedThrough.equals(AssignedThrough.DIRECT)
					&& rolerequestService.canRequestRemoval(loggedInUser, userRole, requestForUser, settingsService.getRolerequestRequester());
				userRoles.add(new RoleForUser(userRole.getId(), assignment.getAssignmentId(), userRole.getItSystem().getName(), userRole.getName(), userRole.getDescription(), requestRemovalPossible));
			}
		}
		model.addAttribute("reasonSetting", settingsService.getRolerequestReason());
		model.addAttribute("roleGroups", new ArrayList<>(roleGroupsById.values()));
		model.addAttribute("userRoles", userRoles);
		model.addAttribute("titleAddition", " fra " + requestForUser.getEntityName());
		model.addAttribute("userUuid", requestForUser.getUuid());
		model.addAttribute("isCombinedEnabled", settingsService.isShowSingleTableInRequestApproveEnabled());


		return "requestmodule/wizard/remove/request";
	}

	record PositionDTO(long id, String position, String orgUnitName) {
	}

	@GetMapping("/ui/request/wizard")
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
						|| userService.isEffectiveManagerOrSubstituteFor(loggedInUser, requestForUser, p.getOrgUnit()) // nearest leader in the hierarchy, e.g. a chief requesting for a team leader
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
		boolean showRecommendedTab = settingsService.isShowRecommendedRolesTab();
		boolean showAllTab = settingsService.isShowAllRolesTab();
		boolean showExistingTab = settingsService.isShowExistingRolesTab();
		model.addAttribute("showRecommendedTab", showRecommendedTab);
		model.addAttribute("showAllTab", showAllTab);
		model.addAttribute("showExistingTab", showExistingTab);
		model.addAttribute("isCombinedEnabled", settingsService.isShowSingleTableInRequestApproveEnabled() && showRecommendedTab && showAllTab);
		model.addAttribute("reasonSetting", settingsService.getRolerequestReason());

		List<OUListForm> allOUs = orgUnitService.getAllCached()
			.stream()
			.map(ou -> new OUListForm(ou, true))
			.sorted(Comparator.comparing(OUListForm::getText))
			.collect(Collectors.toList());
		model.addAttribute("treeOUs", allOUs);

		return "requestmodule/wizard/request";
	}

	@GetMapping("/ui/request/wizard/roles")
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

		boolean showRecommendedTab = settingsService.isShowRecommendedRolesTab();
		boolean showAllTab = settingsService.isShowAllRolesTab();
		boolean showExistingTab = settingsService.isShowExistingRolesTab();
		model.addAttribute("showRecommendedTab", showRecommendedTab);
		model.addAttribute("showAllTab", showAllTab);
		model.addAttribute("showExistingTab", showExistingTab);
		model.addAttribute("showCombinedTable", settingsService.isShowSingleTableInRequestApproveEnabled() && showRecommendedTab && showAllTab);
		return "requestmodule/wizard/fragments/roles :: rolesForWizard";
	}

	@GetMapping("/ui/request/wizard/constraintfragment")
	@Transactional(readOnly = true)
	public String userroleConstraintModal(Model model, @RequestParam long roleId, @RequestParam(required = false) String userUuid) {
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

		// If the receiver already has this exact role assigned (possibly several times, each with its own constraint values),
		// resolve every assignment's postponed constraints separately so no distinct value is dropped, then merge them for display
		List<SystemRoleAssignmentDTO> existingPostponedConstraintDisplayValues = new ArrayList<>();
		if (role.isAllowPostponing() && userUuid != null) {
			User existingAssignmentUser = userService.getOptionalByUuid(userUuid).orElse(null);
			if (existingAssignmentUser != null) {
				Set<CurrentAssignment> currentAssignments = assignmentService.getByUserRoleAndUserIncludingInactive(role, existingAssignmentUser);
				for (CurrentAssignment currentAssignment : currentAssignments) {
					existingPostponedConstraintDisplayValues.addAll(
						postponedConstraintService.resolvePostponedConstraintDisplayValues(role, currentAssignment.getPostponedConstraints()));
				}
			}
		}

		if (role.isAllowPostponing()) {
			systemRoleAssignmentsDTOs = role.getSystemRoleAssignments().stream().map(systemRoleAssignment -> {
					List<SystemRoleAssignmentConstraintValueDTO> postponedConstraintValues = systemRoleAssignment.getConstraintValues().stream()
						.filter(constraintValue ->
							constraintValue.isPostponed() && !globalConstraints.contains(constraintValue.getConstraintValue()) // Filter value if it contains a global constraint
						).map(constraintValue -> {
							SystemRoleAssignmentConstraintValueDTO dto = new SystemRoleAssignmentConstraintValueDTO(constraintValue);

							// Collect the resolved value from every existing assignment (there can be several, each with a different value for this constraint type)
							List<String> existingValues = existingPostponedConstraintDisplayValues.stream()
								.filter(existingSystemRoleAssignment -> existingSystemRoleAssignment.getSystemRole().getId() == systemRoleAssignment.getSystemRole().getId())
								.flatMap(existingSystemRoleAssignment -> existingSystemRoleAssignment.getPostponedConstraints().stream())
								.filter(existingConstraint -> existingConstraint.getConstraintType().getUuid().equals(constraintValue.getConstraintType().getUuid()))
								.map(SystemRoleAssignmentConstraintValueDTO::getConstraintValue)
								.filter(StringUtils::hasLength)
								.distinct()
								.toList();

							if (!existingValues.isEmpty()) {
								dto.setExistingValue(String.join(", ", existingValues));
							}

							return dto;
						})
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
