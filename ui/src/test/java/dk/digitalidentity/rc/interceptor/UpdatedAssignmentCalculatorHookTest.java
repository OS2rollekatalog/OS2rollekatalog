package dk.digitalidentity.rc.interceptor;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.assignment.HistoricItSystemAssignmentService;
import dk.digitalidentity.rc.service.assignment.HistoricOuAssignmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdatedAssignmentCalculatorHookTest {
	@Mock
	private UserService userService;
	@Mock
	private OrgUnitService orgUnitService;
	@Mock
	private AssignmentService assignmentService;
	@Mock
	private HistoricOuAssignmentService historicOuAssignmentService;
	@Mock
	private HistoricItSystemAssignmentService historicItSystemAssignmentService;

	@InjectMocks
	private UpdatedAssignmentCalculatorHook hook;

	private static OrgUnit orgUnitWithRoleGroup(RoleGroup roleGroup, boolean inherit) {
		OrgUnitRoleGroupAssignment assignment = new OrgUnitRoleGroupAssignment();
		assignment.setRoleGroup(roleGroup);
		assignment.setInherit(inherit);
		OrgUnit ou = new OrgUnit();
		ou.setRoleGroupAssignments(new ArrayList<>(List.of(assignment)));
		return ou;
	}

	@Test
	@DisplayName("removing an INHERITED role group from an OU recalculates users in descendant OUs")
	void removeInheritedRoleGroupIncludesDescendants() {
		// Arrange
		RoleGroup roleGroup = new RoleGroup();
		OrgUnit ou = orgUnitWithRoleGroup(roleGroup, true);
		Set<String> descendantUsers = Set.of("user-in-sub-ou");
		when(orgUnitService.findUserUuidsForOu(ou, true)).thenReturn(descendantUsers);

		// Act
		hook.interceptRemoveRoleGroupAssignmentOnOrgUnit(ou, roleGroup);

		// Assert: inherited => descendants must be recalculated, otherwise sub-OU users keep the
		// role group until an unrelated change triggers a recalc (the reported bug).
		verify(orgUnitService).findUserUuidsForOu(ou, true);
		verify(orgUnitService, never()).findUserUuidsForOu(ou, false);
		verify(userService).queueMultipleForRecalculation(descendantUsers);
	}

	@Test
	@DisplayName("removing a NON-inherited role group from an OU recalculates only that OU's own users")
	void removeNonInheritedRoleGroupSkipsDescendants() {
		// Arrange
		RoleGroup roleGroup = new RoleGroup();
		OrgUnit ou = orgUnitWithRoleGroup(roleGroup, false);
		Set<String> directUsers = Set.of("user-in-this-ou");
		when(orgUnitService.findUserUuidsForOu(ou, false)).thenReturn(directUsers);

		// Act
		hook.interceptRemoveRoleGroupAssignmentOnOrgUnit(ou, roleGroup);

		// Assert: not inherited => only direct users, recalculating the subtree would be wasted work.
		verify(orgUnitService).findUserUuidsForOu(ou, false);
		verify(orgUnitService, never()).findUserUuidsForOu(ou, true);
		verify(userService).queueMultipleForRecalculation(directUsers);
	}

	@Test
	@DisplayName("removing a user role from an OU recalculates users in descendant OUs")
	void removeUserRoleOnOrgUnitIncludesDescendants() {
		// Arrange
		OrgUnit ou = new OrgUnit();
		ou.setUserRoleAssignments(new ArrayList<>());
		UserRole userRole = new UserRole();
		Set<String> descendantUsers = Set.of("user-in-sub-ou");
		when(orgUnitService.findUserUuidsForOu(ou, true)).thenReturn(descendantUsers);

		// Act
		hook.interceptRemoveUserRoleAssignmentOnOrgUnit(ou, userRole);

		// Assert
		verify(orgUnitService).findUserUuidsForOu(ou, true);
		verify(orgUnitService, never()).findUserUuidsForOu(ou, false);
		verify(userService).queueMultipleForRecalculation(descendantUsers);
	}
}
