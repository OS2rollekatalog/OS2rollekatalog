package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AvailableRoleGroupDTO;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.RoleGroupService;

@Component
public class UserControllerHelper {

    @Value("#{servletContext.contextPath}")
    private String servletContextPath;

	@Autowired
	private RoleGroupService roleGroupService;

	@Autowired
	private AccessConstraintService assignerRoleConstraint;

	@Autowired
	private AssignmentService assignmentService;
	@Autowired
	private UserPermissionContext userPermissionContext;

	public List<AvailableRoleGroupDTO> getAvailableRoleGroups(User user) {
		List<AvailableRoleGroupDTO> addRoleGroups = new ArrayList<>();
		List<RoleGroup> roleGroups = roleGroupService.getAll();
		roleGroups = assignerRoleConstraint.filterRoleGroupsUserCanAssign(roleGroups);
		Set<CurrentAssignment> assignments = assignmentService.getByUserIncludingInactive(user);

		PermissionConstraint assignConstraint = userPermissionContext.getConstraint(Section.USER, Permission.ASSIGN);

		//filter out groups containing RC roles
		if (!SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR)) {
			roleGroups = roleGroups.stream()
					.filter(rg -> rg.getUserRoleAssignments().stream()
                            .noneMatch(a -> isRoleCatalogueRole(a.getUserRole())))
					.collect(Collectors.toList());
		}

		for (RoleGroup roleGroup : roleGroups) {
			Set<Long> containedRoleItsystemIds = roleGroup.getUserRoleAssignments().stream()
				.map(ura -> ura.getUserRole().getItSystem().getId())
				.collect(Collectors.toSet());
			AvailableRoleGroupDTO rgr = new AvailableRoleGroupDTO();
			rgr.setId(roleGroup.getId());
			rgr.setName(roleGroup.getName());
			rgr.setDescription(roleGroup.getDescription());
			rgr.setAlreadyAssigned(assignments.stream().filter(a -> a.getRoleGroup() != null).anyMatch(a -> a.getRoleGroup().getId() == roleGroup.getId()));
			rgr.setAssignable(assignConstraint.allowsAllITSystems(containedRoleItsystemIds));

			addRoleGroups.add(rgr);
		}

		return addRoleGroups;
	}

	private static boolean isRoleCatalogueRole(final UserRole userRole) {
		return userRole.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER);
	}
}
