package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AvailableRoleGroupDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SelectOUDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleAssignmentConstraintValueDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SystemRoleAssignmentDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleNotAssignedDTO;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentPostponedConstraint;
import dk.digitalidentity.rc.dao.model.assignment.CurrentExceptedAssignment;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.RequireRequesterOrAssignerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PNumberService;
import dk.digitalidentity.rc.service.SENumberService;
import dk.digitalidentity.rc.service.Select2Service;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.model.RoleAssignedToUserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@RequireControllerPermission(section = Section.USER, permission = Permission.READ)
@Controller
public class UserController {
	private final UserService userService;
	private final UserControllerHelper helper;
	private final KleService kleService;
	private final AccessConstraintService assignerRoleConstraint;
	private final RoleCatalogueConfiguration configuration;
	private final UserRoleService userRoleService;
	private final Select2Service select2Service;
	private final OrgUnitService orgUnitService;
	private final DomainService domainService;
	private final SENumberService seNumberService;
	private final PNumberService pNumberService;
	private final SettingsService settingsService;
	private final AssignmentService assignmentService;

	private static final Section permissionEntity = Section.USER;
	private final UserPermissionContext userPermissionContext;

	@Value("#{servletContext.contextPath}")
    private String servletContextPath;

	@GetMapping(value = "/ui/users/list")
	public String view(Model model) {
		model.addAttribute("multipleDomains", domainService.getAll().size() > 1);

		return "users/list";
	}

	@RequirePermission(section = Section.USER, permission = Permission.UPDATE)
	@GetMapping(value = "/ui/users/manage/{uuid}")
	public String manage(Model model, @PathVariable("uuid") String uuid) {
		Optional<User> optionalUser = userService.getOptionalByUuid(uuid);
		if (optionalUser.isEmpty()) {
			return "redirect:../list";
		}
		User user = optionalUser.get();

		List<OrgUnit> userOrgUnits = orgUnitService.getOrgUnitsForUser(user);

		boolean isAssigner = userPermissionContext.hasPermission(Section.USER, Permission.ASSIGN);

		boolean editable = userPermissionContext.getConstraint(Section.USER, Permission.UPDATE)
			.allowsAnyOrgunit(userOrgUnits.stream().map(OrgUnit::getUuid).toList());

		// Get excepted assignments from CurrentExceptedAssignment
		Set<CurrentExceptedAssignment> exceptedAssignments = assignmentService.getExceptedByUser(user);
		List<UserRoleNotAssignedDTO> exceptedAssignmentDTOs = convertExceptedAssignmentsToDTO(exceptedAssignments);

		model.addAttribute("user", user);
		model.addAttribute("exceptedAssignments", exceptedAssignmentDTOs);
		model.addAttribute("editable", editable && assignerRoleConstraint.isUserAccessable(user, true));
		model.addAttribute("canAssign", isAssigner);

		//kle
		List<Kle> kles = kleService.findAll();

		List<KleDTO> kleDTOS = new ArrayList<>();
		for (Kle kle : kles) {
			KleDTO kleDTO = new KleDTO();
			kleDTO.setId(kle.getCode());
			kleDTO.setParent(kle.getParent().equals("0") ? "#" : kle.getParent());
			kleDTO.setText(kle.isActive() ? kle.getCode() + " " + kle.getName() : kle.getCode() + " " + kle.getName() + " [UDGÅET]");
			kleDTOS.add(kleDTO);
		}

		kleDTOS.sort(Comparator.comparing(KleDTO::getText));
		model.addAttribute("allKles", kleDTOS);

		model.addAttribute("kleUiEnabled", configuration.getIntegrations().getKle().isUiEnabled());
		model.addAttribute("canEditKle", isAssigner || SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR));
		model.addAttribute("roleAssignmentOrgUnits", orgUnitService.getOrgUnitsForUser(user).stream().map(o -> new SelectOUDTO(o)).collect(Collectors.toList()));
		model.addAttribute("orgUnitList", select2Service.getOrgUnitList());
		model.addAttribute("itSystemList", select2Service.getItSystemList());
		model.addAttribute("caseNumberEnabled", settingsService.isCaseNumberEnabled());
		model.addAttribute("stale", userService.isUserStale(user.getUuid()));

		// TODO: refactor at some point, so we can use the above KLE list instead of this one...
		userService.addPostponedListsToModel(model);

		return "users/manage";
	}

	@RequirePermission(section = Section.USER, permission = Permission.READ)
	@GetMapping(value = "/ui/users/view/{uuid}")
	public String view(Model model, @PathVariable("uuid") String uuid) {
		Optional<User> optionalUser = userService.getOptionalByUuid(uuid);
		if (optionalUser.isEmpty()) {
			return "redirect:../list";
		}
		User user = optionalUser.get();

		// Get excepted assignments from CurrentExceptedAssignment
		Set<CurrentExceptedAssignment> exceptedAssignments = assignmentService.getExceptedByUser(user);
		List<UserRoleNotAssignedDTO> exceptedAssignmentDTOs = convertExceptedAssignmentsToDTO(exceptedAssignments);

		Collection<OrgUnit> userOrgUnits = orgUnitService.getOrgUnitsForUser(user);

		boolean isAssigner = userPermissionContext.hasPermission(Section.USER, Permission.ASSIGN);

		model.addAttribute("user", user);
		model.addAttribute("exceptedAssignments", exceptedAssignmentDTOs);
		model.addAttribute("editable", false);
		model.addAttribute("canAssign", isAssigner);

		model.addAttribute("allKles", List.of());

		model.addAttribute("kleUiEnabled", false);
		model.addAttribute("canEditKle", false);
		model.addAttribute("roleAssignmentOrgUnits", userOrgUnits.stream().map(o -> new SelectOUDTO(o)).collect(Collectors.toList()));
		model.addAttribute("orgUnitList", select2Service.getOrgUnitList());
		model.addAttribute("itSystemList", select2Service.getItSystemList());
		model.addAttribute("caseNumberEnabled", settingsService.isCaseNumberEnabled());
		model.addAttribute("stale", userService.isUserStale(user.getUuid()));

		// TODO: refactor at some point, so we can use the above KLE list instead of this one...
		userService.addPostponedListsToModel(model);

		return "users/manage";
	}

	private List<UserRoleNotAssignedDTO> convertExceptedAssignmentsToDTO(Set<CurrentExceptedAssignment> exceptedAssignments) {
		List<UserRoleNotAssignedDTO> result = new ArrayList<>();

		// Create maps for efficient lookup
		Map<String, OrgUnit> orgUnitMap = new HashMap<>();
		Map<Long, UserRole> userRoleMap = new HashMap<>();

		for (CurrentExceptedAssignment exception : exceptedAssignments) {
			// Get OrgUnit (cache lookup)
			OrgUnit orgUnit = orgUnitMap.computeIfAbsent(exception.getExceptionOuUuid(),
				orgUnitService::getByUuid);

			if (orgUnit == null) {
				continue;
			}

			// Get UserRole (cache lookup)
			UserRole userRole = userRoleMap.computeIfAbsent(exception.getExceptionUserRoleId(),
				userRoleService::getById);

			if (userRole != null) {
				result.add(new UserRoleNotAssignedDTO(userRole, orgUnit));
			}
		}

		return result;
	}

	@Transactional(readOnly = true)
	@RequirePermission(section = Section.USER, permission = Permission.READ)
	@RequestMapping(value = "/ui/users/manage/{uuid}/roles")
	public String getAssignedRolesFragment(Model model, @PathVariable("uuid") String uuid) {
		Optional<User> optionalUser = userService.getOptionalByUuid(uuid);
		if (optionalUser.isEmpty()) {
			return "redirect:../list";
		}
		User user = optionalUser.get();

		boolean isAssigner = userPermissionContext.hasPermission(Section.USER, Permission.ASSIGN);
		PermissionConstraint assignConstraint = userPermissionContext.getConstraint(Section.USER, Permission.ASSIGN);

		// loads current assignments with userroles and their it systems eagerly fetched
		Set<CurrentAssignment> currentAssignments = assignmentService.getByUserWithEagerRolesAndSystemsIncludingInactive(user);
		List<RoleAssignedToUserDTO> assignmentDTOs = assignmentService.getAssignmentsForUser(user, currentAssignments);
		userRoleService.enrichAssignments(assignmentDTOs, user, assignConstraint, currentAssignments, isAssigner);

		model.addAttribute("assignments", assignmentDTOs);
		model.addAttribute("editable", isAssigner);
		model.addAttribute("canAssign", isAssigner);
		model.addAttribute("caseNumberEnabled", settingsService.isCaseNumberEnabled());

		return "users/fragments/manage_roles :: userAssignedRoles";
	}
	@RequirePermission(section = Section.USER, permission = Permission.ASSIGN)
	@RequestMapping(value = "/ui/users/manage/{uuid}/addUserRole")
	public String getAddUserRoleFragment(Model model, @PathVariable("uuid") String uuid) {
		User user = userService.getByUuid(uuid);
		if (user == null) {
			return "redirect:../list";
		}

		return "users/fragments/manage_add_userrole :: addUserRole";
	}

	@RequirePermission(section = Section.USER, permission = Permission.ASSIGN)
	@RequestMapping(value = "/ui/users/manage/{uuid}/addRoleGroup")
	public String getAddRoleGroupFragment(Model model, @PathVariable("uuid") String uuid) {
		User user = userService.getByUuid(uuid);
		if (user == null) {
			return "redirect:../list";
		}

		List<AvailableRoleGroupDTO> roleGroups = helper.getAvailableRoleGroups(user);

		model.addAttribute("roleGroups", roleGroups);
		return "users/fragments/manage_add_rolegroup :: addRoleGroup";
	}

	@RequireRequesterOrAssignerRole
	@RequestMapping(value = "/ui/users/manage/postponedconstraints/{roleId}")
	public String getPostponedConstraintsFragment(Model model, @PathVariable("roleId") long roleId) {
		List<SystemRoleAssignmentDTO> systemRoleAssignmentsDTOs = new ArrayList<>();

		UserRole role = userRoleService.getById(roleId);
		if (role == null) {
			log.warn("Attempting to get a fragment for a role that does not exist: " + roleId);

			model.addAttribute("systemRoleAssignments", systemRoleAssignmentsDTOs);
			model.addAttribute("postponingAllowed", false);
			model.addAttribute("itSystemList", select2Service.getItSystemList());

			return "users/fragments/assign_user_role_postponed_data_constraints :: postponedConstraints";
		}

		if (role.isAllowPostponing()) {
			for (SystemRoleAssignment systemRoleAssignment : role.getSystemRoleAssignments()) {
				List<SystemRoleAssignmentConstraintValueDTO> postponedConstraintValues = new ArrayList<>();
				for (SystemRoleAssignmentConstraintValue constraintValue : systemRoleAssignment.getConstraintValues()) {
					if (constraintValue.isPostponed()) {
						postponedConstraintValues.add(new SystemRoleAssignmentConstraintValueDTO(constraintValue));
					}
				}

				if (!postponedConstraintValues.isEmpty()) {
					SystemRoleAssignmentDTO systemRoleAssignmentDTO = new SystemRoleAssignmentDTO();
					systemRoleAssignmentDTO.setSystemRole(systemRoleAssignment.getSystemRole());
					systemRoleAssignmentDTO.setPostponedConstraints(postponedConstraintValues);

					systemRoleAssignmentsDTOs.add(systemRoleAssignmentDTO);
				}
			}
		}

		model.addAttribute("systemRoleAssignments", systemRoleAssignmentsDTOs);
		model.addAttribute("postponingAllowed", role.isAllowPostponing());
		model.addAttribute("itSystemList", select2Service.getItSystemList());

		if (role.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN)) {
			model.addAttribute("pNumberList", pNumberService.getAll());
			model.addAttribute("sENumberList", seNumberService.getAll());
		}

		return "users/fragments/assign_user_role_postponed_data_constraints :: postponedConstraints";
	}

	@RequirePermission(section = Section.USER, permission = Permission.ASSIGN)
	@RequestMapping(value = "/ui/users/manage/{userUuid}/postponedconstraints/edit/{assignmentId}")
	public String getPostponedConstraintsEditFragment(Model model, @PathVariable("userUuid") String userUuid, @PathVariable("assignmentId") long assignmentId) {
		List<SystemRoleAssignmentDTO> systemRoleAssignmentsDTOs = new ArrayList<>();

		User user = userService.getByUuid(userUuid);
		if (user == null) {
			log.warn("Attempting to get fragment for user that does not exist: " + userUuid);

			model.addAttribute("systemRoleAssignments", systemRoleAssignmentsDTOs);
			model.addAttribute("postponingAllowed", false);
			model.addAttribute("itSystemList", select2Service.getItSystemList());

			return "users/fragments/assign_user_role_postponed_data_constraints :: postponedConstraints";
		}

		Set<CurrentAssignment> assignments = assignmentService.getDirectlyAssignedUserRolesForUserIncludingInactive(user);
		CurrentAssignment currentAssignment = assignments.stream()
			.filter(ca -> ca.getId() == assignmentId)
			.findFirst()
			.orElse(null);

		if (currentAssignment == null) {
			log.warn("Attempting to get fragment for assignment that does not exist: " + assignmentId);

			model.addAttribute("systemRoleAssignments", systemRoleAssignmentsDTOs);
			model.addAttribute("postponingAllowed", false);
			model.addAttribute("itSystemList", select2Service.getItSystemList());

			return "users/fragments/assign_user_role_postponed_data_constraints :: postponedConstraints";
		}

		UserRole role = currentAssignment.getUserRole();
		if (role.isAllowPostponing()) {
			Set<CurrentAssignmentPostponedConstraint> postponedConstraints = currentAssignment.getPostponedConstraints();

			for (SystemRoleAssignment systemRoleAssignment : role.getSystemRoleAssignments()) {
				List<SystemRoleAssignmentConstraintValueDTO> postponedConstraintValues = new ArrayList<>();

				for (SystemRoleAssignmentConstraintValue constraintValue : systemRoleAssignment.getConstraintValues()) {
					if (constraintValue.isPostponed()) {
						SystemRoleAssignmentConstraintValueDTO dto = new SystemRoleAssignmentConstraintValueDTO(constraintValue);

						CurrentAssignmentPostponedConstraint postponedConstraint = postponedConstraints.stream()
							.filter(p -> p.getSystemRoleId() == systemRoleAssignment.getSystemRole().getId()
								&& p.getConstraintTypeUuid().equals(constraintValue.getConstraintType().getUuid()))
							.findAny()
							.orElse(null);

						if (postponedConstraint != null) {
							// Join the list values with comma - this is the raw format expected by the form
							dto.setConstraintValue(String.join(",", postponedConstraint.getValue()));
						}

						postponedConstraintValues.add(dto);
					}
				}

				if (!postponedConstraintValues.isEmpty()) {
					SystemRoleAssignmentDTO systemRoleAssignmentDTO = new SystemRoleAssignmentDTO();
					systemRoleAssignmentDTO.setSystemRole(systemRoleAssignment.getSystemRole());
					systemRoleAssignmentDTO.setPostponedConstraints(postponedConstraintValues);

					systemRoleAssignmentsDTOs.add(systemRoleAssignmentDTO);
				}
			}
		}

		model.addAttribute("systemRoleAssignments", systemRoleAssignmentsDTOs);
		model.addAttribute("postponingAllowed", role.isAllowPostponing());
		model.addAttribute("itSystemList", select2Service.getItSystemList());

		if (role.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN)) {
			model.addAttribute("pNumberList", pNumberService.getAll());
			model.addAttribute("sENumberList", seNumberService.getAll());
		}

		return "users/fragments/assign_user_role_postponed_data_constraints :: postponedConstraints";
	}

	@RequestMapping(value = "/ui/users/manage/{uuid}/kle/{type}")
	public String getKleFragment(Model model, @PathVariable("uuid") String uuid, @PathVariable("type") String type) {
		User user = userService.getByUuid(uuid);
		if (user == null) {
			return "redirect:../list";
		}

		switch (type) {
		case "PERFORMING":
			model.addAttribute("kles", kleService.getKleAssignments(user, KleType.PERFORMING, true));
			break;
		case "INTEREST":
			model.addAttribute("kles", kleService.getKleAssignments(user, KleType.INTEREST, true));
			break;

		default:
			return "redirect:../list";
		}

		return "fragments/manage_kle :: kle";
	}

	@RequestMapping(value = "/ui/users/manage/{uuid}/kleEdit/{type}")
	public String getKleEditFragment(Model model, @PathVariable("uuid") String uuid, @PathVariable("type") String type) {
		User user = userService.getByUuid(uuid);
		if (user == null) {
			return "redirect:../list";
		}

		switch (type) {
		case "PERFORMING":
			model.addAttribute("selectedKles", user.getKles().stream().filter(userKLEMapping -> userKLEMapping.getAssignmentType().equals(KleType.PERFORMING)).map(UserKLEMapping::getCode).collect(Collectors.toList()));
			break;
		case "INTEREST":
			model.addAttribute("selectedKles", user.getKles().stream().filter(userKLEMapping -> userKLEMapping.getAssignmentType().equals(KleType.INTEREST)).map(UserKLEMapping::getCode).collect(Collectors.toList()));
			break;

		default:
			return "redirect:../list";
		}

		return "fragments/manage_kle_edit :: kleEdit";
	}
}
