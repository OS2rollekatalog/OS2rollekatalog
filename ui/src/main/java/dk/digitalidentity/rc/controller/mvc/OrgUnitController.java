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
import dk.digitalidentity.rc.controller.mvc.viewmodel.EditRolegroupRow;
import dk.digitalidentity.rc.controller.mvc.viewmodel.EditUserRoleRow;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserListDTO;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
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

	@Value("${kle.ui.enabled:false}")
	private boolean kleUiEnabled;

    @Value("#{servletContext.contextPath}")
    private String servletContextPath;

    @Autowired
	private UserService userService;

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
		model.addAttribute("kleUiEnabled", kleUiEnabled);
		model.addAttribute("users", userDTOs);

		if (kleUiEnabled) {
			List<OrgUnit> parentsKleIsInheritedFrom = new ArrayList<>();
			getParentsKleIsInheritedFrom(parentsKleIsInheritedFrom, ou.getParent());

			model.addAttribute("parentsKleIsInheritedFrom", parentsKleIsInheritedFrom);
		}
		
		return "ous/view";
	}

	@RequireAssignerOrKleAdminRole
	@RequestMapping(value = "/ui/ous/edit/{uuid}", method = RequestMethod.GET)
	public String edit(Model model, @PathVariable("uuid") String uuid, @RequestParam(required = false, defaultValue = "tree", value = "backRef") String backRef) {
		OrgUnit ou = orgUnitService.getByUuid(uuid);
		if (ou == null) {
			return "redirect:../list";
		}

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

		model.addAttribute("ou", ou);
		model.addAttribute("allowedLevels", allowedLevels);
		model.addAttribute("parentsKleIsInheritedFrom", parentsKleIsInheritedFrom);
		model.addAttribute("backRef", backRef);
		model.addAttribute("addRoles", getAddRoles(ou));
		model.addAttribute("addRoleGroups", getAddRoleGroups(ou));
		model.addAttribute("users", userDTOs);
		model.addAttribute("allInterestKles", kleInterestDTOS);
		model.addAttribute("allPerformingKles", klePerformingDTOS);
		model.addAttribute("kleUiEnabled", kleUiEnabled);
		model.addAttribute("onlyKleAdmin", onlyKleAdmin);

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

			for (OrgUnitUserRoleAssignment roleMapping : directlyAssignedUserRoles) {
				if (roleMapping.getUserRole().getId() == role.getId()) {
					roleWithAssignment.setChecked(true);
					roleWithAssignment.setCheckedWithInherit(roleMapping.isInherit());
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
			
			for (OrgUnitRoleGroupAssignment roleMapping : directlyAssignedRoleGroups) {
				if (roleMapping.getRoleGroup().getId() == roleGroup.getId()) {
					rgwa.setChecked(true);
					rgwa.setCheckedWithInherit(roleMapping.isInherit());
				}
			}

			addRoleGroups.add(rgwa);
		}

		return addRoleGroups;
	}
}
