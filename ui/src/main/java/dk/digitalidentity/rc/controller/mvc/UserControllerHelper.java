package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AvailableRoleGroupDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AvailableUserRoleDTO;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.AltAccountType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;

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

	public List<AvailableUserRoleDTO> getAvailableUserRoles(User user) {
		List<AvailableUserRoleDTO> addRoles = new ArrayList<>();
		List<UserRole> userRoles = userRoleService.getAll();
		userRoles = assignerRoleConstraint.filterUserRolesUserCanAssign(userRoles);

		// if the user does not have a KSP/CICS account, filter out UserRoles from KSPCICS it-systems
		boolean kspCicsAccount = user.getAltAccounts().stream().anyMatch(a -> a.getAccountType().equals(AltAccountType.KSPCICS));
		if (!kspCicsAccount) {
			userRoles = userRoles.stream().filter(u -> !u.getItSystem().getSystemType().equals(ItSystemType.KSPCICS)).collect(Collectors.toList());
		}
		
		//filter out RC internal roles
		if (!SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)) {
			userRoles = userRoles.stream().filter(role -> !role.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)).collect(Collectors.toList());
		}


		for (UserRole role : userRoles) {

			AvailableUserRoleDTO availableUserRole = new AvailableUserRoleDTO();
			availableUserRole.setId(role.getId());
			availableUserRole.setName(role.getName());
			availableUserRole.setDescription(role.getDescription());
			availableUserRole.setItSystem(role.getItSystem());

			addRoles.add(availableUserRole);
		}

		return addRoles;
	}

	public List<AvailableRoleGroupDTO> getAvailableRoleGroups(User user) {
		List<AvailableRoleGroupDTO> addRoleGroups = new ArrayList<>();
		List<RoleGroup> roleGroups = roleGroupService.getAll();
		roleGroups = assignerRoleConstraint.filterRoleGroupsUserCanAssign(roleGroups);

		for (RoleGroup roleGroup : roleGroups) {
			AvailableRoleGroupDTO rgr = new AvailableRoleGroupDTO();
			rgr.setId(roleGroup.getId());
			rgr.setName(roleGroup.getName());
			rgr.setDescription(roleGroup.getDescription());

			addRoleGroups.add(rgr);
		}

		return addRoleGroups;
	}
}
