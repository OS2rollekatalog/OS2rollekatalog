package dk.digitalidentity.rc.controller.mvc;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.Assignment;
import dk.digitalidentity.rc.controller.mvc.viewmodel.EditRolegroupRow;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.RequestForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.RolesOUTable;
import dk.digitalidentity.rc.controller.mvc.viewmodel.TitleListForm;
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
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.security.RequireRequesterOrReadAccessRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PendingKOMBITUpdateService;
import dk.digitalidentity.rc.service.RequestApproveService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.Select2Service;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.OrgUnitWithRole;
import dk.digitalidentity.rc.service.model.RoleWithDateDTO;
import dk.digitalidentity.rc.service.model.UserWithRole;

@RequireRequesterOrReadAccessRole
@Controller
public class UserRoleController {

	@Autowired
	private MessageSource messageSource;

	@Autowired
	private ModelMapper mapper;

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
	private RequestApproveService requestApproveService;

	@Autowired
	private PendingKOMBITUpdateService kombitService;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private KleService kleService;

	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private RoleGroupService roleGroupService;

	@InitBinder(value = { "role" })
	public void initBinder(WebDataBinder binder) {
		binder.addValidators(userRoleValidator);
	}

	@GetMapping(value = { "/ui/userroles/list" })
	public String list(Model model, Principal principal) throws Exception {
		List<UserRole> roles = userRoleService.getAll();

		// requesters needs to have the list filtered
		if (SecurityUtil.isRequesterAndOnlyRequester()) {
			User user = getUserOrThrow(principal.getName());

			roles = userRoleService.whichRolesCanBeRequestedByUser(roles, user);
		}
		// people with restricted read-only access will be limited
		else if (securityUtil.hasRestrictedReadAccess()) {
			List<Long> itSystems = securityUtil.getRestrictedReadAccessItSystems();

			roles = roles.stream().filter(r -> itSystems.contains(r.getItSystem().getId())).collect(Collectors.toList());
		}

		List<Long> kombitUpdates = kombitService.findAll().stream().map(k -> k.getUserRoleId()).collect(Collectors.toList());
		List<UserRoleForm> userRoles = roles.stream().map(r -> new UserRoleForm(r, kombitUpdates.contains(r.getId()))).collect(Collectors.toList());

		// filter out deleted itSystems
		userRoles = userRoles.stream().filter(ur -> ur.getItSystem().isDeleted() == false).collect(Collectors.toList());

		model.addAttribute("roles", userRoles);

		return "userroles/list";
	}

	@GetMapping(value = "/ui/userroles/view/{id}")
	public String view(Model model, @PathVariable("id") long id, Principal principal) throws Exception {
		UserRole role = userRoleService.getById(id);
		if (role == null) {
			return "redirect:../list";
		}

		UserRoleForm roleForm = mapper.map(role, UserRoleForm.class);
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
		model.addAttribute("requestForm", new RequestForm(id));

		List<UserWithRole> usersWithRoleMapping = userService.getUsersWithUserRole(role, true);
		model.addAttribute("userRoleMapping", usersWithRoleMapping);

		List<OrgUnitWithRole> orgUnits = orgUnitService.getOrgUnitsWithUserRole(role, true);
		model.addAttribute("orgUnits", orgUnits);

		return "userroles/view";
	}

	@PostMapping(value = "/ui/userroles/request")
	public String requestRole(Model model, RequestForm requestForm, RedirectAttributes redirectAttributes, Principal principal) throws Exception {
		UserRole userRole = userRoleService.getById(requestForm.getId());
		User user = getUserOrThrow(principal.getName());
		Locale locale = LocaleContextHolder.getLocale();

		if (!requestApproveService.requestUserRole(userRole, user, requestForm.getReason())) {
			redirectAttributes.addFlashAttribute("errorMessage", messageSource.getMessage("html.page.roles.request.error", null, locale));
		}
		else {
			redirectAttributes.addFlashAttribute("infoMessage", messageSource.getMessage("html.page.roles.request.send", null, locale));
		}

		return "redirect:/ui/userroles/view/" + requestForm.getId();
	}

	@RequireAdministratorRole
	@GetMapping(value = "/ui/userroles/new")
	public String newGet(Model model) {
		model.addAttribute("role", new UserRoleForm());
		model.addAttribute("itSystems", itSystemService.getVisible());

		return "userroles/new";
	}

	// TODO: this is broken because UserRoleForm contains database entity objects, which can not (since Spring Boot 2.1.18) be mapped
	//       to entity classes from some magical string value (no idea why it works today actually)
	//       solution: do not use entity classes in html/form objects please
	@RequireAdministratorRole
	@PostMapping(value = "/ui/userroles/new")
	public String newPost(Model model, @Valid @ModelAttribute("role") UserRoleForm roleForm, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			model.addAttribute("role", roleForm);
			model.addAttribute("itSystems", itSystemService.getAll());

			return "userroles/new";
		}

		UserRole role = mapper.map(roleForm, UserRole.class);
		
		if (roleForm.getIdentifier() == null || roleForm.getIdentifier() == "") {
			role.setIdentifier("id-" + UUID.randomUUID().toString());
		}else {
			role.setIdentifier(roleForm.getIdentifier().replaceAll("[\\s]", ""));
		}
		
		role = userRoleService.save(role);

		return "redirect:edit/" + role.getId();
	}

	@RequireAdministratorRole
	@GetMapping(value = "/ui/userroles/edit/{id}")
	public String editGet(Model model, @PathVariable("id") long id) {
		UserRole role = userRoleService.getById(id);
		if (role == null) {
			return "redirect:../list";
		}

		UserRoleForm userRoleForm = mapper.map(role, UserRoleForm.class);
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
		ConstraintType itSystemConstraintType = constraintTypeService.getByEntityId(Constants.ITSYSTEM_CONSTRAINT_ENTITY_ID);
		ConstraintType internalOuConstraintType = constraintTypeService.getByEntityId(Constants.ORGUNIT_CONSTRAINT_ENTITY_ID);

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
		
		List<User> usersFromDb = userService.getAll();
		List<UserWithRole> usersWithRole = userService.getUsersWithUserRole(role, false);
		
		List<String> uuidsWithRole = usersWithRole.stream().map(u -> u.getUser().getUuid()).collect(Collectors.toList());
		
		List<UserRoleCheckedDTO> users = usersFromDb.stream()
				.map(u -> UserRoleCheckedDTO.builder()
						.uuid(u.getUuid())
						.name(u.getName())
						.userId(u.getUserId())
						.checked(uuidsWithRole.contains(u.getUuid()))
						.build())
				.collect(Collectors.toList());
		
		List<OrgUnit> oUsFromDb = orgUnitService.getAll();
		List<RolesOUTable> allOUs = new ArrayList<>();
		for (OrgUnit ou : oUsFromDb) {
			Assignment a = new Assignment();
			List<RoleWithDateDTO> userRoles = orgUnitService.getNotInheritedUserRolesWithDate(ou);
			RoleWithDateDTO ur = userRoles.stream().filter(r -> r.getId() == role.getId()).findFirst().orElse(null);
			if (ur != null) {
				a.setStartDate(ur.getStartDate());
				a.setStopDate(ur.getStopDate());
				allOUs.add(new RolesOUTable(ou, true, a));
			}else {
				allOUs.add(new RolesOUTable(ou, false, a));
			}
			
		}

		model.addAttribute("role", userRoleForm);
		// TODO: way to much mapping logic - refactor to deal with this
		model.addAttribute("ouConstraintUuid", (ouConstraintType != null) ? ouConstraintType.getUuid() : "NA");
		model.addAttribute("kleConstraintUuid", (kleConstraintType != null) ? kleConstraintType.getUuid() : "NA");
		model.addAttribute("itSystemConstraintUuid", (itSystemConstraintType != null) ? itSystemConstraintType.getUuid() : "NA");
		model.addAttribute("internalOuConstraintUuid", (internalOuConstraintType != null) ? internalOuConstraintType.getUuid() : "NA");
		model.addAttribute("editSystemRoles", editSystemRoles);
		model.addAttribute("editRoleGroups", editRoleGroups);
		model.addAttribute("kleList", kleDTOS);
		model.addAttribute("orgUnitList", select2Service.getOrgUnitList());
		model.addAttribute("itSystemList", select2Service.getItSystemList());
		model.addAttribute("users", users);
		model.addAttribute("roleId", id);
		model.addAttribute("allOUs", allOUs);
		model.addAttribute("titlesEnabled", configuration.getTitles().isEnabled());

		return "userroles/edit";
	}
	
	@GetMapping(value = "/ui/userroles/fragments/{uuid}")
	public String getFragment(Model model, @PathVariable("uuid") String uuid) {
		User user = userService.getByUuid(uuid);
		
		if (user != null) {
			model.addAttribute("positions", user.getPositions());
		}
		
		return "users/fragments/user_user_role_modal :: userUserRoleModal";
		
	}
	
	@GetMapping(value = "/ui/userroles/fragments/ou/{uuid}")
	public String getOUFragment(Model model, @PathVariable("uuid") String uuid) {
		OrgUnit orgUnit = orgUnitService.getByUuid(uuid);
		boolean titlesEnabled = configuration.getTitles().isEnabled();
		
		if (titlesEnabled && orgUnit != null) {
			List<Title> titles = orgUnitService.getTitles(orgUnit);

			List<TitleListForm> titleForms = titles
					.stream()
					.map(title -> new TitleListForm(title))
					.collect(Collectors.toList());
			
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