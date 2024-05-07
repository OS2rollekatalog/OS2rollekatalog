package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AvailableRoleGroupDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AvailableUserRoleDTO;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.RoleAssignedToUserDTO;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class UserControllerHelper {

    @Value("#{servletContext.contextPath}")
    private String servletContextPath;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;
	
	@Autowired
	private AccessConstraintService assignerRoleConstraint;

	@Autowired
	private UserService userService;

	public List<AvailableUserRoleDTO> getAvailableUserRoles(User user) {
		List<AvailableUserRoleDTO> addRoles = new ArrayList<>();
		List<UserRole> userRoles = userRoleService.getAll();
		userRoles = assignerRoleConstraint.filterUserRolesUserCanAssign(userRoles);
		List<RoleAssignedToUserDTO> assignments = userService.getAllUserRoleAndRoleGroupAssignments(user);

		//filter out RC internal roles
		if (!SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)) {
			userRoles = userRoles.stream().filter(role -> !isRoleCatalogueRole(role)).collect(Collectors.toList());
		}

		for (UserRole role : userRoles) {

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

	public List<AvailableRoleGroupDTO> getAvailableRoleGroups(User user) {
		List<AvailableRoleGroupDTO> addRoleGroups = new ArrayList<>();
		List<RoleGroup> roleGroups = roleGroupService.getAll();
		roleGroups = assignerRoleConstraint.filterRoleGroupsUserCanAssign(roleGroups);
		List<RoleAssignedToUserDTO> assignments = userService.getAllUserRoleAndRoleGroupAssignments(user);

		//filter out groups containing RC roles
		if (!SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)) {
			roleGroups = roleGroups.stream()
					.filter(rg -> rg.getUserRoleAssignments().stream()
                            .noneMatch(a -> isRoleCatalogueRole(a.getUserRole())))
					.collect(Collectors.toList());
		}

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

	private static boolean isRoleCatalogueRole(final UserRole userRole) {
		return userRole.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER);
	}
}
