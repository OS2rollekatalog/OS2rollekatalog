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
		// Hver removeUserRole nedenfor udløser via RoleChangeHook en recalculation-besked pr. bruger.
		// For en UserRole med hundredvis af direkte brugere giver det lige så mange enkeltvise
		// INSERT IGNORE'er i simple_queue_items, der under samtidig last deadlocker mod kø-schedulerens
		// statusopdateringer. Vi samler dem til én bulk-enqueue, der først udsendes når oprydningen er
		// committet-klar (ved normal afslutning af det wrappede arbejde).
		userService.runWithBatchedRecalculation(() -> doDeleteWithCleanup(userRole));
	}

	private void doDeleteWithCleanup(UserRole detachedUserRole) {
		// Genhent rollen managed i DENNE transaktion. Kalderen (ItSystemApi.manageItSystem) henter rollerne i
		// en separat, allerede committet transaktion, så det indkomne objekt er detached. removeUserRole matcher
		// assignments via UserRole.equals() (Lombok @Data => alle felter), og en detached instans matcher ikke
		// pålideligt den managed UserRole som assignment'ene peger på i denne session. Resultatet var, at
		// removeUserRole intet fjernede, hvorefter delete() schedulerede rollen til sletning og flush fejlede med
		// TransientPropertyValueException, fordi UserUserRoleAssignment stadig refererede den slettede rolle.
		UserRole userRole = userRoleService.getById(detachedUserRole.getId());
		if (userRole == null) {
			// Allerede slettet (fx samtidig manage-import) — intet at gøre.
			return;
		}

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
