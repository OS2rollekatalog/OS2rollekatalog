package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.mvc.viewmodel.Assignment;
import dk.digitalidentity.rc.controller.mvc.viewmodel.AssignmentType;
import dk.digitalidentity.rc.controller.mvc.viewmodel.EditRolegroupRow;
import dk.digitalidentity.rc.controller.mvc.viewmodel.EditUserRoleRow;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.PositionRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.PositionUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.AltAccountType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.UserRoleAssignedToUser;

@Component
public class UserControllerHelper {

    @Value("#{servletContext.contextPath}")
    private String servletContextPath;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;
	
	@Autowired
	private UserService userService;

	@Autowired
	private AccessConstraintService assignerRoleConstraint;

	public List<EditUserRoleRow> getAddRoles(User user) {
		List<EditUserRoleRow> addRoles = new ArrayList<>();
		List<UserRole> userRoles = userRoleService.getAll();
		userRoles = assignerRoleConstraint.filterUserRolesUserCanAssign(userRoles);

		// if the user does not have a KSP/CICS account, filter out UserRoles from KSPCICS it-systems
		boolean kspCicsAccount = user.getAltAccounts().stream().anyMatch(a -> a.getAccountType().equals(AltAccountType.KSPCICS));
		if (!kspCicsAccount) {
			userRoles = userRoles.stream().filter(u -> !u.getItSystem().getSystemType().equals(ItSystemType.KSPCICS)).collect(Collectors.toList());
		}

		List<UserRoleAssignedToUser> allAssigned = userService.getAllUserRolesAssignedToUser(user, null);

		for (UserRole role : userRoles) {
			boolean checked = shouldBeChecked(user, role);
			
			// if not checked, we see if it was assigned indirectly, and then store that information for the GUI
			AssignedThrough assignedThrough = null;
			if (!checked) {
				for (UserRoleAssignedToUser assigned : allAssigned) {
					if (assigned.getUserRole().getId() == role.getId()) {
						assignedThrough = assigned.getAssignedThrough();
						
						break;
					}
				}
			}
			
			UserRole newRole = new UserRole();
			newRole.setDescription(role.getDescription());
			newRole.setId(role.getId());
			newRole.setIdentifier(role.getIdentifier());
			newRole.setItSystem(role.getItSystem());
			newRole.setName(role.getName());
			newRole.setSystemRoleAssignments(role.getSystemRoleAssignments());

			EditUserRoleRow editUserRoleRow = new EditUserRoleRow();
			editUserRoleRow.setRole(newRole);
			editUserRoleRow.setAssignedThrough(assignedThrough);
			editUserRoleRow.setChecked(checked);
			
			if (!SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR) && role.getItSystem().getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)) {
				editUserRoleRow.setCanCheck(false);
			}
			else {
				editUserRoleRow.setCanCheck(true);
			}

			editUserRoleRow.setAssignment(getAssignment(user, role));

			addRoles.add(editUserRoleRow);
		}

		return addRoles;
	}

	public List<EditRolegroupRow> getAddRoleGroups(User user) {
		List<EditRolegroupRow> addRoleGroups = new ArrayList<>();
		List<RoleGroup> roleGroups = roleGroupService.getAll();
		roleGroups = assignerRoleConstraint.filterRoleGroupsUserCanAssign(roleGroups);

		for (RoleGroup roleGroup : roleGroups) {
			boolean checked = shouldBeChecked(user, roleGroup);

			EditRolegroupRow rgr = new EditRolegroupRow();
			rgr.setRoleGroup(roleGroup);
			rgr.setAssignment(getAssignment(user, roleGroup));
			rgr.setChecked(checked);

			addRoleGroups.add(rgr);
		}

		return addRoleGroups;
	}

	private static Assignment getAssignment(User user, UserRole role) {
		Assignment assignment = new Assignment();

		Optional<UserUserRoleAssignment> userRoleAssignment = user.getUserRoleAssignments().stream().filter(ura -> ura.getUserRole().getId() == role.getId()).findAny();
		if (userRoleAssignment.isPresent()) {
			assignment.setAssignmentType(AssignmentType.DIRECTLY);
			assignment.setStartDate(userRoleAssignment.get().getStartDate());
			assignment.setStopDate(userRoleAssignment.get().getStopDate());

			return assignment;
		}

		for (Position p : user.getPositions()) {
			Optional<PositionUserRoleAssignment> uraPosition = p.getUserRoleAssignments().stream().filter(ura -> ura.getUserRole().getId() == role.getId()).findAny();
			if (uraPosition.isPresent()) {
				assignment.setAssignmentType(AssignmentType.POSITION);
				assignment.setPosition(p.getName());
				assignment.setStartDate(uraPosition.get().getStartDate());
				assignment.setStopDate(uraPosition.get().getStopDate());

				return assignment;
			}
		}

		assignment.setAssignmentType(AssignmentType.NONE);

		return assignment;
	}

	private static Assignment getAssignment(User user, RoleGroup rolegroup) {
		Assignment assignment = new Assignment();

		Optional<UserRoleGroupAssignment> roleGroupAssignment = user.getRoleGroupAssignments().stream().filter(rga -> rga.getRoleGroup().getId() == rolegroup.getId()).findAny();
		if (roleGroupAssignment.isPresent()) {
			assignment.setAssignmentType(AssignmentType.DIRECTLY);
			assignment.setStartDate(roleGroupAssignment.get().getStartDate());
			assignment.setStopDate(roleGroupAssignment.get().getStopDate());

			return assignment;
		}

		for (Position p : user.getPositions()) {
			Optional<PositionRoleGroupAssignment> rgaPosition = p.getRoleGroupAssignments().stream().filter(rga -> rga.getRoleGroup().getId() == rolegroup.getId()).findAny();
			if (rgaPosition.isPresent()) {
				assignment.setAssignmentType(AssignmentType.POSITION);
				assignment.setPosition(p.getName());
				assignment.setStartDate(rgaPosition.get().getStartDate());
				assignment.setStopDate(rgaPosition.get().getStopDate());

				return assignment;
			}
		}

		assignment.setAssignmentType(AssignmentType.NONE);

		return assignment;
	}

	private static boolean shouldBeChecked(User user, UserRole role) {
		if (user.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(role)) {
			return true;
		}

		for (Position p : user.getPositions()) {
	      	if (p.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(role)) {
				return true;
			}
		}

		return false;
	}

	private static boolean shouldBeChecked(User user, RoleGroup rolegroup) {
		if (user.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(rolegroup)) {
			return true;
		}

		for (Position p : user.getPositions()) {
			if (p.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList()).contains(rolegroup)) {
				return true;
			}
		}
		
		return false;
	}
}
