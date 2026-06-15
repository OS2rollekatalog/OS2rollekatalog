package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.EditRolegroupRow;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.TitleListForm;
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
import dk.digitalidentity.rc.dao.model.UserRoleEmailTemplate;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.ConstraintUIType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.SystemRoleLinkType;
import dk.digitalidentity.rc.security.SecurityUtil;
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
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.RoleAssignedToOrgUnitDTO;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import dk.digitalidentity.rc.service.model.UserWithRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
	private final AssignmentService assignmentService;
	private final Validator validator;

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

		boolean canEdit = SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR);
		boolean isInternalRCRole =Constants.ROLE_CATALOGUE_IDENTIFIER.equals(role.getItSystem().getIdentifier());
		boolean canAssign = (SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR) // must either be admin...
			||	(!isInternalRCRole && userPermissionContext.hasPermission(Section.USER, Permission.ASSIGN))); // ... or have assigning permission AND role cannot be from rolecatalogue

		Set<CurrentAssignment> assignments = assignmentService.getActiveByUserRole(role);

		List<UserWithRole> usersWithRoleMapping = assignments.stream()
			.map(assignment -> {
				AssignedThrough assignedThrough = assignmentService.getAssignedThrough(assignment);
				UserWithRole userWithRole = UserWithRole.fromCurrentAssignment(assignment, assignedThrough, RoleAssignmentType.USERROLE);

				// Only direct assignments can be edited (and only by admins)
				boolean isDirectAssignment = assignedThrough.equals(AssignedThrough.DIRECT);
				userWithRole.getAssignment().setCanEdit(canEdit && isDirectAssignment);

				return userWithRole;
			})
			// Filter by allowed orgunits
			.filter(uwr -> userPermissionContext.getConstraint(Section.USER, Permission.READ).allowsOrgunit(uwr.getAssignment().getOrgUnitUuid()))
			.toList();


		model.addAttribute("userRoleMapping", usersWithRoleMapping);
		model.addAttribute("showEdit", showEdit);
		model.addAttribute("assignmentAddingAllowed", canAssign);

		return "userroles/fragments/manage_users :: users";
	}

	@GetMapping(value = "/ui/userroles/{id}/availableUsersFragment")
	public String availableUsersFragment(Model model, @PathVariable("id") long userRoleId) {
		UserRole role = userRoleService.getById(userRoleId);
		if (role == null) {
			return "redirect:../list";
		}

		return "userroles/fragments/manage_add_users :: addUsers";
	}

	record OrgunitWithRoleAssignedDTO(String ouUuid, String ouName, RoleAssignedToOrgUnitDTO assignment, boolean changeable){}
	@GetMapping(value = "/ui/userroles/{id}/assignedOrgUnitsFragment")
	public String assignedOrgUnitsFragment(Model model, @PathVariable("id") long userRoleId, @RequestParam(name = "showEdit", required = false, defaultValue = "false") boolean showEdit) {
		UserRole role = userRoleService.getById(userRoleId);
		if (role == null) {
			return "redirect:../list";
		}

		PermissionConstraint assignConstraint = userPermissionContext.getConstraint(Section.ORGUNIT, Permission.ASSIGN);
		PermissionConstraint readConstraint = userPermissionContext.getConstraint(Section.ORGUNIT, Permission.READ);

		List<OrgunitWithRoleAssignedDTO> orgUnitsWithRole = orgUnitService.getActiveOrgUnitsWithUserRole(role).stream()
			.filter(ouwr -> readConstraint.allowsOrgunit(ouwr.getOuUuid()))
			.map(o -> new OrgunitWithRoleAssignedDTO(o.ouUuid, o.ouName, o.assignment, assignConstraint.allowsOrgunit(o.ouUuid)))
			.toList();

		boolean isInternalRCRole =Constants.ROLE_CATALOGUE_IDENTIFIER.equals(role.getItSystem().getIdentifier());
		boolean canAssign = !role.isUserOnly() &&
			(SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR) // must either be admin...
				|| (!isInternalRCRole && userPermissionContext.hasPermission(Section.ORGUNIT, Permission.ASSIGN))); // ... or have assigning permission AND role cannot be from rolecatalogue

		model.addAttribute("orgUnitMapping", orgUnitsWithRole);
		model.addAttribute("showEdit", showEdit);
		model.addAttribute("assignmentAddingAllowed", showEdit && canAssign);

		return "userroles/fragments/manage_ous :: ous";
	}

	record AssignableOrgUnitDTO (String name, String uuid, boolean assignable) {}
	@GetMapping(value = "/ui/userroles/{id}/availableOrgUnitsFragment")
	public String availableOrgUnitsFragment(Model model, @PathVariable("id") long userRoleId) {
		UserRole role = userRoleService.getById(userRoleId);
		if (role == null) {
			return "redirect:../list";
		}

		PermissionConstraint assignConstraint = userPermissionContext.getConstraint(Section.ORGUNIT, Permission.ASSIGN);
		PermissionConstraint readConstraint = userPermissionContext.getConstraint(Section.ORGUNIT, Permission.READ);

		List<OrgUnit> ousFromDb = orgUnitService.getAllCached();
		var availableOrgUnits = ousFromDb.stream()
				.filter(ou ->readConstraint.allowsOrgunit(ou.getUuid())) // filter by constraints
				.map(ou -> new AssignableOrgUnitDTO(ou.getName(), ou.getUuid(), assignConstraint.allowsOrgunit(ou.getUuid())))
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

	@RequirePermission(section = Section.USER_ROLE, permission = Permission.CREATE)
	@GetMapping(value = "/ui/userroles/copy/{id}")
	public String copyGet(Model model, @PathVariable("id") long id) {
		UserRole role = userRoleService.getById(id);
		if (role == null) {
			return "redirect:../list";
		}

		PermissionConstraint permissionConstraint = userPermissionContext.getConstraint(permissionEntity, Permission.CREATE);
		if (!permissionConstraint.allowsITSystem(role.getItSystem().getId())) {
			return "redirect:../list";
		}

		UserRoleForm copyDto = new UserRoleForm(role, false, false);
		copyDto.setName("Kopi af " + role.getName());

		model.addAttribute("role", copyDto);
		return "userroles/copy";
	}

	@RequirePermission(section = Section.USER_ROLE, permission = Permission.CREATE)
	@PostMapping(value = "/ui/userroles/copy/{id}")
	public String copyPost(Model model, @PathVariable("id") long id, @ModelAttribute("role") UserRoleForm roleForm, BindingResult bindingResult) {
		UserRole roleToCopy = userRoleService.getById(id);
		if (roleToCopy == null) {
			return "redirect:../list";
		}

		PermissionConstraint permissionConstraint = userPermissionContext.getConstraint(permissionEntity, Permission.CREATE);

		roleForm.setItSystem(roleToCopy.getItSystem());
		if (!permissionConstraint.allowsITSystem(roleForm.getItSystem().getId())) {
			return "redirect:../list";
		}

		validator.validate(roleForm, bindingResult);

		if (bindingResult.hasErrors()) {
			model.addAllAttributes(bindingResult.getModel());
			model.addAttribute("role", roleForm);
			return "userroles/copy";
		}

		UserRole role = copyUserRole(roleForm, roleToCopy);

		role = userRoleService.save(role);

		return "redirect:/ui/userroles/edit/" + role.getId();
	}

	@RequirePermission(section = Section.USER_ROLE, permission = Permission.UPDATE)
	@GetMapping(value = "/ui/userroles/edit/{id}/userFragment")
	public String editGetUserFragment(Model model, @PathVariable("id") long id) {
		UserRole role = userRoleService.getById(id);
		if (role == null) {
			return "redirect:../list";
		}

		List<User> usersFromDb = userService.getAll();

		// Get assignments (both direct and from roleGroups)
		Set<CurrentAssignment> assignments = assignmentService.getByUserRoleDirectlyAssignedOrFromRoleGroupIncludingInactive(role);

		// Group by user to handle multiple assignments per user (e.g., if a user has the role both directly and via roleGroup)
		Map<String, CurrentAssignment> assignmentsByUser = new HashMap<>();
		for (CurrentAssignment assignment : assignments) {
			// Keep first assignment for each user (prefer direct over roleGroup)
			if (!assignmentsByUser.containsKey(assignment.getUser().getUuid())) {
				assignmentsByUser.put(assignment.getUser().getUuid(), assignment);
			} else {
				// If user already exists, prefer direct assignment
				CurrentAssignment existing = assignmentsByUser.get(assignment.getUser().getUuid());
				AssignedThrough existingThrough = assignmentService.getAssignedThrough(existing);
				AssignedThrough newThrough = assignmentService.getAssignedThrough(assignment);
				if (existingThrough.equals(AssignedThrough.ROLEGROUP) && newThrough.equals(AssignedThrough.DIRECT)) {
					assignmentsByUser.put(assignment.getUser().getUuid(), assignment);
				}
			}
		}

		List<UserRoleCheckedDTO> users = new ArrayList<>();

		for (User user : usersFromDb) {
			LocalDate startDate = null;
			LocalDate stopDate = null;
			boolean checked = false;

			CurrentAssignment assignment = assignmentsByUser.get(user.getUuid());
			if (assignment != null) {
				checked = true;
				startDate = assignment.getStartDate();
				stopDate = assignment.getStopDate();
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
	record OuDTO(String uuid, String name, boolean checked) {}
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

			List<OuDTO> children = orgUnitService.getAllDescendantsOfOu(orgUnit).stream()
				.map(ou -> new OuDTO(ou.getUuid(), ou.getName(), false))
				.collect(Collectors.toList());
			model.addAttribute("childOus", children);
		}

		model.addAttribute("titlesEnabled", titlesEnabled);

		return "ous/fragments/ou_roles_modal :: ouRolesModal";
	}

	private UserRole copyUserRole(UserRoleForm roleForm, UserRole roleToCopy) {
		UserRole role = roleForm.toUserRole();

		// copy fields not in the UI from the original role
		role.setUserOnly(roleToCopy.isUserOnly());
		role.setSensitiveRole(roleToCopy.isSensitiveRole());
		role.setExtraSensitiveRole(roleToCopy.isExtraSensitiveRole());
		role.setRequesterPermission(roleToCopy.getRequesterPermission());
		role.setApproverPermission(roleToCopy.getApproverPermission());
		role.setRequireManagerAction(roleToCopy.isRequireManagerAction());
		role.setSendToSubstitutes(roleToCopy.isSendToSubstitutes());
		role.setSendToAuthorizationManagers(roleToCopy.isSendToAuthorizationManagers());
		role.setRoleAssignmentAttestationByAttestationResponsible(roleToCopy.isRoleAssignmentAttestationByAttestationResponsible());
		role.setReadOnly(roleToCopy.isReadOnly());
		role.setOuFilterEnabled(roleToCopy.isOuFilterEnabled());
		role.setDelegatedFromCvr(roleToCopy.getDelegatedFromCvr());
		role.setContactEmail(roleToCopy.getContactEmail());
		role.setAdvisEmail(roleToCopy.getAdvisEmail());
		role.setAllowPostponing(roleToCopy.isAllowPostponing());

		// copy email template if present
		if (roleToCopy.getUserRoleEmailTemplate() != null) {
			UserRoleEmailTemplate template = new UserRoleEmailTemplate();
			template.setTitle(roleToCopy.getUserRoleEmailTemplate().getTitle());
			template.setMessage(roleToCopy.getUserRoleEmailTemplate().getMessage());
			template.setUserRole(role);
			role.setUserRoleEmailTemplate(template);
		}

		// remove id
		role.setId(0);

		// clear uuid - for KOMBIT roles it is assigned by FK Administrationsmodulet on create,
		// and a non-null uuid makes the sync attempt an update against a non-existing role
		role.setUuid(null);

		// set identifier
		role.setIdentifier("id-" + UUID.randomUUID().toString());

		// remove linked system role
		role.setLinkedSystemRole(null);
		role.setSystemRoleLinkType(SystemRoleLinkType.NONE);
		role.setLinkedSystemRolePrefix(null);

		// copy ou filter
		if (role.isOuFilterEnabled()) {
			role.setOrgUnitFilterOrgUnits(new ArrayList<>(roleToCopy.getOrgUnitFilterOrgUnits()));
		}

		// copy systemRoles and constraints from roleToCopy
		copySystemRolesAndConstraints(roleToCopy, role);
		return role;
	}

	private void copySystemRolesAndConstraints(UserRole roleToCopy, UserRole role) {
		role.setSystemRoleAssignments(new ArrayList<>());
		for (SystemRoleAssignment systemRoleAssignmentToCopy : roleToCopy.getSystemRoleAssignments()) {
			SystemRoleAssignment roleAssignment = new SystemRoleAssignment();
			roleAssignment.setUserRole(role);
			roleAssignment.setSystemRole(systemRoleAssignmentToCopy.getSystemRole());
			roleAssignment.setAssignedByName(SecurityUtil.getUserFullname());
			roleAssignment.setAssignedByUserId(SecurityUtil.getUserId());
			roleAssignment.setAssignedTimestamp(new Date());

			roleAssignment.setConstraintValues(new ArrayList<>());
			for (SystemRoleAssignmentConstraintValue constraintValueToCopy : systemRoleAssignmentToCopy.getConstraintValues()) {
				SystemRoleAssignmentConstraintValue constraint = new SystemRoleAssignmentConstraintValue();
				constraint.setSystemRoleAssignment(roleAssignment);
				constraint.setConstraintType(constraintValueToCopy.getConstraintType());
				constraint.setConstraintIdentifier(constraintValueToCopy.getConstraintIdentifier());
				constraint.setConstraintValueType(constraintValueToCopy.getConstraintValueType());
				constraint.setPostponed(constraintValueToCopy.isPostponed());
				constraint.setConstraintValue(constraintValueToCopy.getConstraintValue());
				roleAssignment.getConstraintValues().add(constraint);
			}

			role.getSystemRoleAssignments().add(roleAssignment);
		}
	}

}
