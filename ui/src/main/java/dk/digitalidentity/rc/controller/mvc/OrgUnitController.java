package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.viewmodel.Assignment;
import dk.digitalidentity.rc.controller.mvc.viewmodel.EditRolegroupRow;
import dk.digitalidentity.rc.controller.mvc.viewmodel.EditUserRoleRow;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.TitleListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserListDTO;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.TitleRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.TitleUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.RequireAssignerOrKleAdminRole;
import dk.digitalidentity.rc.security.RequireReadAccessOrManagerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;

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
				.collect(Collectors.toList());

		model.addAttribute("allOUs", allOUs);

		return "ous/list";
	}

	@RequestMapping(value = "/ui/ous/view/{uuid}", method = RequestMethod.GET)
	public String view(Model model, @PathVariable("uuid") String uuid, @RequestParam(required = false, defaultValue = "tree", value = "backRef") String backRef) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}

		boolean readOnly = !(SecurityUtil.hasRole(Constants.ROLE_ASSIGNER) || SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR));
		List<String> ousThatCanBeEdited = assignerRoleConstraint.getConstrainedOrgUnits(true);

		List<UserListDTO> userDTOs = getUserDTOs(ou);

		model.addAttribute("editable", !readOnly && (ousThatCanBeEdited == null || ousThatCanBeEdited.contains(ou.getUuid())));
		model.addAttribute("ou", ou);
		model.addAttribute("backRef", backRef);
		model.addAttribute("roles", orgUnitService.getUserRoles(ou, true));
		model.addAttribute("rolegroups", orgUnitService.getRoleGroups(ou, true));
		model.addAttribute("klePerforming", kleService.getKleAssignments(ou, KleType.PERFORMING, true));
		model.addAttribute("kleInterest", kleService.getKleAssignments(ou, KleType.INTEREST, true));
		model.addAttribute("kleUiEnabled", configuration.getIntegrations().getKle().isUiEnabled());
		model.addAttribute("users", userDTOs);

		List<OrgUnit> parentsKleIsInheritedFrom = new ArrayList<>();
		getParentsKleIsInheritedFrom(parentsKleIsInheritedFrom, ou.getParent());

		model.addAttribute("parentsKleIsInheritedFrom", parentsKleIsInheritedFrom);
		
		return "ous/view";
	}

	@RequireAssignerOrKleAdminRole
	@RequestMapping(value = "/ui/ous/edit/{uuid}", method = RequestMethod.GET)
	public String edit(Model model, @PathVariable("uuid") String uuid, @RequestParam(required = false, defaultValue = "tree", value = "backRef") String backRef) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}
		
		boolean titlesEnabled = configuration.getTitles().isEnabled();

		List<OrgUnit> parentsKleIsInheritedFrom = new ArrayList<>();
		getParentsKleIsInheritedFrom(parentsKleIsInheritedFrom, ou.getParent());

		boolean onlyKleAdmin = SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR) && !SecurityUtil.hasRole(Constants.ROLE_ASSIGNER);
		
		List<UserListDTO> userDTOs = getUserDTOs(ou);

		List<String> klePerformingCodes = ou.getKles().stream()
					.filter(k -> k.getAssignmentType().equals(KleType.PERFORMING))
					.map(k -> k.getCode())
					.collect(Collectors.toList());
		
		List<String> kleInterestCodes = ou.getKles().stream()
				.filter(k -> k.getAssignmentType().equals(KleType.INTEREST))
				.map(k -> k.getCode())
				.collect(Collectors.toList());

		List<KleDTO> kleInterestDTOS = new ArrayList<>();
		List<KleDTO> klePerformingDTOS = new ArrayList<>();

		List<Kle> kles = kleService.findAll();
		
		kles = kles.stream()
				.sorted((a, b) -> a.getCode().compareTo(b.getCode()))
				.collect(Collectors.toList());
		
		for (Kle kle : kles) {
			KleDTO klePerformingDTO = new KleDTO();
			klePerformingDTO.setId(kle.getCode());
			klePerformingDTO.setParent(kle.getParent().equals("0") ? "#" : kle.getParent());
			klePerformingDTO.setText(kle.isActive() ? kle.getCode() + " " + kle.getName() : kle.getCode() + " " + kle.getName() + " [UDGÅET]");
			
			if (klePerformingCodes.contains(kle.getCode())) {
				klePerformingDTO.getState().setChecked(true);
			}
			
			KleDTO kleInterestDTO = new KleDTO(klePerformingDTO);
			if (kleInterestCodes.contains(kle.getCode())) {
				kleInterestDTO.getState().setChecked(true);
			}
			
			kleInterestDTOS.add(kleInterestDTO);
			klePerformingDTOS.add(klePerformingDTO);
		}
		
		List<OrgUnitLevel> allowedLevels = orgUnitService.getAllowedLevels(ou);
		
		if (titlesEnabled) {
			List<Title> titles = orgUnitService.getTitles(ou);

			List<TitleListForm> titleForms = titles
					.stream()
					.map(title -> new TitleListForm(title))
					.collect(Collectors.toList());
			
			model.addAttribute("titles", titleForms);
			model.addAttribute("addRoles", getAddRolesTitles(ou, titles));
			model.addAttribute("addRoleGroups", getAddRoleGroupsTitles(ou, titles));
		}
		else {
			model.addAttribute("titles", null);
			model.addAttribute("addRoles", getAddRoles(ou));
			model.addAttribute("addRoleGroups", getAddRoleGroups(ou));
		}

		model.addAttribute("ou", ou);
		model.addAttribute("allowedLevels", allowedLevels);
		model.addAttribute("parentsKleIsInheritedFrom", parentsKleIsInheritedFrom);
		model.addAttribute("backRef", backRef);
		model.addAttribute("users", userDTOs);
		model.addAttribute("allInterestKles", kleInterestDTOS);
		model.addAttribute("allPerformingKles", klePerformingDTOS);
		model.addAttribute("kleUiEnabled", configuration.getIntegrations().getKle().isUiEnabled());
		model.addAttribute("onlyKleAdmin", onlyKleAdmin);
		model.addAttribute("titlesEnabled", titlesEnabled);

		return "ous/edit";
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

	private List<EditUserRoleRow> getAddRoles(OrgUnit ou) {
		List<EditUserRoleRow> addRoles = new ArrayList<>();
		List<UserRole> userRoles = assignerRoleConstraint.filterUserRolesUserCanAssign(userRoleService.getAll());
		List<OrgUnitUserRoleAssignment> directlyAssignedUserRoles = orgUnitService.getRoleMappings(ou);

		for (UserRole role : userRoles) {
			if (role.isUserOnly()) {
				continue;
			}
			
			EditUserRoleRow roleWithAssignment = new EditUserRoleRow();
			roleWithAssignment.setRole(role);
			roleWithAssignment.setAssignment(new Assignment());

			for (OrgUnitUserRoleAssignment roleMapping : directlyAssignedUserRoles) {
				if (roleMapping.getUserRole().getId() == role.getId()) {
					roleWithAssignment.setChecked(true);
					roleWithAssignment.setCheckedWithInherit(roleMapping.isInherit());
					roleWithAssignment.setAssignmentType(roleMapping.isInherit() ? -2 : -1);
					roleWithAssignment.getAssignment().setStartDate(roleMapping.getStartDate());
					roleWithAssignment.getAssignment().setStopDate(roleMapping.getStopDate());
				}
			}

			addRoles.add(roleWithAssignment);
		}

		return addRoles;
	}
	
	private List<EditUserRoleRow> getAddRolesTitles(OrgUnit ou, List<Title> titles) {
		List<EditUserRoleRow> addRoles = new ArrayList<>();
		List<UserRole> userRoles = assignerRoleConstraint.filterUserRolesUserCanAssign(userRoleService.getAll());
		List<OrgUnitUserRoleAssignment> directlyAssignedUserRoles = orgUnitService.getRoleMappings(ou);

		for (UserRole role : userRoles) {
			if (role.isUserOnly()) {
				continue;
			}
			
			EditUserRoleRow roleWithAssignment = new EditUserRoleRow();
			roleWithAssignment.setRole(role);
			roleWithAssignment.setAssignment(new Assignment());
			
			for (OrgUnitUserRoleAssignment roleMapping : directlyAssignedUserRoles) {
				if (roleMapping.getUserRole().getId() == role.getId()) {
					roleWithAssignment.setChecked(true);
					roleWithAssignment.setCheckedWithInherit(roleMapping.isInherit());
					roleWithAssignment.setAssignmentType(roleMapping.isInherit() ? -2 : -1);
					roleWithAssignment.getAssignment().setStartDate(roleMapping.getStartDate());
					roleWithAssignment.getAssignment().setStopDate(roleMapping.getStopDate());
					break;
				}
			}

			// check for titles then
			if (!roleWithAssignment.isChecked()) {
				int counter = 0;
				for (Title title : titles) {
					for (TitleUserRoleAssignment tura : title.getUserRoleAssignments()) {
						if (tura.getUserRole().getId() == role.getId() && tura.getOuUuids().contains(ou.getUuid())) {
							roleWithAssignment.setChecked(true);
							roleWithAssignment.setOuAssignment(false);
							roleWithAssignment.setAssignmentType(++counter);

							// they are identical for each assignment, but we need at least one of them
							roleWithAssignment.getAssignment().setStartDate(tura.getStartDate());
							roleWithAssignment.getAssignment().setStopDate(tura.getStopDate());
						}
					}
				}
			}

			addRoles.add(roleWithAssignment);
		}

		return addRoles;
	}
	
	private List<EditRolegroupRow> getAddRoleGroups(OrgUnit ou) {
		List<EditRolegroupRow> addRoleGroups = new ArrayList<>();
		List<RoleGroup> roleGroups = assignerRoleConstraint.filterRoleGroupsUserCanAssign(roleGroupService.getAll());
		List<OrgUnitRoleGroupAssignment> directlyAssignedRoleGroups = orgUnitService.getRoleGroupMappings(ou);

		for (RoleGroup roleGroup : roleGroups) {
			if (roleGroup.isUserOnly()) {
				continue;
			}

			EditRolegroupRow rgwa = new EditRolegroupRow();
			rgwa.setRoleGroup(roleGroup);
			rgwa.setAssignment(new Assignment());
			
			for (OrgUnitRoleGroupAssignment roleMapping : directlyAssignedRoleGroups) {
				if (roleMapping.getRoleGroup().getId() == roleGroup.getId()) {
					rgwa.setChecked(true);
					rgwa.setCheckedWithInherit(roleMapping.isInherit());
					rgwa.getAssignment().setStartDate(roleMapping.getStartDate());
					rgwa.getAssignment().setStopDate(roleMapping.getStopDate());
					break;
				}
			}

			addRoleGroups.add(rgwa);
		}

		return addRoleGroups;
	}
	
	private List<EditRolegroupRow> getAddRoleGroupsTitles(OrgUnit ou, List<Title> titles) {
		List<EditRolegroupRow> addRoleGroups = new ArrayList<>();
		List<RoleGroup> roleGroups = assignerRoleConstraint.filterRoleGroupsUserCanAssign(roleGroupService.getAll());
		List<OrgUnitRoleGroupAssignment> directlyAssignedRoleGroups = orgUnitService.getRoleGroupMappings(ou);

		for (RoleGroup roleGroup : roleGroups) {
			if (roleGroup.isUserOnly()) {
				continue;
			}

			EditRolegroupRow rgwa = new EditRolegroupRow();
			rgwa.setRoleGroup(roleGroup);
			rgwa.setAssignment(new Assignment());
			
			for (OrgUnitRoleGroupAssignment roleMapping : directlyAssignedRoleGroups) {
				if (roleMapping.getRoleGroup().getId() == roleGroup.getId()) {
					rgwa.setChecked(true);
					rgwa.setCheckedWithInherit(roleMapping.isInherit());
					rgwa.setAssignmentType(roleMapping.isInherit() ? -2 : -1);
					rgwa.getAssignment().setStartDate(roleMapping.getStartDate());
					rgwa.getAssignment().setStopDate(roleMapping.getStopDate());
					break;
				}
			}

			if (!rgwa.isChecked()) {
				int counter = 0;
				for (Title title : titles) {
					for (TitleRoleGroupAssignment trga : title.getRoleGroupAssignments()) {
						if (trga.getRoleGroup().getId() == roleGroup.getId() && trga.getOuUuids().contains(ou.getUuid())) {
							rgwa.setChecked(true);
							rgwa.setOuAssignment(false);
							rgwa.setAssignmentType(++counter);
							
							// yes, we only need the first one, and they are identical, but this is easier
							rgwa.getAssignment().setStartDate(trga.getStartDate());
							rgwa.getAssignment().setStopDate(trga.getStopDate());
						}
					}
				}
			}

			addRoleGroups.add(rgwa);
		}

		return addRoleGroups;
	}
}
