package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createRoleGroup;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createRoleRequest;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createUserRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestApproverResolverTest {

	@Mock
	private ApproverOptionService approverOptionService;

	@Mock
	private RequestAuthorizedRoleService requestAuthorizedRoleService;

	@Mock
	private OrgUnitService orgUnitService;

	@Mock
	private ItSystemService itSystemService;

	@Mock
	private SettingsService settingsService;

	@InjectMocks
	private RequestApproverResolver requestApproverResolver;

	@Nested
	@DisplayName("SYSTEMRESPONSIBLE approval of a role group requires responsibility for ALL contained IT-systems")
	class RoleGroupSystemResponsible {

		private RoleGroup roleGroupSpanningTwoSystems(User responsibleA, User responsibleB) {
			ItSystem systemA = createItSystem("system-a", List.of());
			ItSystem systemB = createItSystem("system-b", List.of());
			when(itSystemService.getAttestationResponsibleUserIds(systemA))
				.thenReturn(List.of(responsibleA.getUserId()));
			when(itSystemService.getAttestationResponsibleUserIds(systemB))
				.thenReturn(List.of(responsibleB.getUserId()));

			UserRole roleA = createUserRole("role-a", systemA, List.of());
			UserRole roleB = createUserRole("role-b", systemB, List.of());

			return createRoleGroup(1L, List.of(roleA, roleB), List.of(ApprovableBy.SYSTEMRESPONSIBLE));
		}

		@Test
		@DisplayName("should NOT allow a user responsible for only one of the bundle's systems to approve")
		void responsibleForOneSystemCannotApprove() {
			// Arrange
			User approver = createUser("approver");
			approver.setUserId("approver");
			User otherResponsible = createUser("other-responsible");
			otherResponsible.setUserId("other-responsible");
			RoleGroup roleGroup = roleGroupSpanningTwoSystems(approver, otherResponsible);
			RoleRequest request = createRoleRequest(createUser("receiver"), null, roleGroup);

			when(approverOptionService.getInheritedApproverOption(roleGroup))
				.thenReturn(List.of(ApprovableBy.SYSTEMRESPONSIBLE));

			// Act
			boolean canApprove = requestApproverResolver.canApprove(request, approver);

			// Assert
			assertThat(canApprove).isFalse();
		}

		@Test
		@DisplayName("should allow a user responsible for all of the bundle's systems to approve")
		void responsibleForAllSystemsCanApprove() {
			// Arrange
			User approver = createUser("approver");
			approver.setUserId("approver");
			RoleGroup roleGroup = roleGroupSpanningTwoSystems(approver, approver);
			RoleRequest request = createRoleRequest(createUser("receiver"), null, roleGroup);

			when(approverOptionService.getInheritedApproverOption(roleGroup))
				.thenReturn(List.of(ApprovableBy.SYSTEMRESPONSIBLE));

			// Act
			boolean canApprove = requestApproverResolver.canApprove(request, approver);

			// Assert
			assertThat(canApprove).isTrue();
		}
	}

	@Nested
	@DisplayName("Self-approval is blocked unless the municipality has enabled it")
	class SelfApproval {

		@Test
		@DisplayName("should NOT allow a user to approve their own request when the setting is disabled")
		void cannotApproveOwnRequestWhenSettingDisabled() {
			// Arrange
			User user = createUser("self");
			ItSystem itSystem = createItSystem("system", List.of());
			UserRole userRole = createUserRole("role", itSystem, List.of());
			RoleRequest request = createRoleRequest(user, null, userRole);
			request.setRequester(user);

			// Act
			boolean canApprove = requestApproverResolver.canApprove(request, user);

			// Assert
			assertThat(canApprove).isFalse();
		}

		@Test
		@DisplayName("should allow a user to approve their own request when the setting is enabled and they are otherwise entitled")
		void canApproveOwnRequestWhenSettingEnabled() {
			// Arrange
			User user = createUser("self");
			ItSystem itSystem = createItSystem("system", List.of());
			UserRole userRole = createUserRole("role", itSystem, List.of());
			RoleRequest request = createRoleRequest(user, null, userRole);
			request.setRequester(user);

			when(settingsService.isAllowSelfApprovalEnabled()).thenReturn(true);
			when(approverOptionService.getInheritedApproverOption(userRole))
				.thenReturn(List.of(ApprovableBy.AUTOMATIC));

			// Act
			boolean canApprove = requestApproverResolver.canApprove(request, user);

			// Assert
			assertThat(canApprove).isTrue();
		}
	}
}
