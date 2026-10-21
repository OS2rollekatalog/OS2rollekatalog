package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.rolerequest.service.permission.ResolvedPermissionService;
import dk.digitalidentity.rc.rolerequest.service.permission.PermittedRoleIds;
import dk.digitalidentity.rc.rolerequest.service.permission.RequestAuthority;
import dk.digitalidentity.rc.rolerequest.service.permission.RequestableRoleService;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManualAssignmentNotificationMap;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.rolerequest.log.RequestPersister;
import dk.digitalidentity.rc.rolerequest.model.dto.RequestedRole;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.service.ManualAssignmentNotificationMapService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createRoleGroup;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createRoleRequest;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createUserRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestServiceTest {

	@Mock
	private UserService userService;

	@Mock
	private OrgUnitService orgUnitService;

	@Mock
	private ItSystemService itSystemService;

	@Mock
	private UserRoleService userRoleService;

	@Mock
	private RequestAuthorizedRoleService requestAuthorizedRoleService;

	@Mock
	private SettingsService settingsService;

	@Mock
	private MessageSource messageSource;

	@Mock
	private AssignmentService assignmentService;

	@Mock
	private CurrentAssignmentService currentAssignmentService;

	@Mock
	private RequestPersister requestPersister;

	@Mock
	private SecurityUtil securityUtil;

	@Mock
	private RequestNotifierService requestNotifierService;

	@Mock
	private ManualAssignmentNotificationMapService manualAssignmentNotificationMapService;

	@Mock
	private RequestableRoleService requestableRoleService;

	@Mock
	private ResolvedPermissionService resolvedPermissionService;

	@Mock
	private RoleGroupService roleGroupService;

	@InjectMocks
	private RequestService requestService;

	private MockedStatic<SecurityUtil> securityUtilMock;

	@BeforeEach
	void setUp() {
		securityUtilMock = mockStatic(SecurityUtil.class);
	}

	@AfterEach
	void tearDown() {
		securityUtilMock.close();
	}

	@Nested
	@DisplayName("approveRequest")
	class ApproveRequest {

		private User receiver;
		private OrgUnit orgUnit;
		private Domain domain;

		@BeforeEach
		void setUpReceiver() {
			domain = new Domain();
			domain.setId(1L);
			receiver = createUser("receiver-uuid");
			receiver.setUserId("receiver-user-id");
			receiver.setDomain(domain);
			orgUnit = createOrgUnit("receivers-orgunit-uuid", null);
			when(settingsService.isRequestApproveEnabled()).thenReturn(true);
			when(requestableRoleService.requestAuthority(any(), any(), any(), any(UserRole.class))).thenReturn(RequestAuthority.MANAGER);
			when(requestableRoleService.requestAuthority(any(), any(), any(), any(RoleGroup.class))).thenReturn(RequestAuthority.MANAGER);
		}

		@Test
		@DisplayName("ADD userrole: calls addUserRole with the request's reason")
		void assignUserRolePassesReason() {
			ItSystem itSystem = createItSystem("it-system-uuid", List.of());
			itSystem.setSystemType(ItSystemType.MANUAL);
			UserRole userRole = createUserRole("role-uuid", itSystem, List.of());
			RoleRequest request = createRoleRequest(receiver, orgUnit, userRole);
			request.setRequestAction(RequestAction.ADD);
			request.setReason("fordi jeg sagde det");

			requestService.approveRequest(request);

			verify(userService).addUserRole(eq(receiver), eq(userRole), any(), any(), any(), any(), anyBoolean(), isNull(), eq("fordi jeg sagde det"));
		}

		@Test
		@DisplayName("ADD rolegroup: calls addRoleGroup with the request's reason")
		void assignRoleGroupPassesReason() {
			ItSystem itSystem = createItSystem("it-system-uuid", List.of());
			itSystem.setSystemType(ItSystemType.MANUAL);
			UserRole userRole = createUserRole("role-uuid", itSystem, List.of());
			RoleGroup roleGroup = createRoleGroup(1L, List.of(userRole), List.of());
			RoleRequest request = createRoleRequest(receiver, orgUnit, roleGroup);
			request.setRequestAction(RequestAction.ADD);
			request.setReason("fordi jeg sagde det");

			requestService.approveRequest(request);

			verify(userService).addRoleGroup(eq(receiver), eq(roleGroup), isNull(), isNull(), eq(orgUnit), isNull(), eq("fordi jeg sagde det"));
		}

		@Test
		@DisplayName("REMOVE userrole: writes removalReason on the matching notification-map row")
		void removeUserRoleWritesRemovalReason() {
			ItSystem itSystem = createItSystem("it-system-uuid", List.of());
			UserRole userRole = createUserRole("role-uuid", itSystem, List.of());
			userRole.setId(100L);
			RoleRequest request = createRoleRequest(receiver, orgUnit, userRole);
			request.setRequestAction(RequestAction.REMOVE);
			request.setReason("ikke længere ansat");

			ManualAssignmentNotificationMap mapRow = new ManualAssignmentNotificationMap();
			mapRow.setUserRoleId(100L);
			ManualAssignmentNotificationMap unrelatedRow = new ManualAssignmentNotificationMap();
			unrelatedRow.setUserRoleId(999L);
			when(manualAssignmentNotificationMapService.getForUser(1L, "receiver-user-id")).thenReturn(List.of(mapRow, unrelatedRow));

			requestService.approveRequest(request);

			verify(manualAssignmentNotificationMapService).save(mapRow);
			assertThat(mapRow.getRemovalReason()).isEqualTo("ikke længere ansat");
			assertThat(unrelatedRow.getRemovalReason()).isNull();
			verify(userService).removeUserRole(receiver, userRole);
		}

		@Test
		@DisplayName("REMOVE rolegroup: writes removalReason on every matching notification-map row")
		void removeRoleGroupWritesRemovalReasonOnAllMatchingRows() {
			ItSystem itSystem = createItSystem("it-system-uuid", List.of());
			itSystem.setSystemType(ItSystemType.MANUAL);
			UserRole userRole1 = createUserRole("role-uuid-1", itSystem, List.of());
			userRole1.setId(100L);
			UserRole userRole2 = createUserRole("role-uuid-2", itSystem, List.of());
			userRole2.setId(200L);
			RoleGroup roleGroup = createRoleGroup(1L, List.of(userRole1, userRole2), List.of());
			RoleRequest request = createRoleRequest(receiver, orgUnit, roleGroup);
			request.setRequestAction(RequestAction.REMOVE);
			request.setReason("ikke længere ansat");

			ManualAssignmentNotificationMap mapRow1 = new ManualAssignmentNotificationMap();
			mapRow1.setUserRoleId(100L);
			ManualAssignmentNotificationMap mapRow2 = new ManualAssignmentNotificationMap();
			mapRow2.setUserRoleId(200L);
			ManualAssignmentNotificationMap unrelatedRow = new ManualAssignmentNotificationMap();
			unrelatedRow.setUserRoleId(999L);
			when(manualAssignmentNotificationMapService.getForUser(1L, "receiver-user-id")).thenReturn(List.of(mapRow1, mapRow2, unrelatedRow));

			requestService.approveRequest(request);

			assertThat(mapRow1.getRemovalReason()).isEqualTo("ikke længere ansat");
			assertThat(mapRow2.getRemovalReason()).isEqualTo("ikke længere ansat");
			assertThat(unrelatedRow.getRemovalReason()).isNull();
			verify(userService).removeRoleGroup(receiver, roleGroup);
		}
	}

	@Nested
	@DisplayName("buildPermittedRoleRequests for removal")
	class CreateRemovalRequest {

		@Test
		@DisplayName("Takes the orgunit from the assignment being removed, so the approval mail has a recipient")
		void removalRequestTakesOrgUnitFromAssignment() {
			User requester = createUser("requester-uuid");
			User receiver = createUser("receiver-uuid");

			UserRole role = createUserRole("role-uuid", null, List.of(), List.of(RequestableBy.MANAGERORSUBSTITUTE));
			role.setId(100L);
			UserRole otherRole = new UserRole();
			otherRole.setId(200L);

			OrgUnit assignmentOrgUnit = createOrgUnit("assignment-orgunit-uuid", null);
			receiver.getUserRoleAssignments().add(directAssignment(otherRole, createOrgUnit("other-orgunit-uuid", null)));
			receiver.getUserRoleAssignments().add(directAssignment(role, assignmentOrgUnit));

			when(userRoleService.findAllByIdIn(Set.of(100L))).thenReturn(Set.of(role));
			when(requestableRoleService.removableAmong(requester, receiver, Set.of(100L), Set.of()))
				.thenReturn(new PermittedRoleIds(Set.of(100L), Set.of()));

			RoleRequest request = requestService.buildPermittedRoleRequests(
				requester, receiver, null, UUID.randomUUID(), "ikke længere ansat",
				List.of(RequestedRole.userRole(100L, null, null)), RequestAction.REMOVE).getFirst();

			assertThat(request.getOrgUnit()).isEqualTo(assignmentOrgUnit);
		}

		private UserUserRoleAssignment directAssignment(UserRole userRole, OrgUnit orgUnit) {
			UserUserRoleAssignment assignment = new UserUserRoleAssignment();
			assignment.setUserRole(userRole);
			assignment.setOrgUnit(orgUnit);
			return assignment;
		}
	}
}
