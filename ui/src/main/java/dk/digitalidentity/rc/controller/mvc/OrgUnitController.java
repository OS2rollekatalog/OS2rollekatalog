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
import dk.digitalidentity.rc.security.RequireReadAccessOrManagerRole;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.RoleAssignedToOrgUnitDTO;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequireReadAccessOrManagerRole
@Controller
public class OrgUnitController {

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;
	
	@Autowired
	private KleService kleService;

	@Autowired
	private AccessConstraintService assignerRoleConstraint;

    @Autowired
	private UserService userService;

    @Autowired
    private RoleCatalogueConfiguration configuration;

    @Value("#{servletContext.contextPath}")
    private String servletContextPath;

	@RequestMapping(value = "/ui/ous/list")
	public String list(Model model) {
		final List<String> list = assignerRoleConstraint.getConstrainedOrgUnits(true);

		List<OUListForm> allOUs = orgUnitService.getAllCached()
				.stream()
				.map(ou -> new OUListForm(ou, (list == null || list.contains(ou.getUuid()))))
				.sorted(Comparator.comparing(OUListForm::getText))
				.collect(Collectors.toList());

		model.addAttribute("allOUs", allOUs);

		return "ous/list";
	}

	@RequireReadAccessRole
	@GetMapping("/ui/ous/manage/{uuid}")
	public String manage(Model model, @PathVariable("uuid") String uuid) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}

		boolean readOnly = !(SecurityUtil.hasRole(Constants.ROLE_ASSIGNER) || SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR));
		List<String> ousThatCanBeEdited = assignerRoleConstraint.getConstrainedOrgUnits(true);

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
		model.addAttribute("editable", !readOnly && (ousThatCanBeEdited == null || ousThatCanBeEdited.contains(ou.getUuid())));
		model.addAttribute("ou", ou);
		model.addAttribute("kleUiEnabled", configuration.getIntegrations().getKle().isUiEnabled());
		model.addAttribute("parentsKleIsInheritedFrom", parentsKleIsInheritedFrom);
		model.addAttribute("users", userDTOs);
		model.addAttribute("canEditKle", SecurityUtil.hasRole(Constants.ROLE_ASSIGNER) || SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR));
		model.addAttribute("allowAccessToOu", assignerRoleConstraint.isAssignmentAllowed(ou));
		
		//Titles
		boolean titlesEnabled = configuration.getTitles().isEnabled();
		if (titlesEnabled) {
			List<Title> titles = orgUnitService.getTitles(ou);

			List<TitleListForm> titleForms = titles
					.stream()
					.map(title -> new TitleListForm(title, false))
					.collect(Collectors.toList());
			
			List<String> titleFormsUuids = titleForms.stream().map(t -> t.getId()).collect(Collectors.toList());
			titleForms.addAll(ou.getTitles().stream().filter(t -> !titleFormsUuids.contains(t.getUuid())).map(t -> new TitleListForm(t, true)).collect(Collectors.toList()));

			// add titles without positions if they are used for assignments
			List<String> newTitleFormsUuids = titleForms.stream().map(t -> t.getId()).collect(Collectors.toList());
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
		}
		else {
			model.addAttribute("titles", null);
		}
		model.addAttribute("titlesEnabled", titlesEnabled);


		return "ous/manage";
	}

	@RequireReadAccessRole
	@RequestMapping(value = "/ui/ous/manage/{uuid}/roles")
	public String getAssignedRolesFragment(Model model, @PathVariable("uuid") String uuid) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}
		
		boolean readOnly = !(SecurityUtil.hasRole(Constants.ROLE_ASSIGNER) || SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR));
		List<String> ousThatCanBeEdited = assignerRoleConstraint.getConstrainedOrgUnits(true);
		boolean editable = !readOnly && (ousThatCanBeEdited == null || ousThatCanBeEdited.contains(ou.getUuid()));

		List<RoleAssignedToOrgUnitDTO> userRoleAssignments = orgUnitService.getAllUserRolesAssignedToOrgUnit(ou);
		List<RoleAssignedToOrgUnitDTO> roleGroupAssignments = orgUnitService.getAllRoleGroupsAssignedToOrgUnit(ou);
		
		List<RoleAssignedToOrgUnitDTO> assignments = new ArrayList<>();
		assignments.addAll(userRoleAssignments);
		assignments.addAll(roleGroupAssignments);
		
		Set<Long> seenRoleGroups = new HashSet<>();
		List<RoleAssignedToOrgUnitDTO> extraJfrs = new ArrayList<>();
		for (RoleAssignedToOrgUnitDTO assignment : assignments) {
			boolean directlyAssignedRole = ((assignment.getAssignedThrough() == AssignedThrough.DIRECT) || (assignment.getAssignedThrough() == AssignedThrough.TITLE));

			if (assignment.getType() == RoleAssignmentType.USERROLE) {
				boolean internalRole = assignment.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER);

				// We allow editing of internal roles when user is an Administrator (which also allows editing other roles)
				// or if user can edit and role is "directly" assigned
				if ((internalRole && SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR) && directlyAssignedRole) || (editable && directlyAssignedRole)) {
					assignment.setCanEdit(true);
				}
			}
			else if (assignment.getType() == RoleAssignmentType.ROLEGROUP) {
				if (editable && directlyAssignedRole) {
					assignment.setCanEdit(true);
				}
				
				// no reason to expand the same RoleGroup multiple times
				if (seenRoleGroups.contains(assignment.getRoleId())) {
					continue;
				}
				
				seenRoleGroups.add(assignment.getRoleId());
				
				// expand userRoles within the RoleGroup
				RoleGroup roleGroup = roleGroupService.getById(assignment.getRoleId());
				if (roleGroup != null && roleGroup.getUserRoleAssignments() != null && roleGroup.getUserRoleAssignments().size() > 0) {
					for (RoleGroupUserRoleAssignment userRoleAssignment : roleGroup.getUserRoleAssignments()) {
						extraJfrs.add(RoleAssignedToOrgUnitDTO.fromRoleGroupUserRoleAssignment(userRoleAssignment, assignment));
					}
				}
			}
		}

		assignments.addAll(extraJfrs);
		
		model.addAttribute("assignments", assignments);
		model.addAttribute("editable", editable);

		return "ous/fragments/manage_roles :: ouAssignedRoles";
	}

	@RequireAssignerRole
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

	@RequireAssignerRole
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

	@RequireAssignerRole
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
	
	@RequireAssignerRole
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

	@RequireAssignerRole
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

	@RequireAssignerRole
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

		boolean readOnly = !(SecurityUtil.hasRole(Constants.ROLE_ASSIGNER) || SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR));
		List<String> ousThatCanBeEdited = assignerRoleConstraint.getConstrainedOrgUnits(true);

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
		List<UserRole> userRoles = assignerRoleConstraint.filterUserRolesUserCanAssign(userRoleService.getAll());

		List<RoleAssignedToOrgUnitDTO> assignments = orgUnitService.getAllUserRolesAssignedToOrgUnit(orgUnit);
		
		// filter out RC internal roles
		if (!SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)) {
			userRoles = userRoles.stream().filter(role -> !role.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)).collect(Collectors.toList());
		}

		for (UserRole role : userRoles) {
			if (role.isUserOnly()) {
				continue;
			}
			
			// filter out roles that allows postponing
			if (role.isAllowPostponing()) {
				continue;
			}
			
			AvailableUserRoleDTO availableUserRole = new AvailableUserRoleDTO();
			availableUserRole.setId(role.getId());
			availableUserRole.setName(role.getName());
			availableUserRole.setDescription(role.getDescription());
			availableUserRole.setItSystem(role.getItSystem());
			availableUserRole.setAlreadyAssigned(assignments.stream().filter(a -> a.getType() == RoleAssignmentType.USERROLE).anyMatch(a -> a.getRoleId() == role.getId()));

			addRoles.add(availableUserRole);
		}

		return addRoles;
	}
	
	public List<AvailableRoleGroupDTO> getAvailableRoleGroups(OrgUnit orgUnit) {
		List<AvailableRoleGroupDTO> addRoleGroups = new ArrayList<>();
		List<RoleGroup> roleGroups = roleGroupService.getAll();
		roleGroups = assignerRoleConstraint.filterRoleGroupsUserCanAssign(roleGroups);

		List<RoleAssignedToOrgUnitDTO> assignments = orgUnitService.getAllRoleGroupsAssignedToOrgUnit(orgUnit);
		
		for (RoleGroup roleGroup : roleGroups) {
			AvailableRoleGroupDTO rgr = new AvailableRoleGroupDTO();
			rgr.setId(roleGroup.getId());
			rgr.setName(roleGroup.getName());
			rgr.setDescription(roleGroup.getDescription());
			rgr.setAlreadyAssigned(assignments.stream().filter(a -> a.getType() == RoleAssignmentType.ROLEGROUP).anyMatch(a -> a.getRoleId() == roleGroup.getId()));

			addRoleGroups.add(rgr);
		}

		return addRoleGroups;
	}
}
