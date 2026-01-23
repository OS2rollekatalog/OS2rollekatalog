package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.RoleGroupForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.TitleListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleAddOrgUnitDTO;
import dk.digitalidentity.rc.controller.rest.model.ItemPermissionDTO;
import dk.digitalidentity.rc.controller.validator.RolegroupValidator;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import dk.digitalidentity.rc.service.FunctionService;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.TitleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.OrgUnitWithRole2;
import dk.digitalidentity.rc.service.model.UserWithRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RequireControllerPermission(section = Section.ROLE_GROUP, permission = Permission.READ)
@Controller
public class RoleGroupController {

	private final ModelMapper mapper;
	private final RoleGroupService roleGroupService;
	private final RolegroupValidator rolegroupValidator;
	private final UserService userService;
	private final OrgUnitService orgUnitService;
    private final RoleCatalogueConfiguration configuration;
	private final TitleService titleService;
	private final UserPermissionContext userPermissionContext;
	private final ManagerSubstituteService managerSubstituteService;
	private final FunctionService functionService;
    private final SettingsService settingsService;

	private static final Section permissionEntity = Section.ROLE_GROUP;


    @InitBinder(value = { "rolegroup" })
	public void initBinder(WebDataBinder binder) {
		binder.addValidators(rolegroupValidator);
	}

	record RoleGroupListItemDTO(Long id, String name, String description, boolean userOnly, List<ApprovableBy> approverPermission, List<RequestableBy> requesterPermission, ItemPermissionDTO allowedActions) {}
	@GetMapping(value = { "/ui/rolegroups/list" })
	public String list(Model model, Principal principal) throws Exception {
		List<RoleGroup> roles = Collections.emptyList();
		List<ApprovableBy> globalApprovables = settingsService.getRolerequestApprover();
		List<RequestableBy> globalRequestables = settingsService.getRolerequestRequester();
		roles = roleGroupService.getAll();
		roles.forEach(rg -> {
			if (rg.getApproverPermission().contains(ApprovableBy.INHERIT)) {
				rg.getApproverPermission().addAll(globalApprovables);
				rg.getApproverPermission().remove(ApprovableBy.INHERIT);
			}
			if (rg.getRequesterPermission().contains(RequestableBy.INHERIT)) {
				rg.getRequesterPermission().addAll(globalRequestables);
				rg.getRequesterPermission().remove(RequestableBy.INHERIT);
			}
		});

		model.addAttribute("roleGroups", mapToRoleGroupListItemDTO(roles));

		return "rolegroups/list";
	}

	@GetMapping(value = "/ui/rolegroups/view/{id}")
	public String view(Model model, @PathVariable("id") long id, Principal principal) throws Exception {
		RoleGroup roleGroup = roleGroupService.getById(id);
		if (roleGroup == null) {
			return "redirect:../list";
		}

		RoleGroupForm roleGroupForm = mapper.map(roleGroup, RoleGroupForm.class);

		// remove userRoles from soft-deleted it-systems until they are really deleted
		roleGroupForm.setUserRoleAssignments(roleGroup.getUserRoleAssignments().stream()
				.filter(a -> a.getUserRole().getItSystem().isDeleted() == false)
				.collect(Collectors.toList()));

		model.addAttribute("rolegroup", roleGroupForm);

		return "rolegroups/view";
	}

	@RequirePermission(section = Section.ROLE_GROUP, permission = Permission.UPDATE)
	@GetMapping(value = "/ui/rolegroups/edit/{id}")
	public String edit(Model model, @PathVariable("id") long id) {
		RoleGroup roleGroup = roleGroupService.getById(id);
		if (roleGroup == null) {
			return "redirect:../list";
		}

		RoleGroupForm roleGroupForm = mapper.map(roleGroup, RoleGroupForm.class);
		boolean titlesEnabled = configuration.getTitles().isEnabled();

		model.addAttribute("rolegroup", roleGroupForm);
//		model.addAttribute("roles", getAddRoles(roleGroup));
		model.addAttribute("titlesEnabled", titlesEnabled);

		return "rolegroups/edit";
	}

	@RequirePermission( section = Section.ROLE_GROUP, permission = Permission.CREATE)
	@GetMapping(value = "/ui/rolegroups/new")
	public String getCreateRoleGroupView(Model model) {
		model.addAttribute("rolegroup", new RoleGroupForm());

		return "rolegroups/new";
	}

	@RequirePermission( section = Section.ROLE_GROUP, permission = Permission.CREATE)
	@PostMapping(value = "/ui/rolegroups/new")
	public String createNewRolegroup(Model model, @Valid @ModelAttribute("rolegroup") RoleGroupForm rolegroupForm, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			model.addAllAttributes(bindingResult.getModel());
			model.addAttribute("rolegroup", rolegroupForm);
			return "rolegroups/new";
		}

		RoleGroup roleGroup = mapper.map(rolegroupForm, RoleGroup.class);

		roleGroup = roleGroupService.save(roleGroup);
		return "redirect:edit/" + roleGroup.getId();
	}

	@GetMapping(value = "/ui/rolegroups/{id}/assignedUsersFragment")
	public String assignedUsersFragment(Model model, @PathVariable("id") long roleGroupId, @RequestParam(name = "showEdit", required = false, defaultValue = "false") boolean showEdit) {
		RoleGroup group = roleGroupService.getById(roleGroupId);
		if (group == null) {
			return "redirect:../list";
		}
		Map<Permission, PermissionConstraint> constraintMap = userPermissionContext.getConstraintsPerPermission(permissionEntity);
		PermissionConstraint readConstraint = constraintMap.get(Permission.READ);

		List<UserWithRole> usersWithRoleMapping = userService.getUsersWithRoleGroup(group, true).stream()
				// Filter by allowed orgunits
				.filter(uwr -> readConstraint.allowsOrgunit(uwr.getAssignment().getOrgUnitUuid()))
				.toList();
		model.addAttribute("userRoleMapping", usersWithRoleMapping);

		model.addAttribute("showEdit", showEdit);

		return "rolegroups/fragments/manage_users :: users";
	}

	@SuppressWarnings("deprecation")
	@GetMapping(value = "/ui/rolegroups/{id}/assignedUsersFragmentView")
	public String assignedUsersFragmentView(Model model, @PathVariable("id") long roleGroupId) {
		RoleGroup group = roleGroupService.getById(roleGroupId);
		if (group == null) {
			return "redirect:../list";
		}

		Map<Permission, PermissionConstraint> constraintMap = userPermissionContext.getConstraintsPerPermission(permissionEntity);
		PermissionConstraint readConstraint = constraintMap.get(Permission.READ);

		List<UserWithRole> usersWithRoleMapping = userService.getUsersWithRoleGroup(group, true).stream()
				// Filter by allowed orgunits
				.filter(uwr -> readConstraint.allowsOrgunit(uwr.getAssignment().getOrgUnitUuid()))
				.toList();
		model.addAttribute("userRoleMapping", usersWithRoleMapping);

		return "rolegroups/fragments/view_users :: users";
	}

	@GetMapping(value = "/ui/rolegroups/{id}/availableUsersFragment")
	public String availableUsersFragment(Model model, @PathVariable("id") long roleGroupId) {
		RoleGroup group = roleGroupService.getById(roleGroupId);
		if (group == null) {
			return "redirect:../list";
		}

		Map<Permission, PermissionConstraint> constraintMap = userPermissionContext.getConstraintsPerPermission(permissionEntity);
		PermissionConstraint readConstraint = constraintMap.get(Permission.READ);

		List<User> usersFromDb = userService.getAll().stream()
				.filter(u -> readConstraint.getConstrainedOUUuids() == null
						|| u.getPositions().stream().anyMatch(p ->
						readConstraint.getConstrainedOUUuids().contains(p.getOrgUnit().getUuid())))
				.toList();

		model.addAttribute("users", usersFromDb);
		return "rolegroups/fragments/manage_add_users :: addUsers";
	}

	@GetMapping(value = "/ui/rolegroups/{id}/assignedOrgUnitsFragment")
	public String assignedOrgUnitsFragment(Model model, @PathVariable("id") long roleGroupId, @RequestParam(name = "showEdit", required = false, defaultValue = "false") boolean showEdit) {
		RoleGroup group = roleGroupService.getById(roleGroupId);
		if (group == null) {
			return "redirect:../list";
		}

		Map<Permission, PermissionConstraint> constraintMap = userPermissionContext.getConstraintsPerPermission(permissionEntity);
		PermissionConstraint readConstraint = constraintMap.get(Permission.READ);

		List<OrgUnitWithRole2> orgUnitsWithRole = orgUnitService.getActiveOrgUnitsWithRoleGroup(group).stream()
				.filter(ouwr -> readConstraint.allowsOrgunit(ouwr.getOuUuid()))
				.toList();
		model.addAttribute("orgUnitMapping", orgUnitsWithRole);
		model.addAttribute("showEdit", showEdit);
		model.addAttribute("showCreateBtn", !group.isUserOnly());

		return "rolegroups/fragments/manage_ous :: ous";
	}

	@GetMapping(value = "/ui/rolegroups/{id}/availableOrgUnitsFragment")
	public String availableOrgUnitsFragment(Model model, @PathVariable("id") long roleGroupId) {
		RoleGroup group = roleGroupService.getById(roleGroupId);
		if (group == null) {
			return "redirect:../list";
		}

		Map<Permission, PermissionConstraint> constraintMap = userPermissionContext.getConstraintsPerPermission(permissionEntity);
		PermissionConstraint readConstraint = constraintMap.get(Permission.READ);

		List<OrgUnit> ousFromDb = orgUnitService.getAllCached().stream()
				.filter(ou -> readConstraint.allowsOrgunit(ou.getUuid()))
				.toList();
		var availableOrgUnits = ousFromDb.stream().map(ou -> new UserRoleAddOrgUnitDTO(ou)).collect(Collectors.toList());

		model.addAttribute("ous", availableOrgUnits);
		return "rolegroups/fragments/manage_add_ous :: addOrgUnits";
	}

	@GetMapping(value = "/ui/rolegroups/fragments/{uuid}")
	public String getFragment(Model model, @PathVariable("uuid") String uuid) {
		User user = userService.getByUuid(uuid);

		if (user != null) {
			model.addAttribute("positions", user.getPositions());
			model.addAttribute("possibleOrgUnits", orgUnitService.getOrgUnitsForUser(user));
		}

		return "users/fragments/user_role_group_modal :: userRoleGroupModal";

	}

	record ManagerOrSubstituteDTO(String name, String userId) {}
	record FunctionDTO(String uuid, String name, boolean checked) {}
	@GetMapping(value = "/ui/rolegroups/fragments/ou/{uuid}")
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
			titleForms.addAll(orgUnit.getTitles().stream().filter(t -> !titleFormsUuids.contains(t.getUuid())).map(t -> new TitleListForm(t, true)).toList());

			model.addAttribute("titles", titleForms);

			List<TitleListForm> allTitles = titleService.getAll().stream().map(title -> new TitleListForm(title, false)).toList();
			model.addAttribute("allTitles", allTitles);
		}
		else {
			model.addAttribute("titles", null);
		}

		if (orgUnit != null) {
			User manager = orgUnitService.getManager(orgUnit);
			model.addAttribute("manager", manager == null ? null : new ManagerOrSubstituteDTO(manager.getName(), manager.getUserId()));
			model.addAttribute("substitutes", managerSubstituteService.getSubstitutesForOrgUnit(orgUnit)
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

	private List<RoleGroupListItemDTO> mapToRoleGroupListItemDTO(Collection<RoleGroup> roleGroups) {
		Set<Long> readConstraintSystems = userPermissionContext.getConstraint(permissionEntity, Permission.READ).getConstrainedItSystemIds();
		Set<Long> updateConstraintSystems =  userPermissionContext.getConstraint(permissionEntity, Permission.UPDATE).getConstrainedItSystemIds();
		Set<Long> createConstraintSystems =  userPermissionContext.getConstraint(permissionEntity, Permission.CREATE).getConstrainedItSystemIds();
		Set<Long> deleteConstraintSystems =  userPermissionContext.getConstraint(permissionEntity, Permission.DELETE).getConstrainedItSystemIds();

		return roleGroups.stream().map(r -> {
							Set<Long> itSystemIds = r.getUserRoleAssignments().stream().map(u -> u.getUserRole().getItSystem().getId()).collect(Collectors.toSet());

							boolean canRead = (readConstraintSystems == null || readConstraintSystems.containsAll(itSystemIds));
							// if there is no read permission for the relevant it-systems, do not include in list at all
							if (!canRead) {
								return null;
							}

							// You are allowed to modify the rolegroup if you have permissions for ALL itsystems covered by the rolegroup
							ItemPermissionDTO specificAllowedActions = new ItemPermissionDTO(
									(createConstraintSystems == null || (createConstraintSystems.containsAll( itSystemIds) && !createConstraintSystems.isEmpty())),
									true,
									(updateConstraintSystems == null || (updateConstraintSystems.containsAll( itSystemIds) && !updateConstraintSystems.isEmpty())),
									(deleteConstraintSystems == null || (deleteConstraintSystems.containsAll( itSystemIds) && !deleteConstraintSystems.isEmpty()))
							);

							return new RoleGroupListItemDTO(
									r.getId(),
									r.getName(),
									r.getDescription(),
									r.isUserOnly(),
									r.getApproverPermission(),
									r.getRequesterPermission(),
									specificAllowedActions);
						}
				)
				.filter(Objects::nonNull)
				.toList();
	}
}
