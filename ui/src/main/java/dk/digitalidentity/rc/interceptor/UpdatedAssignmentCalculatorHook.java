package dk.digitalidentity.rc.interceptor;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.assignment.HistoricItSystemAssignmentService;
import dk.digitalidentity.rc.service.assignment.HistoricOuAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class UpdatedAssignmentCalculatorHook implements RoleChangeHook {
	private final UserService userService;
	private final OrgUnitService orgUnitService;
	private final AssignmentService assignmentService;
	private final HistoricOuAssignmentService historicOuAssignmentService;
	private final HistoricItSystemAssignmentService historicItSystemAssignmentService;


	/**
	 * @param user user relevant for the changes in assignment
	 */
	@Override
	public void interceptActivateUser(User user) {
		addUserUuidToAssignmentCalculatorQueue(user.getUuid());
	}

	/**
	 * @param user user user relevant for the changes in assignment
	 */
	@Override
	public void interceptFlagUserDeleted(User user) {
		addUserUuidToAssignmentCalculatorQueue(user.getUuid());
	}

	/**
	 * @param user      user user relevant for the changes in assignment
	 * @param roleGroup not relevant
	 */
	@Override
	public void interceptAddRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		// this hook uses the Immediate version (only) instead
	}

	@Override
	public void interceptAddImmediateRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		addUserUuidToAssignmentCalculatorQueue(user.getUuid());
	}

	/**
	 * @param user      user user relevant for the changes in assignment
	 * @param roleGroup not relevant
	 */
	@Override
	public void interceptRemoveRoleGroupAssignmentOnUser(User user, RoleGroup roleGroup) {
		addUserUuidToAssignmentCalculatorQueue(user.getUuid());
	}

	/**
	 * @param user       user user relevant for the changes in assignment
	 * @param assignment not relevant
	 */
	@Override
	public void interceptEditRoleGroupAssignmentOnUser(User user, UserRoleGroupAssignment assignment) {
		addUserUuidToAssignmentCalculatorQueue(user.getUuid());
	}

	/**
	 * @param user     user user relevant for the changes in assignment
	 * @param userRole not relevant
	 */
	@Override
	public void interceptAddUserRoleAssignmentOnUser(User user, UserRole userRole) {
		// this hook uses the Immediate version (only) instead
	}

	@Override
	public void interceptAddImmediateUserRoleAssignmentOnUser(User user, UserRole userRole) {
		addUserUuidToAssignmentCalculatorQueue(user.getUuid());
	}

	/**
	 * @param user       user user relevant for the changes in assignment
	 * @param assignment not relevant
	 */
	@Override
	public void interceptEditUserRoleAssignmentOnUser(User user, UserUserRoleAssignment assignment) {
		addUserUuidToAssignmentCalculatorQueue(user.getUuid());
	}

	/**
	 * @param user     user user relevant for the changes in assignment
	 * @param userRole not relevant
	 */
	@Override
	public void interceptRemoveUserRoleAssignmentOnUser(User user, UserRole userRole) {
		addUserUuidToAssignmentCalculatorQueue(user.getUuid());
	}

	/**
	 * @param user     user user relevant for the changes in assignment
	 * @param position not relevant
	 */
	@Override
	public void interceptAddPositionOnUser(User user, Position position) {
		addUserUuidToAssignmentCalculatorQueue(user.getUuid());
	}

	/**
	 * @param user     user user relevant for the changes in assignment
	 * @param position not relevant
	 */
	@Override
	public void interceptRemovePositionOnUser(User user, Position position) {
		addUserUuidToAssignmentCalculatorQueue(user.getUuid());
	}

	// --- Post-add hooks (OU) ---

	/** Fired @AfterReturning OrgUnitService.addUserRole — the saved assignment is fully populated. */
	@Override
	public void interceptAddedUserRoleAssignmentOnOrgUnit(OrgUnit ou, OrgUnitUserRoleAssignment assignment) {
		historicOuAssignmentService.recordUserRoleAdded(ou, assignment);
	}

	/** Fired @AfterReturning OrgUnitService.addRoleGroup — the saved assignment is fully populated. */
	@Override
	public void interceptAddedRoleGroupAssignmentOnOrgUnit(OrgUnit ou, OrgUnitRoleGroupAssignment assignment) {
		historicOuAssignmentService.recordRoleGroupAdded(ou, assignment);
	}

	/**
	 * @param ou        OrgUnit that has changed
	 * @param roleGroup not relevant
	 * @param inherit   not relevant
	 */
	@Override
	public void interceptAddRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, boolean inherit) {
		// this hook uses the Immediate version (only) instead
	}

	@Override
	public void interceptAddImmediateRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup, boolean inherit) {
		Set<String> userUuids = inherit
			? orgUnitService.findUserUuidsForOu(ou, true)
			: orgUnitService.findUserUuidsForOu(ou, false);

		addMultipleUsersToAssignmentCalculatorQueue(userUuids);
	}

	/**
	 * Fired @Before OrgUnitService.removeRoleGroup — ou.getRoleGroupAssignments() still contains the
	 * assignment being removed. Use forEach since the same role group may be assigned multiple times
	 * on the same OU with different constraints.
	 */
	@Override
	public void interceptRemoveRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup) {
		ou.getRoleGroupAssignments().stream()
			.filter(a -> a.getRoleGroup().getId() == roleGroup.getId())
			.forEach(a -> historicOuAssignmentService.recordRoleGroupRemoved(ou, a));
		Set<String> userUuids = orgUnitService.findUserUuidsForOu(ou, false);
		addMultipleUsersToAssignmentCalculatorQueue(userUuids);
	}

	/**
	 * @param ou       OrgUnit that has changed
	 * @param userRole not relevant
	 * @param inherit  x
	 */
	@Override
	public void interceptAddUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole, boolean inherit) {
		// this hook uses the Immediate version (only) instead
	}

	@Override
	public void interceptAddImmediateUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole, boolean inherit) {
		Set<String> userUuids = inherit
			? orgUnitService.findUserUuidsForOu(ou, true)
			: orgUnitService.findUserUuidsForOu(ou, false);

		addMultipleUsersToAssignmentCalculatorQueue(userUuids);
	}

	/**
	 * Fired @Before OrgUnitService.removeUserRole — ou.getUserRoleAssignments() still contains the
	 * assignment being removed. Use forEach (not findFirst) since the same role may be assigned
	 * multiple times on the same OU with different constraints.
	 */
	@Override
	public void interceptRemoveUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole) {
		ou.getUserRoleAssignments().stream()
			.filter(a -> a.getUserRole().getId() == userRole.getId())
			.forEach(a -> historicOuAssignmentService.recordUserRoleRemoved(ou, a));
		Set<String> userUuids = orgUnitService.findUserUuidsForOu(ou, true);
		addMultipleUsersToAssignmentCalculatorQueue(userUuids);
	}

	/**
	 * Fired @Before OrgUnitService.updateUserRoleAssignment — assignment carries pre-update state,
	 * used to compute the hash of the open record to close. Use forEach in case the same role is
	 * assigned multiple times on the same OU with different constraints.
	 */
	@Override
	public void interceptEditUserRoleAssignmentOnOrgUnit(OrgUnit ou, UserRole userRole) {
		ou.getUserRoleAssignments().stream()
			.filter(a -> a.getUserRole().getId() == userRole.getId())
			.forEach(a -> historicOuAssignmentService.recordUserRoleUpdated(ou, a));
		Set<String> userUuids = orgUnitService.findUserUuidsForOu(ou, true);
		addMultipleUsersToAssignmentCalculatorQueue(userUuids);
	}

	/**
	 * Fired @Before OrgUnitService.updateRoleGroupAssignment — assignment carries pre-update state,
	 * used to compute hashes of the open records to close. Use forEach in case the same role group
	 * is assigned multiple times on the same OU with different constraints.
	 */
	@Override
	public void interceptEditRoleGroupAssignmentOnOrgUnit(OrgUnit ou, RoleGroup roleGroup) {
		ou.getRoleGroupAssignments().stream()
			.filter(a -> a.getRoleGroup().getId() == roleGroup.getId())
			.forEach(a -> historicOuAssignmentService.recordRoleGroupUpdated(ou, a));
		Set<String> userUuids = orgUnitService.findUserUuidsForOu(ou, true);
		addMultipleUsersToAssignmentCalculatorQueue(userUuids);
	}

	/**
	 * @param roleGroup roleGroup that has changed
	 * @param userRole  not relevant
	 */
	@Override
	public void interceptAddUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		historicOuAssignmentService.recordUserRoleAddedToRoleGroup(roleGroup, userRole);
		addUsersWithRoleGroup(roleGroup);
	}

	/**
	 * @param roleGroup roleGroup that has changed
	 * @param userRole  userRole that was removed
	 */
	@Override
	public void interceptRemoveUserRoleAssignmentOnRoleGroup(RoleGroup roleGroup, UserRole userRole) {
		historicOuAssignmentService.recordUserRoleRemovedFromRoleGroup(roleGroup, userRole);
		addUsersWithRoleGroup(roleGroup);
	}

	/**
	 * @param userRole             userRole that has changed
	 * @param systemRoleAssignment not relevant
	 */
	@Override
	public void interceptAddSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		historicItSystemAssignmentService.recordSystemRoleAssignmentAdded(userRole, systemRoleAssignment);
		addAddUsersWithUserRole(userRole);
	}

	/**
	 * @param userRole             userRole that has changed
	 * @param systemRoleAssignment not relevant
	 */
	@Override
	public void interceptRemoveSystemRoleAssignmentOnUserRole(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		historicItSystemAssignmentService.recordSystemRoleAssignmentRemoved(userRole, systemRoleAssignment);
		addAddUsersWithUserRole(userRole);
	}

	private void addUsersWithRoleGroup(RoleGroup roleGroup) {
		Set<String> userUuids = assignmentService.getUserUuidsByRoleGroup(roleGroup);
		addMultipleUsersToAssignmentCalculatorQueue(userUuids);
	}

	@Override
	public void interceptDeleteUserRole(UserRole userRole) {
		addAddUsersWithUserRole(userRole);
	}

	@Override
	public void interceptDeleteRoleGroup(RoleGroup roleGroup) {
		addUsersWithRoleGroup(roleGroup);
	}

	private void addAddUsersWithUserRole(UserRole userRole) {
		// En transient UserRole (endnu ikke persisteret) kan per definition ikke have
		// CurrentAssignment-rækker, og Hibernate 6 afviser at bruge den som query-parameter.
		if (userRole.getId() == 0) {
			return;
		}

		// User-UUID-projektion frem for getByUserRole(): hookket kører @Before delete, og hvis vi
		// trak CurrentAssignment-entiteter ind i persistence-konteksten ville de holde en reference
		// til den nu slettede UserRole — næste iteration's auto-flush kaster så TransientPropertyValueException.
		final Set<String> userUuids = assignmentService.getUserUuidsByUserRole(userRole);

		addMultipleUsersToAssignmentCalculatorQueue(userUuids);
	}

	/**
	 * Adds uuid of a user to the queue, in order to recalculate changed roles for the user
	 *
	 * @param userUuid Uuid of a user, for which role assignments has changed in some way
	 */
	private void addUserUuidToAssignmentCalculatorQueue(String userUuid) {
		userService.queueForRecalculation(userUuid);
	}

	private void addMultipleUsersToAssignmentCalculatorQueue(final Set<String> userUuids) {
		userService.queueMultipleForRecalculation(userUuids);
	}
}
