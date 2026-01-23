package dk.digitalidentity.rc.controller.mvc;

import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.EditRolegroupRow;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.TitleListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleAddOrgUnitDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleCheckedDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleForm;
import dk.digitalidentity.rc.controller.validator.UserRoleValidator;
import dk.digitalidentity.rc.controller.viewmodel.EditSystemRoleRow;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ConstraintTypeSupport;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.FunctionService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PNumberService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SENumberService;
import dk.digitalidentity.rc.service.Select2Service;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.TitleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.OrgUnitWithRole2;
import dk.digitalidentity.rc.service.model.UserWithRole;
import dk.digitalidentity.rc.service.model.UserWithRoleAndDates;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequireControllerPermission(section = Section.USER_ROLE, permission = Permission.READ)
@RequiredArgsConstructor
@Controller
public class UserRoleController {
	private final UserRoleService userRoleService;
	private final ItSystemService itSystemService;
	private final SystemRoleService systemRoleService;
	private final UserRoleValidator userRoleValidator;
	private final Select2Service select2Service;
	private final ConstraintTypeService constraintTypeService;
	private final SettingsService settingsService;
	private final UserService userService;
	private final KleService kleService;
	private final OrgUnitService orgUnitService;
	private final RoleCatalogueConfiguration configuration;
	private final RoleGroupService roleGroupService;
	private final SENumberService seNumberService;
	private final PNumberService pNumberService;
	private final TitleService titleService;
	private final UserPermissionContext userPermissionContext;
	private final ManagerSubstituteService managerSubstituteService;
	private final FunctionService functionService;

	@InitBinder(value = { "role" })
	public void initBinder(WebDataBinder binder) {
		binder.addValidators(userRoleValidator);
	}

	private static final Section permissionEntity = Section.USER_ROLE;

	@GetMapping(value = {"/ui/userroles/list"})
	public String list(Model model, Principal principal) throws Exception {
		return "userroles/list";
	}

	@GetMapping(value = "/ui/userroles/view/{id}")
	public String view(Model model, @PathVariable("id") long id, Principal principal) throws Exception {
		UserRole role = userRoleService.getById(id);
		if (role == null) {
			return "redirect:../list";
		}
		boolean allowedByConstraints = userPermissionContext.getConstraint(permissionEntity, Permission.READ).allowsITSystem(role.getItSystem().getId());
		if (!allowedByConstraints) {
			return "redirect:../list";
		}

		UserRoleForm roleForm = new UserRoleForm(role, false, false);
		List<RoleGroup> roleGroups = roleGroupService.getAll();
		roleGroups = roleGroups.stream()
				.filter(rg -> rg.getUserRoleAssignments().stream().anyMatch(ass -> ass.getUserRole().equals(role)))
				.collect(Collectors.toList());

		roleForm.setRoleGroups(roleGroups);
		model.addAttribute("role", roleForm);

		boolean titlesEnabled = configuration.getTitles().isEnabled();
		model.addAttribute("titlesEnabled", titlesEnabled);

		boolean hideRolegroups = false;
		if (role.isAllowPostponing()) {
			hideRolegroups = true;
		}

		List<ItSystem> roleCatalogue = itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
		if (roleCatalogue != null && !roleCatalogue.isEmpty() && role.getItSystem().equals(roleCatalogue.getFirst())) {
				hideRolegroups = true;
			}


		model.addAttribute("hideRolegroups", hideRolegroups);
		model.addAttribute("allowPostponing", role.isAllowPostponing());

		return "userroles/view";
	}

	@GetMapping(value = "/ui/userroles/{id}/assignedUsersFragment")
	public String assignedUsersFragment(Model model, @PathVariable("id") long userRoleId, @RequestParam(name = "showEdit", required = false, defaultValue = "false") boolean showEdit) {
		UserRole role = userRoleService.getById(userRoleId);
		if (role == null) {
			return "redirect:../list";
		}

		List<UserWithRole> usersWithRoleMapping = userService.getUsersWithUserRole(role, true).stream()
				// Filter by allowed orgunits
				.filter(uwr -> userPermissionContext.getConstraint(Section.USER, Permission.READ).allowsOrgunit(uwr.getAssignment().getOrgUnitUuid()))
				.toList();

		// force-load positions (Thymeleaf does not respect @BatchSize for some reason, and this is much faster - and Thymeleaf needs the orgunit.name)
		usersWithRoleMapping.forEach(u -> u.getUser().getPositions().forEach(p -> p.getOrgUnit().getName()));

		model.addAttribute("userRoleMapping", usersWithRoleMapping);
		model.addAttribute("showEdit", showEdit);

		return "userroles/fragments/manage_users :: users";
	}

	@GetMapping(value = "/ui/userroles/{id}/assignedUsersFragmentView")
	public String assignedUsersFragmentView(Model model, @PathVariable("id") long userRoleId) {
		UserRole role = userRoleService.getById(userRoleId);
		if (role == null) {
			return "redirect:../list";
		}

		List<UserWithRole> usersWithRole = userService.getUsersWithUserRole(role, true).stream()
				// Filter by allowed orgunits
				.filter(uwr -> userPermissionContext.getConstraint(Section.USER, Permission.READ).allowsOrgunit(uwr.getAssignment().getOrgUnitUuid()))
				.toList();
		model.addAttribute("userRoleMapping", usersWithRole);

		return "userroles/fragments/view_users :: users";
	}

	@GetMapping(value = "/ui/userroles/{id}/availableUsersFragment")
	public String availableUsersFragment(Model model, @PathVariable("id") long userRoleId) {
		UserRole role = userRoleService.getById(userRoleId);
		if (role == null) {
			return "redirect:../list";
		}

		return "userroles/fragments/manage_add_users :: addUsers";
	}

	@GetMapping(value = "/ui/userroles/{id}/assignedOrgUnitsFragment")
	public String assignedOrgUnitsFragment(Model model, @PathVariable("id") long userRoleId, @RequestParam(name = "showEdit", required = false, defaultValue = "false") boolean showEdit) {
		UserRole role = userRoleService.getById(userRoleId);
		if (role == null) {
			return "redirect:../list";
		}

		Map<Permission, PermissionConstraint> constraintMap = userPermissionContext.getConstraintsPerPermission(permissionEntity);
		PermissionConstraint readConstraint = constraintMap.get(Permission.READ);

		List<OrgUnitWithRole2> orgUnitsWithRole = orgUnitService.getActiveOrgUnitsWithUserRole(role).stream()
				.filter(ouwr -> readConstraint.allowsOrgunit(ouwr.getOuUuid()))
				.toList();
		model.addAttribute("orgUnitMapping", orgUnitsWithRole);
		model.addAttribute("showEdit", showEdit);
		model.addAttribute("showCreateBtn", !role.isUserOnly());

		return "userroles/fragments/manage_ous :: ous";
	}

	@GetMapping(value = "/ui/userroles/{id}/availableOrgUnitsFragment")
	public String availableOrgUnitsFragment(Model model, @PathVariable("id") long userRoleId) {
		UserRole role = userRoleService.getById(userRoleId);
		if (role == null) {
			return "redirect:../list";
		}

		Map<Permission, PermissionConstraint> constraintMap = userPermissionContext.getConstraintsPerPermission(permissionEntity);
		PermissionConstraint readConstraint = constraintMap.get(Permission.READ);

		List<OrgUnit> ousFromDb = orgUnitService.getAllCached();
		var availableOrgUnits = ousFromDb.stream()
				.filter(ou ->readConstraint.allowsOrgunit(ou.getUuid())) // filter by constraints
				.map(UserRoleAddOrgUnitDTO::new)
				.toList();

		model.addAttribute("ous", availableOrgUnits);
		return "userroles/fragments/manage_add_ous :: addOrgUnits";
	}

	@RequirePermission(section = Section.USER_ROLE, permission = Permission.CREATE)
	@GetMapping(value = "/ui/userroles/new")
	public String newGet(Model model) {
		PermissionConstraint permissionConstraint = userPermissionContext.getConstraint(permissionEntity, Permission.CREATE);
		model.addAttribute("role", new UserRoleForm());
		model.addAttribute("itSystems", itSystemService.getVisible().stream()
				.filter( its -> permissionConstraint.allowsITSystem(its.getId()))
				.toList());
		return "userroles/new";
	}

	@RequirePermission(section = Section.USER_ROLE, permission = Permission.CREATE)
	@PostMapping(value = "/ui/userroles/new")
	public String newPost(Model model, @Valid @ModelAttribute("role") UserRoleForm roleForm, BindingResult bindingResult) {
		PermissionConstraint permissionConstraint = userPermissionContext.getConstraint(permissionEntity, Permission.CREATE);
		PermissionConstraint updateConstraint = userPermissionContext.getConstraint(permissionEntity, Permission.UPDATE);

		if (bindingResult.hasErrors()) {
			model.addAttribute("role", roleForm);
			model.addAttribute("itSystems", itSystemService.getVisible().stream()
					.filter( its -> permissionConstraint.allowsITSystem(its.getId()))
					.toList()
			);

			return "userroles/new";
		}

		UserRole role = roleForm.toUserRole();
		if (!permissionConstraint.allowsITSystem(roleForm.getItSystem().getId())) {
			return "userroles/new";
		}


		if (!StringUtils.hasLength(roleForm.getIdentifier())) {
			role.setIdentifier("id-" + UUID.randomUUID().toString());
		} else {
			role.setIdentifier(roleForm.getIdentifier().replaceAll("[\\s]", ""));
		}

		role = userRoleService.save(role);

		return "redirect:edit/" + role.getId();
	}

	@RequirePermission(section = Section.USER_ROLE, permission = Permission.UPDATE)
	@GetMapping(value = "/ui/userroles/edit/{id}/userFragment")
	public String editGetUserFragment(Model model, @PathVariable("id") long id) {
		UserRole role = userRoleService.getById(id);
		if (role == null) {
			return "redirect:../list";
		}

		List<User> usersFromDb = userService.getAll();
		List<UserWithRoleAndDates> usersWithRole = userService.getUsersWithUserRoleDirectlyAssigned(role);

		List<String> uuidsWithRole = usersWithRole.stream().map(u -> u.getUser().getUuid()).collect(Collectors.toList());
		List<UserRoleCheckedDTO> users = new ArrayList<>();

		for (User user : usersFromDb) {
			LocalDate startDate = null;
			LocalDate stopDate = null;
			boolean checked = false;

			if (uuidsWithRole.contains(user.getUuid())) {
				checked = true;
				//We know it exists because of the if, and there should only be one
				UserWithRoleAndDates userWithRole = usersWithRole.stream().filter(u -> u.getUser().getUuid().equals(user.getUuid())).findAny().orElse(null);
				startDate = userWithRole.getStartDate();
				stopDate = userWithRole.getStopDate();
			}

			UserRoleCheckedDTO dto = new UserRoleCheckedDTO();
			dto.setName(user.getName());
			dto.setUuid(user.getUuid());
			dto.setUserId(user.getUserId());
			dto.setChecked(checked);
			dto.setStartDate(startDate);
			dto.setStopDate(stopDate);

			users.add(dto);
		}

		model.addAttribute("users", users);
		model.addAttribute("roleId", id);

		return "userroles/editUserFragment";
	}

	@RequirePermission(section = Section.USER_ROLE, permission = Permission.UPDATE)
	@GetMapping(value = "/ui/userroles/edit/{id}")
	public String editGet(Model model, @PathVariable("id") long id) {
		UserRole role = userRoleService.getById(id);
		boolean constraintsAllowAccess = userPermissionContext.getConstraint(permissionEntity, Permission.UPDATE).allowsITSystem(role.getItSystem().getId());
		if (role == null || role.isReadOnly() || !constraintsAllowAccess) {
			return "redirect:../list";
		}

		ItSystem itSystem = role.getItSystem();
		List<SystemRole> systemRoles = systemRoleService.getByItSystem(itSystem);

		List<EditSystemRoleRow> editSystemRoles = mapToEditSystemRoleRow(systemRoles, role);
		model.addAttribute("editSystemRoles", editSystemRoles);

		Map<Long, Set<String>> systemRoleComboMultiConstraintUuids = mapToSystemRoleComboMultiConstraintUuids(systemRoles);
		model.addAttribute("systemRoleComboMultiConstraintUuids", systemRoleComboMultiConstraintUuids);

		List<Kle> kles = kleService.findAll();
		List<KleDTO> kleDTOS = mapToKleDTOs(kles);
		model.addAttribute("kleList", kleDTOS);

		model.addAttribute("role", new UserRoleForm(role, false, false));
		model.addAttribute("roleId", id);

		// TODO: way to much mapping logic - refactor to deal with this
		model.addAttribute("ouConstraintUuid", getOUConstraintUuid());
		model.addAttribute("kleConstraintUuid", getKLEConstraintUuid());
		model.addAttribute("itSystemConstraintUuid", getItSystemConstraintUuid());
		model.addAttribute("internalOuConstraintUuid", getInternalOuConstraintUuid());

		List<RoleGroup> roleGroups = roleGroupService.getAll();
		List<EditRolegroupRow> editRoleGroups = mapToEditRolegroupRows(roleGroups, role);
		model.addAttribute("editRoleGroups", editRoleGroups);

		model.addAttribute("orgUnitList", select2Service.getOrgUnitList());
		model.addAttribute("itSystemList", select2Service.getItSystemList());
		model.addAttribute("titlesEnabled", configuration.getTitles().isEnabled());
		model.addAttribute("allowPostponing", role.isAllowPostponing());

		if (itSystem.getSystemType().equals(ItSystemType.NEMLOGIN)) {
			model.addAttribute("pNumberList", pNumberService.getAll());
			model.addAttribute("sENumberList", seNumberService.getAll());
		}

		List<ItSystem> roleCatalogueCandidates = itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
		ItSystem roleCatalogue = null;
		if (!roleCatalogueCandidates.isEmpty()) {
			roleCatalogue = roleCatalogueCandidates.getFirst();
		}
		model.addAttribute("hideRolegroups", shouldRolegroupsBeHidden(role, roleCatalogue));

		Map<Permission, PermissionConstraint> orgunitConstraintMap = userPermissionContext.getConstraintsPerPermission(permissionEntity);
		PermissionConstraint readOrgunitConstraint = orgunitConstraintMap.getOrDefault(Permission.READ, new PermissionConstraint(null, null));
		List<OUListForm> ouListForms = orgUnitService.getAllCached().stream()
			.filter(ou -> readOrgunitConstraint.allowsOrgunit(ou.getUuid()))
			.map(ou -> new OUListForm(ou, false))
			.toList();

		List<String> selectedOus = role.getOrgUnitFilterOrgUnits().stream()
			.map(OrgUnit::getUuid)
			.filter(readOrgunitConstraint::allowsOrgunit)
			.toList();

		model.addAttribute("treeOUs", ouListForms);
		model.addAttribute("selectedFilterOUs", selectedOus);

		model.addAttribute("caseNumberEnabled", settingsService.isCaseNumberEnabled());

		return "userroles/edit";
	}

	private boolean shouldRolegroupsBeHidden(UserRole role, ItSystem roleCatalogue) {
		return role.isAllowPostponing() || role.getItSystem().equals(roleCatalogue);
	}

	private List<KleDTO> mapToKleDTOs(List<Kle> kles) {
		List<KleDTO> kleDTOS = new ArrayList<>();
		for (Kle kle : kles) {
			KleDTO kleDTO = new KleDTO();
			kleDTO.setId(kle.getCode());
			kleDTO.setParent(kle.getParent().equals("0") ? "#" : kle.getParent());

			String code = kle.getCode().replaceAll("\\.\\*", "");
			kleDTO.setText(kle.isActive() ? code + " " + kle.getName() : code + " " + kle.getName() + " [UDGÅET]");
			kleDTOS.add(kleDTO);
		}
		return kleDTOS;
	}

	private Map<Long, Set<String>> mapToSystemRoleComboMultiConstraintUuids(List<SystemRole> systemRoles) {
		Map<Long, Set<String>> systemRoleComboMultiConstraintUuids = new HashMap<>();
		for (SystemRole systemRole : systemRoles) {
			for (ConstraintTypeSupport supportedConstraintType : systemRole.getSupportedConstraintTypes()) {
				if (supportedConstraintType.getConstraintType().getUiType().equals(ConstraintUIType.COMBO_MULTI)) {
					if (!systemRoleComboMultiConstraintUuids.containsKey(systemRole.getId())) {
						systemRoleComboMultiConstraintUuids.put(systemRole.getId(), new HashSet<>());
					}
					systemRoleComboMultiConstraintUuids.get(systemRole.getId()).add(supportedConstraintType.getConstraintType().getUuid());
				}
			}
		}
		return systemRoleComboMultiConstraintUuids;
	}

	private List<EditSystemRoleRow> mapToEditSystemRoleRow(List<SystemRole> systemRoles, UserRole role) {
		List<EditSystemRoleRow> editSystemRoles = new ArrayList<>();
		for (SystemRole systemRole : systemRoles) {
			EditSystemRoleRow esr = new EditSystemRoleRow();
			esr.setChecked(false);

			for (SystemRoleAssignment roleAssignment : role.getSystemRoleAssignments()) {
				if (roleAssignment.getSystemRole().equals(systemRole)) {
					esr.setChecked(true);

					for (SystemRoleAssignmentConstraintValue constraint : roleAssignment.getConstraintValues()) {
						esr.getSelectedConstraints().put(constraint.getConstraintType().getUuid(), constraint);
					}
				}
			}

			esr.setId(systemRole.getId());
			esr.setSystemRole(systemRole);
			editSystemRoles.add(esr);
		}
		return editSystemRoles;
	}

	private List<EditRolegroupRow> mapToEditRolegroupRows(List<RoleGroup> roleGroups, UserRole role) {
		return roleGroups.stream().map(roleGroup -> {
			EditRolegroupRow erg = new EditRolegroupRow();
			erg.setChecked(false);
			for (RoleGroupUserRoleAssignment assignment : roleGroup.getUserRoleAssignments()) {
				if (assignment.getUserRole().equals(role)) {
					erg.setChecked(true);
				}
			}

			erg.setRoleGroup(roleGroup);
			return erg;
		}).toList();
	}

	private String getOUConstraintUuid() {
		ConstraintType ouConstraintType = constraintTypeService.getByEntityId(Constants.OU_CONSTRAINT_ENTITY_ID);
		return (ouConstraintType != null) ? ouConstraintType.getUuid() : "NA";
	}

	private String getKLEConstraintUuid() {
		ConstraintType kleConstraintType = constraintTypeService.getByEntityId(Constants.KLE_CONSTRAINT_ENTITY_ID);
		return (kleConstraintType != null) ? kleConstraintType.getUuid() : "NA";
	}

	private String getItSystemConstraintUuid() {
		ConstraintType internalItSystemConstraintType = constraintTypeService.getByEntityId(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID);
		return (internalItSystemConstraintType != null) ? internalItSystemConstraintType.getUuid() : "NA";
	}

	private String getInternalOuConstraintUuid() {
		ConstraintType internalOuConstraintType = constraintTypeService.getByEntityId(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID);
		return (internalOuConstraintType != null) ? internalOuConstraintType.getUuid() : "NA";
	}

	@GetMapping(value = "/ui/userroles/fragments/{uuid}")
	public String getFragment(Model model, @PathVariable("uuid") String uuid) {
		User user = userService.getByUuid(uuid);
		if (user != null) {
			model.addAttribute("positions", user.getPositions());
			model.addAttribute("possibleOrgUnits", orgUnitService.getOrgUnitsForUser(user));
			model.addAttribute("caseNumberEnabled", settingsService.isCaseNumberEnabled());
		}

		return "users/fragments/user_user_role_modal :: userUserRoleModal";
	}

	@GetMapping(value = "/ui/userroles/scriptFragment")
	public String getFragmentScripts(Model model) {
		model.addAttribute("userRoleListTableId", "listTable1");
		model.addAttribute("page", "role");

		return "users/fragments/user_user_role_modal :: userUserRoleModalScript";
	}

	record ManagerOrSubstituteDTO(String name, String userId) {}
	record FunctionDTO(String uuid, String name, boolean checked) {}
	@GetMapping(value = "/ui/userroles/fragments/ou/{uuid}")
	public String getOUFragment(Model model, @PathVariable("uuid") String uuid, @RequestParam(name = "edit", required = false, defaultValue = "false") boolean edit) {
		OrgUnit orgUnit = orgUnitService.getByUuid(uuid);
		boolean titlesEnabled = configuration.getTitles().isEnabled();

		if (titlesEnabled && orgUnit != null) {
			List<Title> titles = orgUnitService.getTitles(orgUnit);

			List<TitleListForm> titleForms = titles
					.stream()
					.map(title -> new TitleListForm(title, false))
					.collect(Collectors.toList());

			List<String> titleFormsUuids = titleForms.stream().map(TitleListForm::getId).toList();
			titleForms.addAll(orgUnit.getTitles().stream().filter(t -> !titleFormsUuids.contains(t.getUuid())).map(t -> new TitleListForm(t, true)).collect(Collectors.toList()));

			model.addAttribute("titles", titleForms);

			List<TitleListForm> allTitles = titleService.getAll().stream().map(title -> new TitleListForm(title, false)).toList();
			model.addAttribute("allTitles", allTitles);
		} else {
			model.addAttribute("titles", null);
		}

		if (orgUnit != null) {
			User manager = orgUnitService.getManager(orgUnit);
			model.addAttribute("manager", manager == null ? null : new ManagerOrSubstituteDTO(manager.getName(), manager.getUserId()));
			List<User> substitutesForOrgUnit = managerSubstituteService.getSubstitutesForOrgUnit(orgUnit);
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
		}

		model.addAttribute("titlesEnabled", titlesEnabled);

		return "ous/fragments/ou_roles_modal :: ouRolesModal";
	}

	private User getUserOrThrow(String userId) throws Exception {
		User user = userService.getByUserId(userId);
		if (user == null) {
			throw new Exception("Ukendt bruger: " + userId);
		}

		return user;
	}
}
