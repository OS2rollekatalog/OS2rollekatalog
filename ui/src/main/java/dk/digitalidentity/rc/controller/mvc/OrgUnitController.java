package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AvailableRoleGroupDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AvailableUserRoleDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.ExceptedUsersDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.TitleListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserListDTO;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.RequireAssignerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.security.permission.NotPermittedException;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import dk.digitalidentity.rc.service.FunctionService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.TitleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.RoleAssignedToOrgUnitDTO;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("AutoBoxing")
@RequireControllerPermission(section = Section.ORGUNIT, permission = Permission.READ)
@RequiredArgsConstructor
@Controller
public class OrgUnitController {
	private final OrgUnitService orgUnitService;
	private final UserRoleService userRoleService;
	private final RoleGroupService roleGroupService;
	private final KleService kleService;
	private final AccessConstraintService accessConstraintService;
	private final UserService userService;
    private final RoleCatalogueConfiguration configuration;
	private final TitleService titleService;
	private final UserPermissionContext userPermissionContext;
	private final ManagerSubstituteService managerSubstituteService;
	private final FunctionService functionService;

	private static final Section permissionEntity = Section.ORGUNIT;
	private final ItSystemService itSystemService;

	@RequestMapping(value = "/ui/ous/list")
	public String list(Model model) {
		PermissionConstraint editConstraints = userPermissionContext.getConstraint(permissionEntity, Permission.UPDATE);

		List<OUListForm> allOUs = orgUnitService.getAllCached()
				.stream()
				.map(ou -> new OUListForm(ou, (editConstraints.allowsOrgunit(ou.getUuid()))))
				.sorted(Comparator.comparing(OUListForm::getText))
				.collect(Collectors.toList());

		model.addAttribute("allOUs", allOUs);

		return "ous/list";
	}

	record ManagerOrSubstituteDTO(String name, String userId) {}
	record FunctionDTO(String uuid, String name, boolean checked) {}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.READ)
	@GetMapping("/ui/ous/manage/{uuid}")
	public String manage(Model model, @PathVariable("uuid") String uuid) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}

		PermissionConstraint editConstraints = userPermissionContext.getConstraint(permissionEntity, Permission.UPDATE);

		boolean editable = userPermissionContext.hasPermission(permissionEntity, Permission.UPDATE)
				&& editConstraints.allowsOrgunit(ou.getUuid());

		boolean isAssigner = userPermissionContext.getConstraint(permissionEntity, Permission.ASSIGN)
			.allowsOrgunit(ou.getUuid());

		List<OrgUnitLevel> allowedLevels = orgUnitService.getAllowedLevels(ou);

		List<OrgUnit> parentsKleIsInheritedFrom = new ArrayList<>();
		getParentsKleIsInheritedFrom(parentsKleIsInheritedFrom, ou.getParent());

		List<UserListDTO> userDTOs = getUserDTOs(ou);

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

		model.addAttribute("allowedLevels", allowedLevels);
		model.addAttribute("editable", editable);
		model.addAttribute("canAssign", isAssigner);
		model.addAttribute("ou", ou);
		model.addAttribute("kleUiEnabled", configuration.getIntegrations().getKle().isUiEnabled());
		model.addAttribute("parentsKleIsInheritedFrom", parentsKleIsInheritedFrom);
		model.addAttribute("users", userDTOs);
		model.addAttribute("canEditKle", isAssigner || SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR));
		model.addAttribute("allowAccessToOu", accessConstraintService.isAssignmentAllowed(ou));

		//Titles
		boolean titlesEnabled = configuration.getTitles().isEnabled();
		if (titlesEnabled) {
			List<Title> titles = orgUnitService.getTitles(ou);

			List<TitleListForm> titleForms = titles
					.stream()
					.map(title -> new TitleListForm(title, false))
					.collect(Collectors.toList());

			List<String> titleFormsUuids = titleForms.stream().map(TitleListForm::getId).toList();
			titleForms.addAll(ou.getTitles().stream().filter(t -> !titleFormsUuids.contains(t.getUuid())).map(t -> new TitleListForm(t, true)).toList());

			// add titles without positions if they are used for assignments
			List<String> newTitleFormsUuids = titleForms.stream().map(TitleListForm::getId).collect(Collectors.toList());
			for (OrgUnitUserRoleAssignment userRoleAssignment : ou.getUserRoleAssignments()) {
				for (Title title : userRoleAssignment.getTitles()) {
					if (!newTitleFormsUuids.contains(title.getUuid())) {
						titleForms.add(new TitleListForm(title, false, true));
						newTitleFormsUuids.add(title.getUuid());
					}
				}
			}
			for (OrgUnitRoleGroupAssignment roleGroupAssignment : ou.getRoleGroupAssignments()) {
				for (Title title : roleGroupAssignment.getTitles()) {
					if (!newTitleFormsUuids.contains(title.getUuid())) {
						titleForms.add(new TitleListForm(title, false, true));
						newTitleFormsUuids.add(title.getUuid());
					}
				}
			}

			model.addAttribute("titles", titleForms);

			List<TitleListForm> allTitles = titleService.getAll().stream().map(title -> new TitleListForm(title, false)).toList();
			model.addAttribute("allTitles", allTitles);
		}
		else {
			model.addAttribute("titles", null);
		}
		model.addAttribute("titlesEnabled", titlesEnabled);

		User manager = orgUnitService.getManager(ou);
		model.addAttribute("manager", manager == null ? null : new ManagerOrSubstituteDTO(manager.getName(), manager.getUserId()));
		List<User> substitutesForOrgUnit = managerSubstituteService.getSubstitutesForOrgUnit(ou);
		model.addAttribute("substitutes", substitutesForOrgUnit
				.stream()
				.map(s -> new ManagerOrSubstituteDTO(s.getName(), s.getUserId()))
				.collect(Collectors.toList())
		);

		List<Function> functions = functionService.getAllActive();
		model.addAttribute("functions", functions
				.stream()
				.map(f -> new FunctionDTO(f.getUuid(), f.getName(), false))
				.collect(Collectors.toList())
		);
		return "ous/manage";
	}

	@GetMapping("/ui/ous/view/{uuid}")
	@RequirePermission(section = Section.ORGUNIT, permission = Permission.READ)
	public String view(Model model, @PathVariable("uuid") String uuid) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}

		boolean editable = false;
		boolean isAssigner = userPermissionContext.getConstraint(permissionEntity, Permission.ASSIGN)
			.allowsOrgunit(ou.getUuid());

		List<OrgUnitLevel> allowedLevels = orgUnitService.getAllowedLevels(ou);

		List<UserListDTO> userDTOs = getUserDTOs(ou);

		model.addAttribute("allKles", List.of());

		model.addAttribute("allowedLevels", allowedLevels);
		model.addAttribute("editable", editable);
		model.addAttribute("canAssign", isAssigner);
		model.addAttribute("ou", ou);
		model.addAttribute("kleUiEnabled", false);
		model.addAttribute("parentsKleIsInheritedFrom", List.of());
		model.addAttribute("users", userDTOs);
		model.addAttribute("canEditKle", false);
		model.addAttribute("allowAccessToOu", accessConstraintService.isAssignmentAllowed(ou));

		//Titles
		boolean titlesEnabled = configuration.getTitles().isEnabled();
		if (titlesEnabled) {
			List<Title> titles = orgUnitService.getTitles(ou);

			List<TitleListForm> titleForms = titles
					.stream()
					.map(title -> new TitleListForm(title, false))
					.collect(Collectors.toList());

			List<String> titleFormsUuids = titleForms.stream().map(TitleListForm::getId).toList();
			titleForms.addAll(ou.getTitles().stream().filter(t -> !titleFormsUuids.contains(t.getUuid())).map(t -> new TitleListForm(t, true)).toList());

			// add titles without positions if they are used for assignments
			List<String> newTitleFormsUuids = titleForms.stream().map(TitleListForm::getId).collect(Collectors.toList());
			for (OrgUnitUserRoleAssignment userRoleAssignment : ou.getUserRoleAssignments()) {
				for (Title title : userRoleAssignment.getTitles()) {
					if (!newTitleFormsUuids.contains(title.getUuid())) {
						titleForms.add(new TitleListForm(title, false, true));
						newTitleFormsUuids.add(title.getUuid());
					}
				}
			}
			for (OrgUnitRoleGroupAssignment roleGroupAssignment : ou.getRoleGroupAssignments()) {
				for (Title title : roleGroupAssignment.getTitles()) {
					if (!newTitleFormsUuids.contains(title.getUuid())) {
						titleForms.add(new TitleListForm(title, false, true));
						newTitleFormsUuids.add(title.getUuid());
					}
				}
			}

			model.addAttribute("titles", titleForms);

			List<TitleListForm> allTitles = titleService.getAll().stream().map(title -> new TitleListForm(title, false)).toList();
			model.addAttribute("allTitles", allTitles);
		}
		else {
			model.addAttribute("titles", null);
		}
		model.addAttribute("titlesEnabled", titlesEnabled);

		User manager = orgUnitService.getManager(ou);
		model.addAttribute("manager", manager == null ? null : new UserRoleController.ManagerOrSubstituteDTO(manager.getName(), manager.getUserId()));
		List<User> substitutesForOrgUnit = managerSubstituteService.getSubstitutesForOrgUnit(ou);
		model.addAttribute("substitutes", substitutesForOrgUnit
				.stream()
				.map(s -> new UserRoleController.ManagerOrSubstituteDTO(s.getName(), s.getUserId()))
				.collect(Collectors.toList())
		);

		return "ous/manage";
	}

	@RequestMapping(value = "/ui/ous/manage/{uuid}/roles")
	@RequirePermission(section = Section.ORGUNIT, permission = Permission.READ)
	public String getAssignedRolesFragment(Model model, @PathVariable("uuid") String uuid) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}

		PermissionConstraint readConstraints = userPermissionContext.getConstraint(Section.ORGUNIT, Permission.READ);
		PermissionConstraint editConstraints = userPermissionContext.getConstraint(Section.ORGUNIT, Permission.UPDATE);

		if (!readConstraints.allowsOrgunit(ou.getUuid())) {
			throw new NotPermittedException("No permissions to read orgunit "+uuid, Section.ORGUNIT, Permission.READ);
		}

		boolean ouEditable = editConstraints.allowsOrgunit(ou.getUuid());
		boolean isAssigner = userPermissionContext.getConstraint(permissionEntity, Permission.ASSIGN)
			.allowsOrgunit(ou.getUuid());

		List<RoleAssignedToOrgUnitDTO> assignments = new ArrayList<>();
		if (userPermissionContext.hasPermission(Section.USER_ROLE, Permission.READ)) {
			List<RoleAssignedToOrgUnitDTO> userRoleAssignments = orgUnitService.getAllUserRolesAssignedToOrgUnit(ou).stream()
					.filter(oua -> readConstraints.allowsITSystem(oua.getItSystem().getId()))
					.toList();
			assignments.addAll(userRoleAssignments);
		}
		if (userPermissionContext.hasPermission(Section.ROLE_GROUP, Permission.READ)) {
			List<RoleAssignedToOrgUnitDTO> roleGroupAssignments = orgUnitService.getAllRoleGroupsAssignedToOrgUnit(ou).stream()
					.filter(oua -> oua.getItSystem() == null || readConstraints.allowsITSystem(oua.getItSystem().getId()))
					.toList();
			assignments.addAll(roleGroupAssignments);
		}

		assignments.addAll(findAdditionalRolesforOU(assignments, ou, ouEditable));

		model.addAttribute("assignments", assignments);
		model.addAttribute("editable", ouEditable);
		model.addAttribute("canAssign", isAssigner);

		return "ous/fragments/manage_roles :: ouAssignedRoles";
	}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.ASSIGN)
	@RequestMapping(value = "/ui/ous/manage/{uuid}/addUserRole")
	public String getAddUserRoleFragment(Model model, @PathVariable("uuid") String uuid) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}

		List<AvailableUserRoleDTO> roles = getAvailableUserRoles(ou);

		model.addAttribute("roles", roles);
		return "ous/fragments/manage_add_userrole :: addUserRole";
	}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.ASSIGN)
	@RequestMapping(value = "/ui/ous/manage/{uuid}/addRoleGroup")
	public String getAddRoleGroupFragment(Model model, @PathVariable("uuid") String uuid) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}

		List<AvailableRoleGroupDTO> roleGroups = getAvailableRoleGroups(ou);

		model.addAttribute("roleGroups", roleGroups);
		return "ous/fragments/manage_add_rolegroup :: addRoleGroup";
	}

	@RequestMapping(value = "/ui/ous/manage/{uuid}/kle/{type}")
	public String getKleFragment(Model model, @PathVariable("uuid") String uuid, @PathVariable("type") String type) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}

		switch (type) {
		case "PERFORMING":
			model.addAttribute("kles", kleService.getKleAssignments(ou, KleType.PERFORMING, true));
			break;
		case "INTEREST":
			model.addAttribute("kles", kleService.getKleAssignments(ou, KleType.INTEREST, true));
			break;

		default:
			return "redirect:../list";
		}

		return "fragments/manage_kle :: kle";
	}

	@RequestMapping(value = "/ui/ous/manage/{uuid}/kleEdit/{type}")
	public String getKleEditFragment(Model model, @PathVariable("uuid") String uuid, @PathVariable("type") String type) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}

		switch (type) {
		case "PERFORMING":
			model.addAttribute("selectedKles", ou.getKles().stream().filter(userKLEMapping -> userKLEMapping.getAssignmentType().equals(KleType.PERFORMING)).map(KLEMapping::getCode).collect(Collectors.toList()));
			break;
		case "INTEREST":
			model.addAttribute("selectedKles", ou.getKles().stream().filter(userKLEMapping -> userKLEMapping.getAssignmentType().equals(KleType.INTEREST)).map(KLEMapping::getCode).collect(Collectors.toList()));
			break;

		default:
			return "redirect:../list";
		}

		return "fragments/manage_kle_edit :: kleEdit";
	}

	@RequestMapping(value = "/ui/ous/manage/users/{uuid}")
	public String getExceptedUsersRoleFragment(Model model, @PathVariable("uuid") String uuid) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}

		// Get users
		List<User> users = userService.findByOrgUnit(ou);

		ArrayList<ExceptedUsersDTO> usersDTOS = new ArrayList<>();
		for (User user : users) {
			usersDTOS.add(new ExceptedUsersDTO(user, false));
		}

		model.addAttribute("exceptedUsersDTO", usersDTOS);

		return "ous/fragments/ou_excepted_users :: table";
	}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.ASSIGN)
	@RequestMapping(value = "/ui/ous/manage/role/exceptedusers2/{uuid}/{assignmentId}")
	public String getExceptedUsersRoleFragment2(Model model, @PathVariable("uuid") String uuid, @PathVariable("assignmentId") long assignmentId) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}

		// Get users
		List<User> users = userService.findByOrgUnit(ou);
		ArrayList<ExceptedUsersDTO> usersDTOS = new ArrayList<>();

		OrgUnitUserRoleAssignment roleAssignment = ou.getUserRoleAssignments().stream().filter(ura -> ura.getId() == assignmentId).findAny().orElse(null);
		if (roleAssignment != null) {
			List<String> exceptedUsers = roleAssignment.getExceptedUsers().stream().map(User::getUuid).collect(Collectors.toList());

			for (User user : users) {
				usersDTOS.add(new ExceptedUsersDTO(user, exceptedUsers.contains(user.getUuid())));
			}
		}

		model.addAttribute("exceptedUsersDTO", usersDTOS);
		return "ous/fragments/ou_excepted_users :: table";
	}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.ASSIGN)
	@RequestMapping(value = "/ui/ous/manage/rolegroup/exceptedusers/{uuid}/{rolegroupid}")
	public String getExceptedUsersRoleGroupFragment(Model model, @PathVariable("uuid") String uuid, @PathVariable("rolegroupid") long roleGroupId) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}

		// Get users
		List<User> users = userService.findByOrgUnit(ou);

		// Check if role is assigned to ou already
		RoleGroup roleGroup = roleGroupService.getById(roleGroupId);
		Optional<OrgUnitRoleGroupAssignment> match = ou.getRoleGroupAssignments()
				.stream()
				.filter(roleGroupAssignment -> Objects.equals(roleGroupAssignment.getRoleGroup().getId(), roleGroup.getId()))
				.findAny();

		// If present check mark excepted users
		List<String> exceptedUsers = new ArrayList<>();
		if (match.isPresent()) {
			OrgUnitRoleGroupAssignment roleGroupAssignment = match.get();

			if (roleGroupAssignment.isContainsExceptedUsers()) {
				exceptedUsers = roleGroupAssignment.getExceptedUsers().stream().map(User::getUuid).collect(Collectors.toList());
			}
		}

		ArrayList<ExceptedUsersDTO> usersDTOS = new ArrayList<>();
		for (User user : users) {
			usersDTOS.add(new ExceptedUsersDTO(user, exceptedUsers.contains(user.getUuid())));
		}

		model.addAttribute("exceptedUsersDTO", usersDTOS);
		return "ous/fragments/ou_excepted_users :: table";
	}

	@RequirePermission(section = Section.ORGUNIT, permission = Permission.ASSIGN)
	@RequestMapping(value = "/ui/ous/manage/rolegroup/exceptedusers2/{uuid}/{assignmentId}")
	public String getExceptedUsersRoleGroupFragment2(Model model, @PathVariable("uuid") String uuid, @PathVariable("assignmentId") long assignmentId) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}

		// Get users
		List<User> users = userService.findByOrgUnit(ou);
		ArrayList<ExceptedUsersDTO> usersDTOS = new ArrayList<>();

		OrgUnitRoleGroupAssignment roleAssignment = ou.getRoleGroupAssignments().stream().filter(rga -> rga.getId() == assignmentId).findAny().orElse(null);
		if (roleAssignment != null) {
			List<String> exceptedUsers = roleAssignment.getExceptedUsers().stream().map(User::getUuid).collect(Collectors.toList());

			for (User user : users) {
				usersDTOS.add(new ExceptedUsersDTO(user, exceptedUsers.contains(user.getUuid())));
			}
		}

		model.addAttribute("exceptedUsersDTO", usersDTOS);
		return "ous/fragments/ou_excepted_users :: table";
	}

	@RequireAssignerRole
	@RequestMapping(value = "/ui/ous/manage/requestapprove/{uuid}")
	public String getRequestApproveFragment(Model model, @PathVariable("uuid") String uuid) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}

		boolean isAssigner = userPermissionContext.getConstraint(permissionEntity, Permission.ASSIGN)
			.allowsOrgunit(ou.getUuid());

		boolean readOnly = !(isAssigner || SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR));
		List<String> ousThatCanBeEdited = accessConstraintService.getConstrainedOrgUnits(true);

		model.addAttribute("ou", ou);
		model.addAttribute("editable", !readOnly && (ousThatCanBeEdited == null || ousThatCanBeEdited.contains(ou.getUuid())));

		return "ous/fragments/request_approve :: requestApproveTable";
	}

	private List<UserListDTO> getUserDTOs(OrgUnit ou) {
		List<User> users = userService.findByOrgUnit(ou);
		List<UserListDTO> userDTOs = new ArrayList<UserListDTO>();
		for (User user : users) {
			UserListDTO userDTO = new UserListDTO();
			userDTO.setId(user.getUuid());
			userDTO.setName(user.getName());
			userDTO.setUserId(user.getUserId());
			String title = user.getPositions().stream().filter(p -> Objects.equals(p.getOrgUnit().getUuid(),ou.getUuid())).map(p -> p.getName()).collect(Collectors.joining(", "));
			userDTO.setTitle(title);
			userDTOs.add(userDTO);
		}

		return userDTOs;
	}

	private void getParentsKleIsInheritedFrom(List<OrgUnit> parentsKleIsInheritedFrom, OrgUnit orgUnit) {
		if (orgUnit == null) {
			return;
		}

		if (orgUnit.isInheritKle()) {
			parentsKleIsInheritedFrom.add(orgUnit);
		}

		getParentsKleIsInheritedFrom(parentsKleIsInheritedFrom, orgUnit.getParent());
	}

	private List<AvailableUserRoleDTO> getAvailableUserRoles(OrgUnit orgUnit) {
		List<AvailableUserRoleDTO> addRoles = new ArrayList<>();
		PermissionConstraint readConstraint = userPermissionContext.getConstraint(Section.ORGUNIT, Permission.READ);
		PermissionConstraint assignConstraint = userPermissionContext.getConstraint(Section.ORGUNIT, Permission.ASSIGN);

		boolean isAdmin = SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR);

		List<UserRole> userRoles = userRoleService.getAll().stream()
			.filter(ur ->
				!ur.isReadOnly()
					&& !ur.isUserOnly()
					&& !ur.isAllowPostponing() // filter out roles that allows postponing
					&& readConstraint.allowsITSystem(ur.getItSystem().getId()) // it system must be in constraints for READ
			)
			.toList();

		List<RoleAssignedToOrgUnitDTO> assignments = orgUnitService.getAllUserRolesAssignedToOrgUnit(orgUnit);

		for (UserRole role : userRoles) {
			boolean isInternal = role.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER);
			boolean isAssignable = isAdmin // admins can always assign
				|| (!isInternal // non-admins cannot assign internal roles
				&& assignConstraint.allowsITSystem(role.getItSystem().getId()) // it system must be in constraints
				&& assignConstraint.allowsOrgunit(orgUnit.getUuid())); // orgunit must be in constraints

			AvailableUserRoleDTO availableUserRole = new AvailableUserRoleDTO();
			availableUserRole.setId(role.getId());
			availableUserRole.setName(role.getName());
			availableUserRole.setDescription(role.getDescription());
			availableUserRole.setItSystem(role.getItSystem());
			availableUserRole.setAlreadyAssigned(assignments.stream().filter(a -> a.getType() == RoleAssignmentType.USERROLE).anyMatch(a -> a.getRoleId() == role.getId()));
			availableUserRole.setAssignable(isAssignable);

			addRoles.add(availableUserRole);
		}

		return addRoles;
	}

	public List<AvailableRoleGroupDTO> getAvailableRoleGroups(OrgUnit orgUnit) {
		PermissionConstraint readConstraint = userPermissionContext.getConstraint(Section.ROLE_GROUP, Permission.READ);

		// Get ALL rolegroups
		List<RoleGroup> roleGroups = roleGroupService.getAll();

		List<RoleAssignedToOrgUnitDTO> assignments = orgUnitService.getAllRoleGroupsAssignedToOrgUnit(orgUnit);

		PermissionConstraint assignConstraint = userPermissionContext.getConstraint(Section.ORGUNIT, Permission.ASSIGN);
		long rolecatalogueId = itSystemService.getFirstByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER).getId();
		boolean isAdmin = SecurityUtil.hasDirectAdminRole();

		// Map to DTO's, filtering out any not within READ constraints
		List<AvailableRoleGroupDTO> addRoleGroups = new ArrayList<>();
		for (RoleGroup roleGroup : roleGroups) {
			Set<Long> containedRoleItsystemIds = roleGroup.getUserRoleAssignments().stream()
				.map(ura -> ura.getUserRole().getItSystem().getId())
				.collect(Collectors.toSet());

			if (!readConstraint.allowsAllITSystems(containedRoleItsystemIds)
				|| !readConstraint.allowsOrgunit(orgUnit.getUuid())) {
				// if READ constraints disallow the orgunit or any of the it system for roles, the group is not shown
				continue;
			}


			boolean containsInternalSystem =  containedRoleItsystemIds.contains(rolecatalogueId);
			// Calculate if this user can assign the group
			boolean isAssignable = isAdmin || // Admins can always assign...
				(!containsInternalSystem // internal roles are not assignable by non-admins
					&& assignConstraint.allowsOrgunit(orgUnit.getUuid())  // the orgunit must be within ASSIGN constraints
					&& assignConstraint.allowsAllITSystems(containedRoleItsystemIds)); // ALL itsystems for roles contained in group must be within ASSIGN constraints

			AvailableRoleGroupDTO rgr = new AvailableRoleGroupDTO();
			rgr.setId(roleGroup.getId());
			rgr.setName(roleGroup.getName());
			rgr.setDescription(roleGroup.getDescription());
			rgr.setAlreadyAssigned(assignments.stream().filter(a -> a.getType() == RoleAssignmentType.ROLEGROUP).anyMatch(a -> a.getRoleId() == roleGroup.getId()));
			rgr.setAssignable(isAssignable);

			addRoleGroups.add(rgr);
		}

		return addRoleGroups;
	}

	private List<RoleAssignedToOrgUnitDTO> findAdditionalRolesforOU(List<RoleAssignedToOrgUnitDTO> assignments, OrgUnit ou, boolean ouEditable)  {
		List<RoleAssignedToOrgUnitDTO> extraJfrs = new ArrayList<>();

		Map<Permission, PermissionConstraint> constraintMap = userPermissionContext.getConstraintsPerPermission(permissionEntity);
		PermissionConstraint editConstraints = constraintMap.getOrDefault(Permission.UPDATE, new PermissionConstraint(null, null));

		// Run through all assignments already found
		for (RoleAssignedToOrgUnitDTO assignment : assignments) {

			// Determine if it is directly assigned, or if it is inherited from another ou
			boolean directlyAssignedRole = ((assignment.getAssignedThrough() == AssignedThrough.DIRECT) || (assignment.getAssignedThrough() == AssignedThrough.TITLE));

			if (assignment.getType() == RoleAssignmentType.USERROLE) {
				// additional roles can only be edited if they are directly assigned
				boolean editable = directlyAssignedRole
						&& editConstraints.allowsITSystem(assignment.getItSystem().getId());
				assignment.setCanEdit(editable);
			} else if (assignment.getType() == RoleAssignmentType.ROLEGROUP) {
				boolean editable = directlyAssignedRole
					&& (assignment.getItSystem() == null || editConstraints.allowsITSystem(assignment.getItSystem().getId()));
				assignment.setCanEdit(editable);

				// expand userRoles within the RoleGroup
				RoleGroup roleGroup = roleGroupService.getById(assignment.getRoleId());
				if (roleGroup != null && roleGroup.getUserRoleAssignments() != null
					&& !roleGroup.getUserRoleAssignments().isEmpty()
				) {
					for (RoleGroupUserRoleAssignment userRoleAssignment : roleGroup.getUserRoleAssignments()) {
						extraJfrs.add(RoleAssignedToOrgUnitDTO.fromRoleGroupUserRoleAssignment(userRoleAssignment, assignment));
					}
				}
			}
		}
		return extraJfrs;
	}
}
