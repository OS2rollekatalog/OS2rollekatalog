package dk.digitalidentity.rc.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fjerner alle referencer til en UserRole før sletning.
 * <p>
 * Uden denne oprydning afviser Hibernate at flushe en managed
 * {@code UserUserRoleAssignment}/{@code OrgUnitUserRoleAssignment}/
 * {@code RoleGroupUserRoleAssignment} der peger på en netop fjernet
 * UserRole, selv om tilsvarende FK'er i databasen har ON DELETE CASCADE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRoleCleanupService {

	private final RoleGroupService roleGroupService;
	private final UserService userService;
	private final OrgUnitService orgUnitService;
	private final AssignmentService assignmentService;
	private final UserRoleService userRoleService;

	@Transactional
	public void deleteWithCleanup(UserRole userRole) {
		List<RoleGroup> roleGroups = roleGroupService.getByUserRole(userRole);
		Set<User> directUsers = assignmentService.getUsersWithUserRoleDirectlyAssigned(userRole);
		List<OrgUnit> orgUnits = orgUnitService.getAllWithRoleIncludingInactive(userRole);

		if (!roleGroups.isEmpty() || !directUsers.isEmpty() || !orgUnits.isEmpty()) {
			log.info("Cleaning up userRole {} before delete: {} role groups, {} direct users, {} org units",
					userRole.getId(), roleGroups.size(), directUsers.size(), orgUnits.size());
		}

		for (RoleGroup roleGroup : roleGroups) {
			roleGroupService.removeUserRole(roleGroup, userRole);
		}

		for (User user : directUsers) {
			userService.removeUserRole(user, userRole);
		}

		for (OrgUnit orgUnit : orgUnits) {
			orgUnitService.removeUserRole(orgUnit, userRole);
		}

		userRoleService.delete(userRole);
	}
}
