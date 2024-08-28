package dk.digitalidentity.rc.controller.mvc;

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
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.security.RequireRequesterOrReadAccessRole;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PNumberService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SENumberService;
import dk.digitalidentity.rc.service.Select2Service;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.OrgUnitWithRole2;
import dk.digitalidentity.rc.service.model.UserWithRole;
import dk.digitalidentity.rc.service.model.UserWithRole2;
import dk.digitalidentity.rc.service.model.UserWithRoleAndDates;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RequireRequesterOrReadAccessRole
@Controller
public class UserRoleController {

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private UserRoleValidator userRoleValidator;

	@Autowired
	private Select2Service select2Service;

	@Autowired
	private ConstraintTypeService constraintTypeService;

	@Autowired
	private SettingsService settingsService;

	@Autowired
	private UserService userService;

	@Autowired
	private KleService kleService;

	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private RoleGroupService roleGroupService;
	
	@Autowired
	private SENumberService seNumberService;
	
	@Autowired
	private PNumberService pNumberService;

	@InitBinder(value = { "role" })
	public void initBinder(WebDataBinder binder) {
		binder.addValidators(userRoleValidator);
	}

	@GetMapping(value = { "/ui/userroles/list" })
	public String list(Model model, Principal principal) throws Exception {
		return "userroles/list";
	}

	@GetMapping(value = "/ui/userroles/view/{id}")
	public String view(Model model, @PathVariable("id") long id, Principal principal) throws Exception {
		UserRole role = userRoleService.getById(id);
		if (role == null) {
			return "redirect:../list";
		}

		UserRoleForm roleForm = new UserRoleForm(role, false, false);
		List<RoleGroup> roleGroups = roleGroupService.getAll();
		roleGroups = roleGroups.stream()
				.filter(rg -> rg.getUserRoleAssignments().stream().anyMatch(ass -> ass.getUserRole().equals(role)))
				.collect(Collectors.toList());

		roleForm.setRoleGroups(roleGroups);
		model.addAttribute("role", roleForm);

		boolean canRequest = false;
		if (settingsService.isRequestApproveEnabled()) {
			User user = getUserOrThrow(principal.getName());
			canRequest = userRoleService.canRequestRole(role, user);
		}

		model.addAttribute("canRequest", canRequest);

		boolean titlesEnabled = configuration.getTitles().isEnabled();
		model.addAttribute("titlesEnabled", titlesEnabled);
		
		boolean hideRolegroups = false;
		if (role.isAllowPostponing()) {
			hideRolegroups = true;
		}
		
		List<ItSystem> roleCatalogue = itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
		if (roleCatalogue != null && roleCatalogue.size() >= 1) {
			if (role.getItSystem().equals(roleCatalogue.get(0))) {
				hideRolegroups = true;
			}
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

		List<UserWithRole2> usersWithRoleMapping = userService.getActiveUsersWithUserRole(role);
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

		List<UserWithRole> usersWithRole = userService.getUsersWithUserRole(role, true);
		model.addAttribute("userRoleMapping", usersWithRole);
		
		return "userroles/fragments/view_users :: users";
	}

	@GetMapping(value = "/ui/userroles/{id}/availableUsersFragment")
	public String availableUsersFragment(Model model, @PathVariable("id") long userRoleId) {
		UserRole role = userRoleService.getById(userRoleId);
		if (role == null) {
			return "redirect:../list";
		}

		List<User> usersFromDb = userService.getAll();

		model.addAttribute("users", usersFromDb);
		return "userroles/fragments/manage_add_users :: addUsers";
	}

	@GetMapping(value = "/ui/userroles/{id}/assignedOrgUnitsFragment")
	public String assignedOrgUnitsFragment(Model model, @PathVariable("id") long userRoleId, @RequestParam(name = "showEdit", required = false, defaultValue = "false") boolean showEdit) {
		UserRole role = userRoleService.getById(userRoleId);
		if (role == null) {
			return "redirect:../list";
		}

		List<OrgUnitWithRole2> orgUnitsWithRole = orgUnitService.getActiveOrgUnitsWithUserRole(role);
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

		List<OrgUnit> ousFromDb = orgUnitService.getAllCached();
		var availableOrgUnits = ousFromDb.stream().map(ou -> new UserRoleAddOrgUnitDTO(ou)).collect(Collectors.toList());

		model.addAttribute("ous", availableOrgUnits);
		return "userroles/fragments/manage_add_ous :: addOrgUnits";
	}

	@RequireAdministratorRole
	@GetMapping(value = "/ui/userroles/new")
	public String newGet(Model model) {
		model.addAttribute("role", new UserRoleForm());
		model.addAttribute("itSystems", itSystemService.getVisible());

		return "userroles/new";
	}

	@RequireAdministratorRole
	@PostMapping(value = "/ui/userroles/new")
	public String newPost(Model model, @Valid @ModelAttribute("role") UserRoleForm roleForm, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			model.addAttribute("role", roleForm);
			model.addAttribute("itSystems", itSystemService.getAll());

			return "userroles/new";
		}

		UserRole role = roleForm.toUserRole();
		
		if (!StringUtils.hasLength(roleForm.getIdentifier())) {
			role.setIdentifier("id-" + UUID.randomUUID().toString());
		}
		else {
			role.setIdentifier(roleForm.getIdentifier().replaceAll("[\\s]", ""));
		}
		
		role = userRoleService.save(role);

		return "redirect:edit/" + role.getId();
	}
	
	@RequireAdministratorRole
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

	@RequireAdministratorRole
	@GetMapping(value = "/ui/userroles/edit/{id}")
	public String editGet(Model model, @PathVariable("id") long id) {
		UserRole role = userRoleService.getById(id);
		if (role == null) {
			return "redirect:../list";
		}
		
		UserRoleForm userRoleForm = new UserRoleForm(role, false, false);
		ItSystem itSystem = role.getItSystem();
		List<SystemRole> systemRoles = systemRoleService.getByItSystem(itSystem);
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

		List<EditRolegroupRow> editRoleGroups = new ArrayList<>();
		List<RoleGroup> roleGroups = roleGroupService.getAll();

		for (RoleGroup roleGroup : roleGroups) {
			EditRolegroupRow erg = new EditRolegroupRow();
			erg.setChecked(false);

			for (RoleGroupUserRoleAssignment assignment : roleGroup.getUserRoleAssignments()) {
				if (assignment.getUserRole().equals(role)) {
					erg.setChecked(true);
				}
			}

			erg.setRoleGroup(roleGroup);
			editRoleGroups.add(erg);
		}

		ConstraintType ouConstraintType = constraintTypeService.getByEntityId(Constants.OU_CONSTRAINT_ENTITY_ID);
		ConstraintType kleConstraintType = constraintTypeService.getByEntityId(Constants.KLE_CONSTRAINT_ENTITY_ID);
		ConstraintType internalItSystemConstraintType = constraintTypeService.getByEntityId(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID);
		ConstraintType internalOuConstraintType = constraintTypeService.getByEntityId(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID);

		List<Kle> kles = kleService.findAll();
		List<KleDTO> kleDTOS = new ArrayList<>();
		for (Kle kle : kles) {
			KleDTO kleDTO = new KleDTO();
			kleDTO.setId(kle.getCode());
			kleDTO.setParent(kle.getParent().equals("0") ? "#" : kle.getParent());

			String code = kle.getCode().replaceAll("\\.\\*", "");
			kleDTO.setText(kle.isActive() ? code + " " + kle.getName() : code + " " + kle.getName() + " [UDGÅET]");
			kleDTOS.add(kleDTO);
		}

		model.addAttribute("role", userRoleForm);
		// TODO: way to much mapping logic - refactor to deal with this
		model.addAttribute("ouConstraintUuid", (ouConstraintType != null) ? ouConstraintType.getUuid() : "NA");
		model.addAttribute("kleConstraintUuid", (kleConstraintType != null) ? kleConstraintType.getUuid() : "NA");
		model.addAttribute("itSystemConstraintUuid", (internalItSystemConstraintType != null) ? internalItSystemConstraintType.getUuid() : "NA");
		model.addAttribute("internalOuConstraintUuid", (internalOuConstraintType != null) ? internalOuConstraintType.getUuid() : "NA");
		model.addAttribute("editSystemRoles", editSystemRoles);
		model.addAttribute("editRoleGroups", editRoleGroups);
		model.addAttribute("kleList", kleDTOS);
		model.addAttribute("orgUnitList", select2Service.getOrgUnitList());
		model.addAttribute("itSystemList", select2Service.getItSystemList());
		model.addAttribute("roleId", id);
		model.addAttribute("titlesEnabled", configuration.getTitles().isEnabled());
		model.addAttribute("allowPostponing", role.isAllowPostponing());
		
		if (role.getItSystem().getSystemType().equals(ItSystemType.NEMLOGIN)) {
			model.addAttribute("pNumberList", pNumberService.getAll());
			model.addAttribute("sENumberList", seNumberService.getAll());
		}
		
		boolean hideRolegroups = false;
		if (role.isAllowPostponing()) {
			hideRolegroups = true;
		}
		
		List<ItSystem> roleCatalogue = itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
		if (roleCatalogue != null && roleCatalogue.size() >= 1) {
			if (role.getItSystem().equals(roleCatalogue.get(0))) {
				hideRolegroups = true;
			}
		}
		
		model.addAttribute("hideRolegroups", hideRolegroups);
		
		
		List<OUListForm> treeOUs = orgUnitService.getAllCached()
				.stream()
				.map(ou -> new OUListForm(ou, false))
				.sorted(Comparator.comparing(OUListForm::getText))
				.collect(Collectors.toList());

		model.addAttribute("treeOUs", treeOUs);

		return "userroles/edit";
	}
	
	@GetMapping(value = "/ui/userroles/fragments/{uuid}")
	public String getFragment(Model model, @PathVariable("uuid") String uuid) {
		User user = userService.getByUuid(uuid);
		if (user != null) {
			model.addAttribute("positions", user.getPositions());
			model.addAttribute("possibleOrgUnits", orgUnitService.getOrgUnitsForUser(user));
		}
		
		return "users/fragments/user_user_role_modal :: userUserRoleModal";	
	}
	
	@GetMapping(value = "/ui/userroles/scriptFragment")
	public String getFragmentScripts(Model model) {
		model.addAttribute("userRoleListTableId", "listTable1");
		model.addAttribute("page", "role");
		
		return "users/fragments/user_user_role_modal :: userUserRoleModalScript";
	}

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
			
			List<String> titleFormsUuids = titleForms.stream().map(t -> t.getId()).collect(Collectors.toList());
			titleForms.addAll(orgUnit.getTitles().stream().filter(t -> !titleFormsUuids.contains(t.getUuid())).map(t -> new TitleListForm(t, true)).collect(Collectors.toList()));
			
			model.addAttribute("titles", titleForms);
		}
		else {
			model.addAttribute("titles", null);
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
