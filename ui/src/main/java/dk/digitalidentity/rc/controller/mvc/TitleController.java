package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.mvc.viewmodel.EditRolegroupRow;
import dk.digitalidentity.rc.controller.mvc.viewmodel.EditUserRoleRow;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.TitleRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.TitleUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.RequireAssignerRole;
import dk.digitalidentity.rc.security.RequireReadAccessOrManagerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.TitleService;
import dk.digitalidentity.rc.service.UserRoleService;

@RequireReadAccessOrManagerRole
@Controller
public class TitleController {

	@Autowired
	private TitleService titleService;
	
	@Autowired
	private AccessConstraintService assignerRoleConstraint;
	
	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;
	
	@Autowired
	private OrgUnitService orgUnitService;

	@GetMapping("/ui/titles/list")
	public String list(Model model) {
		model.addAttribute("titles", titleService.getAll());

		return "titles/list";
	}
	
	@GetMapping("/ui/titles/view/{uuid}")
	public String view(Model model, @PathVariable("uuid") String uuid) {
		Title title = titleService.getByUuid(uuid);
		if (title == null) {
			return "redirect:/ui/titles/list";
		}

		model.addAttribute("editable", SecurityUtil.hasRole(Constants.ROLE_ASSIGNER));
		model.addAttribute("title", title);
		model.addAttribute("roles", title.getUserRoleAssignments());
		model.addAttribute("rolegroups", title.getRoleGroupAssignments());
		
		return "titles/view";
	}

	@RequireAssignerRole
	@GetMapping("/ui/titles/edit/{uuid}")
	public String edit(Model model, @PathVariable("uuid") String uuid) {
		Title title = titleService.getByUuid(uuid);
		if (title == null) {
			return "redirect:/ui/titles/list";
		}

		final List<String> list = assignerRoleConstraint.getConstrainedOrgUnits(true);
		List<OUListForm> allOUs = orgUnitService.getAllCached()
				.stream()
				.map(ou -> new OUListForm(ou, (list == null || list.contains(ou.getUuid()))))
				.collect(Collectors.toList());

		model.addAttribute("allOUs", allOUs);
		model.addAttribute("editable", SecurityUtil.hasRole(Constants.ROLE_ASSIGNER));
		model.addAttribute("title", title);
		model.addAttribute("addRoles", getAddRoles(title));
		model.addAttribute("addRoleGroups", getAddRoleGroups(title));
		
		return "titles/edit";
	}
	
	private List<EditUserRoleRow> getAddRoles(Title title) {
		List<EditUserRoleRow> addRoles = new ArrayList<>();
		List<UserRole> userRoles = assignerRoleConstraint.filterUserRolesUserCanAssign(userRoleService.getAll());
		List<TitleUserRoleAssignment> directlyAssignedUserRoles = title.getUserRoleAssignments();

		for (UserRole role : userRoles) {
			if (role.isUserOnly()) {
				continue;
			}
			
			EditUserRoleRow roleWithAssignment = new EditUserRoleRow();
			roleWithAssignment.setRole(role);
			for (TitleUserRoleAssignment roleMapping : directlyAssignedUserRoles) {
				if (roleMapping.getUserRole().getId() == role.getId()) {
					roleWithAssignment.setChecked(true);
					roleWithAssignment.setOuAssignments(roleMapping.getOuUuids().size());

					break;
				}
			}

			addRoles.add(roleWithAssignment);
		}

		return addRoles;
	}

	private List<EditRolegroupRow> getAddRoleGroups(Title title) {
		List<EditRolegroupRow> addRoleGroups = new ArrayList<>();
		List<RoleGroup> roleGroups = assignerRoleConstraint.filterRoleGroupsUserCanAssign(roleGroupService.getAll());
		List<TitleRoleGroupAssignment> directlyAssignedRoleGroups = title.getRoleGroupAssignments();

		for (RoleGroup roleGroup : roleGroups) {
			if (roleGroup.isUserOnly()) {
				continue;
			}

			EditRolegroupRow rgwa = new EditRolegroupRow();
			rgwa.setRoleGroup(roleGroup);
			
			for (TitleRoleGroupAssignment roleMapping : directlyAssignedRoleGroups) {
				if (roleMapping.getRoleGroup().getId() == roleGroup.getId()) {
					rgwa.setChecked(true);
					rgwa.setOuAssignments(roleMapping.getOuUuids().size());
					
					break;
				}
			}

			addRoleGroups.add(rgwa);
		}

		return addRoleGroups;
	}
}
