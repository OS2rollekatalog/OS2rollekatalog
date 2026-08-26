package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
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

import java.util.ArrayList;
import java.util.List;

import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createRoleGroup;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createRoleRequest;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createUserRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

		@Test
		@DisplayName("self-approval is still blocked when the requester is a different Java object with the same uuid")
		void blocksSelfApprovalAcrossDetachedInstancesWithSameUuid() {
			// Arrange: a detached/refetched User (e.g. loaded fresh in a controller) is a different
			// reference than the one on the request, but represents the same person. User has no
			// equals()/hashCode(), so this must be compared by uuid, not by reference.
			User requesterInstance = createUser("self-uuid");
			User refetchedSameUser = createUser("self-uuid");
			ItSystem itSystem = createItSystem("system", List.of());
			UserRole userRole = createUserRole("role", itSystem, List.of());
			RoleRequest request = createRoleRequest(requesterInstance, null, userRole);
			request.setRequester(requesterInstance);

			// Act
			boolean canApprove = requestApproverResolver.canApprove(request, refetchedSameUser);

			// Assert
			assertThat(canApprove).isFalse();
		}
	}

	@Nested
	@DisplayName("A deleted user can never approve")
	class DeletedApprover {

		@Test
		@DisplayName("canApprove returns false for a deleted approver even if otherwise entitled")
		void deletedApproverCannotApprove() {
			User approver = createUser("approver");
			approver.setDeleted(true);
			ItSystem itSystem = createItSystem("system", List.of());
			UserRole userRole = createUserRole("role", itSystem, List.of(ApprovableBy.AUTOMATIC));
			RoleRequest request = createRoleRequest(createUser("receiver"), null, userRole);
			request.setApproverOption(List.of(ApprovableBy.AUTOMATIC));

			boolean canApprove = requestApproverResolver.canApprove(request, approver);

			assertThat(canApprove).isFalse();
		}
	}

	@Nested
	@DisplayName("The request's snapshotted approver options gate approval, not the role's current config")
	class ApproverOptionSnapshot {

		@Test
		@DisplayName("a request created as AUTHORIZED must NOT become approvable-by-anyone when the role is later switched to AUTOMATIC")
		void snapshotAuthorizedIsNotOverriddenByLiveAutomatic() {
			// Arrange: the request was snapshotted as AUTHORIZED when created, but the role's current
			// (live) approver option has since been changed to AUTOMATIC.
			User approver = createUser("approver");
			ItSystem itSystem = createItSystem("system", List.of());
			UserRole userRole = createUserRole("role", itSystem, List.of(ApprovableBy.AUTOMATIC));
			RoleRequest request = createRoleRequest(createUser("receiver"), null, userRole);
			request.setApproverOption(List.of(ApprovableBy.AUTHORIZED));

			// The approver is not authorized for the role's it-system, so under AUTHORIZED they may not approve.
			when(requestAuthorizedRoleService.accessibleItsSystems(approver))
				.thenReturn(new RequestAuthorizedRoleService.LimitedToItSystems(
					RequestAuthorizedRoleService.LimitedToType.NONE, java.util.Set.of()));

			// Act
			boolean canApprove = requestApproverResolver.canApprove(request, approver);

			// Assert: gated by the AUTHORIZED snapshot (which the approver fails), NOT the live AUTOMATIC.
			assertThat(canApprove).isFalse();
			// The live role config must not even be consulted when a snapshot is present.
			verify(approverOptionService, never()).getInheritedApproverOption(any(UserRole.class));
		}

		@Test
		@DisplayName("a legacy request without a snapshot falls back to the role's live approver option")
		void missingSnapshotFallsBackToLiveResolution() {
			// Arrange: legacy request created before the snapshot was stored (approverOption == null).
			User approver = createUser("approver");
			ItSystem itSystem = createItSystem("system", List.of());
			UserRole userRole = createUserRole("role", itSystem, List.of());
			RoleRequest request = createRoleRequest(createUser("receiver"), null, userRole);
			assertThat(request.getApproverOption()).isNull();

			when(approverOptionService.getInheritedApproverOption(userRole))
				.thenReturn(List.of(ApprovableBy.AUTOMATIC));

			// Act
			boolean canApprove = requestApproverResolver.canApprove(request, approver);

			// Assert: falls back to the live option (AUTOMATIC) so legacy requests keep working.
			assertThat(canApprove).isTrue();
		}
	}

	/**
	 * determineApprovable() delegates the MANAGERORSUBSTITUTE/AUTHRESPONSIBLE hierarchy walk to
	 * OrgUnitService.getEffectiveApprover()/isAuthorizationManagerFor(). Those methods are exercised
	 * against real object graphs in OrgUnitServiceTest, but here we wire a REAL OrgUnitService (not a
	 * mock returning a canned answer) so this class's own use of the results — the manager/substitute
	 * comparison, the OU that gates the substitute match — is verified against actual recursive behavior,
	 * not an assumption about what OrgUnitService does.
	 */
	@Nested
	@DisplayName("determineApprovable — MANAGERORSUBSTITUTE / AUTHRESPONSIBLE against a real OrgUnitService")
	class DetermineApprovableRealOrgUnitService {

		private final OrgUnitService realOrgUnitService = new OrgUnitService();
		private RequestApproverResolver resolver;

		private RequestApproverResolver resolverWithRealOrgUnitService() {
			return new RequestApproverResolver(approverOptionService, requestAuthorizedRoleService, realOrgUnitService, itSystemService, settingsService);
		}

		@Test
		@DisplayName("manager of the direct OU can approve")
		void directManagerCanApprove() {
			User manager = createUser("manager");
			manager.setManagerSubstitutes(new ArrayList<>());
			OrgUnit orgUnit = createOrgUnit("ou-1", null, manager);
			User receiver = createUser("receiver");

			UserRole userRole = createUserRole("role", createItSystem("system", List.of()), List.of());
			RoleRequest request = createRoleRequest(receiver, orgUnit, userRole);
			request.setApproverOption(List.of(ApprovableBy.MANAGERORSUBSTITUTE));

			resolver = resolverWithRealOrgUnitService();

			assertThat(resolver.canApprove(request, manager)).isTrue();
		}

		@Test
		@DisplayName("walks up to the parent OU manager when the direct OU's manager is the receiver")
		void walksToParentWhenDirectManagerIsReceiver() {
			User receiverAsManager = createUser("receiver-manager");
			receiverAsManager.setManagerSubstitutes(new ArrayList<>());
			User parentManager = createUser("parent-manager");
			parentManager.setManagerSubstitutes(new ArrayList<>());
			OrgUnit parentOu = createOrgUnit("ou-parent", null, parentManager);
			OrgUnit childOu = createOrgUnit("ou-child", parentOu, receiverAsManager);

			UserRole userRole = createUserRole("role", createItSystem("system", List.of()), List.of());
			RoleRequest request = createRoleRequest(receiverAsManager, childOu, userRole);
			request.setApproverOption(List.of(ApprovableBy.MANAGERORSUBSTITUTE));

			resolver = resolverWithRealOrgUnitService();

			assertThat(resolver.canApprove(request, parentManager)).isTrue();
			// The receiver themselves must not be treated as their own approver even though
			// they are literally the child OU's manager.
			assertThat(resolver.canApprove(request, receiverAsManager)).isFalse();
		}

		@Test
		@DisplayName("a substitute registered on the resolved (parent) OU can approve")
		void substituteOnResolvedOuCanApprove() {
			User manager = createUser("manager");
			User substitute = createUser("substitute");
			OrgUnit orgUnit = createOrgUnit("ou-1", null, manager);

			ManagerSubstitute ms = new ManagerSubstitute();
			ms.setSubstitute(substitute);
			ms.setOrgUnit(orgUnit);
			List<ManagerSubstitute> substitutes = new ArrayList<>();
			substitutes.add(ms);
			manager.setManagerSubstitutes(substitutes);

			User receiver = createUser("receiver");
			UserRole userRole = createUserRole("role", createItSystem("system", List.of()), List.of());
			RoleRequest request = createRoleRequest(receiver, orgUnit, userRole);
			request.setApproverOption(List.of(ApprovableBy.MANAGERORSUBSTITUTE));

			resolver = resolverWithRealOrgUnitService();

			assertThat(resolver.canApprove(request, substitute)).isTrue();
		}

		@Test
		@DisplayName("a substitute registered on a DIFFERENT OU than the resolved one cannot approve")
		void substituteOnDifferentOuCannotApprove() {
			User manager = createUser("manager");
			User substitute = createUser("substitute");
			OrgUnit orgUnit = createOrgUnit("ou-1", null, manager);
			OrgUnit otherOu = createOrgUnit("ou-other", null, manager);

			ManagerSubstitute ms = new ManagerSubstitute();
			ms.setSubstitute(substitute);
			ms.setOrgUnit(otherOu);
			List<ManagerSubstitute> substitutes = new ArrayList<>();
			substitutes.add(ms);
			manager.setManagerSubstitutes(substitutes);

			User receiver = createUser("receiver");
			UserRole userRole = createUserRole("role", createItSystem("system", List.of()), List.of());
			RoleRequest request = createRoleRequest(receiver, orgUnit, userRole);
			request.setApproverOption(List.of(ApprovableBy.MANAGERORSUBSTITUTE));

			resolver = resolverWithRealOrgUnitService();

			assertThat(resolver.canApprove(request, substitute)).isFalse();
		}

		@Test
		@DisplayName("an unrelated user (neither manager nor substitute) cannot approve")
		void unrelatedUserCannotApprove() {
			User manager = createUser("manager");
			manager.setManagerSubstitutes(new ArrayList<>());
			User stranger = createUser("stranger");
			OrgUnit orgUnit = createOrgUnit("ou-1", null, manager);

			User receiver = createUser("receiver");
			UserRole userRole = createUserRole("role", createItSystem("system", List.of()), List.of());
			RoleRequest request = createRoleRequest(receiver, orgUnit, userRole);
			request.setApproverOption(List.of(ApprovableBy.MANAGERORSUBSTITUTE));

			resolver = resolverWithRealOrgUnitService();

			assertThat(resolver.canApprove(request, stranger)).isFalse();
		}

		@Test
		@DisplayName("authorization manager on the receiver's OU can approve under AUTHRESPONSIBLE")
		void authResponsibleCanApprove() {
			User authManager = createUser("auth-manager");
			OrgUnit orgUnit = createOrgUnit("ou-1", null);
			dk.digitalidentity.rc.dao.model.AuthorizationManager am = new dk.digitalidentity.rc.dao.model.AuthorizationManager();
			am.setUser(authManager);
			orgUnit.setAuthorizationManagers(List.of(am));

			User receiver = createUser("receiver");
			dk.digitalidentity.rc.dao.model.Position position = new dk.digitalidentity.rc.dao.model.Position();
			position.setOrgUnit(orgUnit);
			receiver.setPositions(List.of(position));

			UserRole userRole = createUserRole("role", createItSystem("system", List.of()), List.of());
			RoleRequest request = createRoleRequest(receiver, orgUnit, userRole);
			request.setApproverOption(List.of(ApprovableBy.AUTHRESPONSIBLE));

			resolver = resolverWithRealOrgUnitService();

			assertThat(resolver.canApprove(request, authManager)).isTrue();
		}

		@Test
		@DisplayName("user who is authorization manager on an unrelated OU cannot approve under AUTHRESPONSIBLE")
		void authResponsibleOnUnrelatedOuCannotApprove() {
			User authManager = createUser("auth-manager");
			OrgUnit unrelatedOu = createOrgUnit("ou-other", null);
			dk.digitalidentity.rc.dao.model.AuthorizationManager am = new dk.digitalidentity.rc.dao.model.AuthorizationManager();
			am.setUser(authManager);
			unrelatedOu.setAuthorizationManagers(List.of(am));

			OrgUnit orgUnit = createOrgUnit("ou-1", null);
			User receiver = createUser("receiver");
			dk.digitalidentity.rc.dao.model.Position position = new dk.digitalidentity.rc.dao.model.Position();
			position.setOrgUnit(orgUnit);
			receiver.setPositions(List.of(position));

			UserRole userRole = createUserRole("role", createItSystem("system", List.of()), List.of());
			RoleRequest request = createRoleRequest(receiver, orgUnit, userRole);
			request.setApproverOption(List.of(ApprovableBy.AUTHRESPONSIBLE));

			resolver = resolverWithRealOrgUnitService();

			assertThat(resolver.canApprove(request, authManager)).isFalse();
		}
	}
}
