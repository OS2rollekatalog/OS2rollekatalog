package dk.digitalidentity.rc.rolerequest.controller.mvc;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.rolerequest.controller.mvc.enums.RoleType;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.RoleGroupAssignedToUser;
import dk.digitalidentity.rc.service.model.UserRoleAssignedToUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Controller
public class RequestRoleListController {

	@Autowired
	private RoleGroupService roleGroupService;

	@Autowired
	private SettingsService settingsService;

	@Autowired
	private UserService userService;

	record RoleGroupUserRole(String itSystemName, String name, String description) {}
	@GetMapping(value = "/ui/request/rolegroups/{roleGroupId}/userroles")
	public String userRoles(final Model model, final @PathVariable("roleGroupId") Long roleGroupId) {
		if (!settingsService.isRequestApproveEnabled()) {
			return "redirect:/error";
		}

		List<RoleGroupUserRole> userRoles = new ArrayList<>();
		RoleGroup roleGroup = roleGroupService.getById(roleGroupId);
		if (roleGroup != null) {
			for (RoleGroupUserRoleAssignment userRoleAssignment : roleGroup.getUserRoleAssignments()) {
				userRoles.add(new RoleGroupUserRole(userRoleAssignment.getUserRole().getItSystem().getName(), userRoleAssignment.getUserRole().getName(), userRoleAssignment.getUserRole().getDescription()));
			}
		}
		model.addAttribute("userRoles", userRoles);
		return "requestmodule/fragments/rolegroup_userrole_list :: itSystemUserRoleTable";
	}

	record RoleForUser(long id, long assignmentId, RoleType type, String itSystemName, String name, String description) {}
	@GetMapping(value = "/ui/request/users/{uuid}/roles")
	public String userRoles(final Model model, final @PathVariable("uuid") String uuid) {
		if (!settingsService.isRequestApproveEnabled()) {
			return "redirect:/error";
		}

		User user = userService.getOptionalByUuid(uuid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User with uuid " + uuid + " not found"));
		List<RoleForUser> userRoles = new ArrayList<>();
		List<RoleForUser> roleGroups = new ArrayList<>();

		for (RoleGroupAssignedToUser roleGroupAssignment : userService.getAllRoleGroupsAssignedToUser(user)) {
			roleGroups.add(new RoleForUser(roleGroupAssignment.getRoleGroup().getId(), roleGroupAssignment.getAssignmentId(), RoleType.ROLE_GROUP, "", roleGroupAssignment.getRoleGroup().getName(), roleGroupAssignment.getRoleGroup().getDescription()));
		}

		for (UserRoleAssignedToUser userRoleAssignment : userService.getAllUserRolesAssignedToUserExemptingRoleGroups(user, null)) {
			userRoles.add(new RoleForUser(userRoleAssignment.getUserRole().getId(), userRoleAssignment.getAssignmentId(), RoleType.USER_ROLE, userRoleAssignment.getUserRole().getItSystem().getName(), userRoleAssignment.getUserRole().getName(), userRoleAssignment.getUserRole().getDescription()));
		}

		//get setting for reason requirement
		model.addAttribute("reasonRequirement", settingsService.getRolerequestReason().toString());

		model.addAttribute("userRoles", userRoles);
		model.addAttribute("roleGroups", roleGroups);
		model.addAttribute("userUuid", uuid);
		return "requestmodule/wizard/fragments/roles_for_user_list :: roles";
	}
}
