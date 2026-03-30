package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;

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
	@DisplayName("canRequest for UserRole")
	class CanRequestUserRole {

		private User receivingUser;
		private OrgUnit receiversOrgUnit;

		@BeforeEach
		void setUpCommonEntities() {
			receivingUser = createUser("receiver-uuid");
			receiversOrgUnit = createOrgUnit("orgunit-uuid", null);
		}

		@Test
		@DisplayName("ReadOnly cannot be requested")
		void readOnlyCannotBeRequested() {
			// Arrange
			securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);

			ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
			UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of());
			role.setReadOnly(true);

			// Act
			boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.NONE));

			// Assert
			assertThat(result).isFalse();
		}

		@Nested
		@DisplayName("Admin can always request")
		class AdminCanAlwaysRequest {

			@BeforeEach
			void setUpAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(true);
			}

			@Test
			@DisplayName("Admin can not request when role permission is NONE")
			void adminCanRequestWhenPermissionIsNone() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.NONE));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.NONE));

				// Assert
				assertThat(result).isFalse();
			}

			@Test
			@DisplayName("Admin can request even when role is filtered to different orgUnit")
			void adminCanRequestEvenWithOrgUnitFilter() {
				// Arrange
				OrgUnit filteredOrgUnit = createOrgUnit("filtered-orgunit-uuid", null);
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.ADMIN));
				itSystem.setOrgUnitFilterOrgUnits(List.of(filteredOrgUnit));

				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.ADMIN));
				OrgUnit differentOrgUnit = createOrgUnit("different-orgunit-uuid", null);

				// Act
				boolean result = requestService.canRequest(role, receivingUser, differentOrgUnit, List.of(RequestableBy.ADMIN));

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Admin cannot request readOnly role")
			void adminCannotRequestReadOnlyRole() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.ADMIN));
				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.ADMIN));
				role.setReadOnly(true);

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.ADMIN));

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("Uses the role's RequestableBy unless INHERIT")
		class UsesRolePermission {

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("requester-user-id");
			}

			@Test
			@DisplayName("User can request for self with Role set to EMPLOYEE")
			void usesRolePermissionEmployee() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.NONE));
				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.EMPLOYEE));

				when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.of(receivingUser));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.NONE));

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Does not use IT system permission when role has explicit permission")
			void doesNotUseItSystemPermissionWhenRoleHasExplicit() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.EMPLOYEE));
				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.NONE));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.EMPLOYEE));

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("Uses IT system's RequestableBy when role is INHERIT")
		class UsesItSystemPermission {

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
			}

			@Test
			@DisplayName("Inherits IT system permission when role is set to INHERIT")
			void inheritsItSystemPermission() {
				// Arrange
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("requester-user-id");

				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.EMPLOYEE));
				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.INHERIT));

				when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.of(receivingUser));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.NONE));

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Uses IT system NONE permission when role inherits")
			void inheritsItSystemNonePermission() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.NONE));
				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.INHERIT));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.EMPLOYEE));

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("Uses global RequestableBy when both role and IT system are INHERIT")
		class UsesGlobalPermission {

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("requester-user-id");
			}

			@Test
			@DisplayName("Falls back to global permission when both role and IT system are INHERIT")
			void fallsBackToGlobalPermission() {
				// Arrange
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("requester-user-id");

				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.INHERIT));
				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.INHERIT));

				when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.of(receivingUser));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.EMPLOYEE));

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Uses global NONE when both role and IT system inherit")
			void usesGlobalNonePermission() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.INHERIT));
				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.INHERIT));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.NONE));

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("Returns false when permission is NONE")
		class PermissionNone {

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
			}

			@Test
			@DisplayName("Returns false when role permission is NONE")
			void returnsFalseWhenRolePermissionIsNone() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.EMPLOYEE));
				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.NONE));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.EMPLOYEE));

				// Assert
				assertThat(result).isFalse();
			}

			@Test
			@DisplayName("Returns false when inherited IT system permission is NONE")
			void returnsFalseWhenItSystemPermissionIsNone() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.NONE));
				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.INHERIT));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.EMPLOYEE));

				// Assert
				assertThat(result).isFalse();
			}

			@Test
			@DisplayName("Returns false when global permission is NONE")
			void returnsFalseWhenGlobalPermissionIsNone() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.INHERIT));
				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.INHERIT));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.NONE));

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("OrgUnit filter restricts access")
		class OrgUnitFilter {

			private OrgUnit allowedOrgUnit;

			@BeforeEach
			void setUpNonAdminAndAllowedOrgUnit() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
				allowedOrgUnit = createOrgUnit("allowed-orgunit-uuid", null);
			}

			@Test
			@DisplayName("Denies access when IT system has enabled orgUnit filter and user's orgUnit is not in list")
			void deniesWhenItSystemOrgUnitFilterExcludesUser() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.EMPLOYEE));
				itSystem.setOuFilterEnabled(true);
				itSystem.setOrgUnitFilterOrgUnits(List.of(allowedOrgUnit));
				when(itSystemService.getOUFilterUuidsWithChildren(itSystem)).thenReturn(List.of("allowed-orgunit-uuid"));

				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.EMPLOYEE));
				OrgUnit differentOrgUnit = createOrgUnit("different-orgunit-uuid", null);

				// Act
				boolean result = requestService.canRequest(role, receivingUser, differentOrgUnit, List.of(RequestableBy.EMPLOYEE));

				// Assert
				assertThat(result).isFalse();
			}

			@Test
			@DisplayName("Allows access when IT system has enabled orgUnit filter and user's orgUnit is in list")
			void allowsWhenItSystemOrgUnitFilterIncludesUser() {
				// Arrange
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("requester-user-id");

				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.EMPLOYEE));
				itSystem.setOuFilterEnabled(true);
				itSystem.setOrgUnitFilterOrgUnits(List.of(allowedOrgUnit));
				when(itSystemService.getOUFilterUuidsWithChildren(itSystem)).thenReturn(List.of("allowed-orgunit-uuid"));

				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.EMPLOYEE));
				when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.of(receivingUser));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, allowedOrgUnit, List.of(RequestableBy.EMPLOYEE));

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Denies access when role has orgUnit filter and user's orgUnit is not in list")
			void deniesWhenRoleOrgUnitFilterExcludesUser() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.EMPLOYEE));

				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.EMPLOYEE));
				role.setOuFilterEnabled(true);
				role.setOrgUnitFilterOrgUnits(List.of(allowedOrgUnit));
				when(userRoleService.getOUFilterUuidsWithChildren(role)).thenReturn(List.of("allowed-orgunit-uuid"));

				OrgUnit differentOrgUnit = createOrgUnit("different-orgunit-uuid", null);

				// Act
				boolean result = requestService.canRequest(role, receivingUser, differentOrgUnit, List.of(RequestableBy.EMPLOYEE));

				// Assert
				assertThat(result).isFalse();
			}

			@Test
			@DisplayName("Denies access when receiver's orgUnit is null and filter exists on IT system")
			void deniesWhenReceiversOrgUnitIsNullAndFilterExistsOnSystem() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.EMPLOYEE));
				itSystem.setOuFilterEnabled(true);
				itSystem.setOrgUnitFilterOrgUnits(List.of(allowedOrgUnit));
				when(itSystemService.getOUFilterUuidsWithChildren(itSystem)).thenReturn(List.of("allowed-orgunit-uuid"));

				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.EMPLOYEE));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, null, List.of(RequestableBy.EMPLOYEE));

				// Assert
				assertThat(result).isFalse();
			}

			@Test
			@DisplayName("Denies access when receiver's orgUnit is null and filter exists on Role")
			void deniesWhenReceiversOrgUnitIsNullAndFilterExistsOnRole() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.EMPLOYEE));

				UserRole role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.EMPLOYEE));
				role.setOuFilterEnabled(true);
				role.setOrgUnitFilterOrgUnits(List.of(allowedOrgUnit));
				when(userRoleService.getOUFilterUuidsWithChildren(role)).thenReturn(List.of("allowed-orgunit-uuid"));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, null, List.of(RequestableBy.EMPLOYEE));

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("AUTHORIZED permission with constraints")
		class AuthorizedPermission {

			private User requestingUser;
			private UserRole role;

			@BeforeEach
			void setUpAuthorizedUser() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED)).thenReturn(true);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("requester-user-id");

				requestingUser = createUser("requester-uuid");
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.AUTHORIZED));
				itSystem.setId(1L);
				role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.AUTHORIZED));

				when(userService.getByUserId("requester-user-id")).thenReturn(requestingUser);
			}

			@Test
			@DisplayName("Returns true when user is AUTHORIZED and has access to all orgUnits and itSystems")
			void returnsTrueWhenAuthorizedWithAllAccess() {
				// Arrange
				when(requestAuthorizedRoleService.accessibleOrgUnits(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToOrgUnits(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));
				when(requestAuthorizedRoleService.accessibleItsSystems(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToItSystems(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.NONE));

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Returns true when user is AUTHORIZED and receiver's orgUnit is in constrained list")
			void returnsTrueWhenAuthorizedWithConstrainedOrgUnitAccess() {
				// Arrange
				when(requestAuthorizedRoleService.accessibleOrgUnits(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToOrgUnits(
								RequestAuthorizedRoleService.LimitedToType.CONSTRAINED, Set.of("orgunit-uuid")));
				when(requestAuthorizedRoleService.accessibleItsSystems(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToItSystems(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.NONE));

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Returns false when user is AUTHORIZED but receiver's orgUnit is not in constrained list")
			void returnsFalseWhenAuthorizedButOrgUnitNotInConstrainedList() {
				// Arrange
				when(requestAuthorizedRoleService.accessibleOrgUnits(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToOrgUnits(
								RequestAuthorizedRoleService.LimitedToType.CONSTRAINED, Set.of("other-orgunit-uuid")));
				when(requestAuthorizedRoleService.accessibleItsSystems(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToItSystems(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));
				when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.ofNullable(requestingUser));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.NONE));

				// Assert
				assertThat(result).isFalse();
			}

			@Test
			@DisplayName("Returns false when user is AUTHORIZED but IT system is not in constrained list")
			void returnsFalseWhenAuthorizedButItSystemNotInConstrainedList() {
				// Arrange
				when(requestAuthorizedRoleService.accessibleOrgUnits(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToOrgUnits(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));
				when(requestAuthorizedRoleService.accessibleItsSystems(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToItSystems(
								RequestAuthorizedRoleService.LimitedToType.CONSTRAINED, Set.of(999L)));
				when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.ofNullable(requestingUser));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.NONE));

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("MANAGERORSUBSTITUTE permission")
		class ManagerOrSubstitutePermission {

			private UserRole role;

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);

				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.MANAGERORSUBSTITUTE));
				role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.MANAGERORSUBSTITUTE));
			}

			@Test
			@DisplayName("Returns true when user is manager of receiver and permission is MANAGERORSUBSTITUTE")
			void returnsTrueWhenManagerOfReceiver() {
				// Arrange
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("manager-user-id");

				User managerUser = createUser("manager-uuid");
				managerUser.setUserId("manager-user-id");

				when(userService.getOptionalByUserId("manager-user-id")).thenReturn(Optional.of(managerUser));
				when(userService.isManagerOrSubstituteManagerFor(managerUser, receivingUser)).thenReturn(true);

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.NONE));

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Returns false when user is not manager of receiver")
			void returnsFalseWhenNotManagerOfReceiver() {
				// Arrange
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("non-manager-user-id");

				User nonManagerUser = createUser("non-manager-uuid");
				nonManagerUser.setUserId("non-manager-user-id");

				when(userService.getOptionalByUserId("non-manager-user-id")).thenReturn(Optional.of(nonManagerUser));
				when(userService.isManagerOrSubstituteManagerFor(nonManagerUser, receivingUser)).thenReturn(false);
				when(orgUnitService.getByAuthorizationManagerMatchingUser(nonManagerUser)).thenReturn(List.of());

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.NONE));

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("AUTHRESPONSIBLE permission")
		class AuthResponsiblePermission {

			private UserRole role;

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);

				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.AUTHRESPONSIBLE));
				role = createUserRole("role-uuid", itSystem, List.of(), List.of(RequestableBy.AUTHRESPONSIBLE));
			}

			@Test
			@DisplayName("Returns true when user is AuthorizationResponsible and permission is AUTHRESPONSIBLE")
			void returnsTrueWhenAuthResponsible() {
				// Arrange
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("auth-resp-user-id");

				User authRespUser = createUser("auth-resp-uuid");
				authRespUser.setUserId("auth-resp-user-id");

				when(userService.getOptionalByUserId("auth-resp-user-id")).thenReturn(Optional.of(authRespUser));
				when(userService.isManagerOrSubstituteManagerFor(authRespUser, receivingUser)).thenReturn(false);
				when(orgUnitService.getByAuthorizationManagerMatchingUser(authRespUser)).thenReturn(List.of(receiversOrgUnit));

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.NONE));

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Returns false when user is not AuthorizationResponsible")
			void returnsFalseWhenNotAuthResponsible() {
				// Arrange
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("regular-user-id");

				User regularUser = createUser("regular-uuid");
				regularUser.setUserId("regular-user-id");

				when(userService.getOptionalByUserId("regular-user-id")).thenReturn(Optional.of(regularUser));
				when(userService.isManagerOrSubstituteManagerFor(regularUser, receivingUser)).thenReturn(false);
				when(orgUnitService.getByAuthorizationManagerMatchingUser(regularUser)).thenReturn(List.of());

				// Act
				boolean result = requestService.canRequest(role, receivingUser, receiversOrgUnit, List.of(RequestableBy.NONE));

				// Assert
				assertThat(result).isFalse();
			}
		}
	}

	@Nested
	@DisplayName("canRequest for RoleGroup")
	class CanRequestRoleGroup {

		private User requestingUser;
		private User receivingUser;
		private OrgUnit receiversOrgUnit;

		@BeforeEach
		void setUpCommonEntities() {
			requestingUser = createUser("requester-uuid");
			receivingUser = createUser("receiver-uuid");
			receivingUser.setUserId("requester-user-id");
			receiversOrgUnit = createOrgUnit("orgunit-uuid", null);
		}

		@Nested
		@DisplayName("Admin can always request")
		class AdminCanAlwaysRequest {

			@BeforeEach
			void setUpAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(true);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("requester-user-id");
			}

			@Test
			@DisplayName("Admin can not request when role permission is NONE")
			void adminCanRequestWhenPermissionIsNone() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(), List.of());
				RoleGroup roleGroup = createRoleGroup(1L, List.of(userRole), List.of(), List.of(RequestableBy.NONE));

				when(settingsService.getRolerequestRequester()).thenReturn(List.of(RequestableBy.ADMIN));

				// Act
				boolean result = requestService.canRequest(requestingUser, roleGroup, receivingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("Uses the role's RequestableBy unless INHERIT")
		class UsesRolePermission {

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("requester-user-id");
			}

			@Test
			@DisplayName("User can request for self with Role set to EMPLOYEE")
			void usesRolePermissionEmployee() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of(RequestableBy.NONE));
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(), List.of());
				RoleGroup roleGroup = createRoleGroup(1L, List.of(userRole), List.of(), List.of(RequestableBy.EMPLOYEE));

				when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.of(requestingUser));

				// Act - requesting for self (requestingUser == receivingUser)
				boolean result = requestService.canRequest(requestingUser, roleGroup, requestingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Does not use global permission when role has explicit permission")
			void doesNotUseGlobalPermissionWhenRoleHasExplicit() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(), List.of());
				RoleGroup roleGroup = createRoleGroup(1L, List.of(userRole), List.of(), List.of(RequestableBy.NONE));

				lenient().when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.of(requestingUser));

				// Act
				boolean result = requestService.canRequest(requestingUser, roleGroup, receivingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("Uses global RequestableBy when role is INHERIT")
		class UsesGlobalPermission {

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("requester-user-id");
			}

			@Test
			@DisplayName("Falls back to global permission when role is INHERIT")
			void fallsBackToGlobalPermission() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(), List.of());
				RoleGroup roleGroup = createRoleGroup(1L, List.of(userRole), List.of(), List.of(RequestableBy.INHERIT));

				when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.of(requestingUser));
				when(settingsService.getRolerequestRequester()).thenReturn(List.of(RequestableBy.EMPLOYEE));

				// Act - requesting for self
				boolean result = requestService.canRequest(requestingUser, roleGroup, requestingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Uses global NONE when role inherits")
			void usesGlobalNonePermission() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(), List.of());
				RoleGroup roleGroup = createRoleGroup(1L, List.of(userRole), List.of(), List.of(RequestableBy.INHERIT));

				lenient().when(settingsService.getRolerequestRequester()).thenReturn(List.of(RequestableBy.NONE));
				lenient().when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.ofNullable(requestingUser));

				// Act
				boolean result = requestService.canRequest(requestingUser, roleGroup, receivingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("Returns false when permission is NONE")
		class PermissionNone {

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("requester-user-id");
			}

			@Test
			@DisplayName("Returns false when role permission is NONE")
			void returnsFalseWhenRolePermissionIsNone() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(), List.of());
				RoleGroup roleGroup = createRoleGroup(1L, List.of(userRole), List.of(), List.of(RequestableBy.NONE));

				lenient().when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.ofNullable(requestingUser));

				// Act
				boolean result = requestService.canRequest(requestingUser, roleGroup, receivingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isFalse();
			}

			@Test
			@DisplayName("Returns false when global permission is NONE")
			void returnsFalseWhenGlobalPermissionIsNone() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(), List.of());
				RoleGroup roleGroup = createRoleGroup(1L, List.of(userRole), List.of(), List.of(RequestableBy.INHERIT));

				lenient().when(settingsService.getRolerequestRequester()).thenReturn(List.of(RequestableBy.NONE));
				lenient().when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.ofNullable(requestingUser));

				// Act
				boolean result = requestService.canRequest(requestingUser, roleGroup, receivingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("OrgUnit filter restricts access")
		class OrgUnitFilter {

			private OrgUnit allowedOrgUnit;

			@BeforeEach
			void setUpNonAdminAndAllowedOrgUnit() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("requester-user-id");
				allowedOrgUnit = createOrgUnit("allowed-orgunit-uuid", null);
			}

			@Test
			@DisplayName("Denies access when RoleGroup has enabled orgUnit filter and user's orgUnit is not in list")
			void deniesWhenRoleGroupOrgUnitFilterExcludesUser() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(), List.of());
				RoleGroup roleGroup = createRoleGroup(1L, List.of(userRole), List.of(), List.of(RequestableBy.EMPLOYEE));
				roleGroup.setOuFilterEnabled(true);
				roleGroup.setOrgUnitFilterOrgUnits(List.of(allowedOrgUnit));

				OrgUnit differentOrgUnit = createOrgUnit("different-orgunit-uuid", null);

				// Act - requesting for self so EMPLOYEE would normally allow, but orgUnit filter should deny
				boolean result = requestService.canRequest(requestingUser, roleGroup, requestingUser, differentOrgUnit);

				// Assert
				assertThat(result).isFalse();
			}

			@Test
			@DisplayName("Allows access when RoleGroup has enabled orgUnit filter and user's orgUnit is in list")
			void allowsWhenRoleGroupOrgUnitFilterIncludesUser() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(), List.of());
				RoleGroup roleGroup = createRoleGroup(1L, List.of(userRole), List.of(), List.of(RequestableBy.EMPLOYEE));
				roleGroup.setOuFilterEnabled(true);
				roleGroup.setOrgUnitFilterOrgUnits(List.of(allowedOrgUnit));

				when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.of(requestingUser));

				// Act - requesting for self so EMPLOYEE permission works, and orgUnit is in filter
				boolean result = requestService.canRequest(requestingUser, roleGroup, requestingUser, allowedOrgUnit);

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Denies access when receiver's orgUnit is null and filter exists on RoleGroup")
			void deniesWhenReceiversOrgUnitIsNullAndFilterExistsOnRoleGroup() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(), List.of());
				RoleGroup roleGroup = createRoleGroup(1L, List.of(userRole), List.of(), List.of(RequestableBy.EMPLOYEE));
				roleGroup.setOuFilterEnabled(true);
				roleGroup.setOrgUnitFilterOrgUnits(List.of(allowedOrgUnit));

				// Act - requesting for self but orgUnit is null while filter exists
				boolean result = requestService.canRequest(requestingUser, roleGroup, requestingUser, null);

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("AUTHORIZED permission with constraints")
		class AuthorizedPermission {

			private RoleGroup roleGroup;

			@BeforeEach
			void setUpAuthorizedUser() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED)).thenReturn(true);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("requester-user-id");

				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				itSystem.setId(1L);
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(), List.of());
				roleGroup = createRoleGroup(1L, List.of(userRole), List.of(), List.of(RequestableBy.AUTHORIZED));

			}

			@Test
			@DisplayName("Returns true when user is AUTHORIZED and has access to all orgUnits and itSystems")
			void returnsTrueWhenAuthorizedWithAllAccess() {
				// Arrange
				when(requestAuthorizedRoleService.accessibleOrgUnits(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToOrgUnits(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));
				when(requestAuthorizedRoleService.accessibleItsSystems(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToItSystems(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));

				// Act
				boolean result = requestService.canRequest(requestingUser, roleGroup, receivingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Returns true when user is AUTHORIZED and receiver's orgUnit is in constrained list")
			void returnsTrueWhenAuthorizedWithConstrainedOrgUnitAccess() {
				// Arrange
				when(requestAuthorizedRoleService.accessibleOrgUnits(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToOrgUnits(
								RequestAuthorizedRoleService.LimitedToType.CONSTRAINED, Set.of("orgunit-uuid")));
				when(requestAuthorizedRoleService.accessibleItsSystems(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToItSystems(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));

				// Act
				boolean result = requestService.canRequest(requestingUser, roleGroup, receivingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Returns false when user is AUTHORIZED but receiver's orgUnit is not in constrained list")
			void returnsFalseWhenAuthorizedButOrgUnitNotInConstrainedList() {
				// Arrange
				when(requestAuthorizedRoleService.accessibleOrgUnits(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToOrgUnits(
								RequestAuthorizedRoleService.LimitedToType.CONSTRAINED, Set.of("other-orgunit-uuid")));
				when(requestAuthorizedRoleService.accessibleItsSystems(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToItSystems(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));
				when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.of(requestingUser));

				// Act
				boolean result = requestService.canRequest(requestingUser, roleGroup, receivingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isFalse();
			}

			@Test
			@DisplayName("Returns false when user is AUTHORIZED but IT system is not in constrained list")
			void returnsFalseWhenAuthorizedButItSystemNotInConstrainedList() {
				// Arrange
				when(requestAuthorizedRoleService.accessibleOrgUnits(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToOrgUnits(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));
				when(requestAuthorizedRoleService.accessibleItsSystems(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToItSystems(
								RequestAuthorizedRoleService.LimitedToType.CONSTRAINED, Set.of(999L)));
				when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.of(requestingUser));

				// Act
				boolean result = requestService.canRequest(requestingUser, roleGroup, receivingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isFalse();
			}

			@Test
			@DisplayName("Returns true when all IT systems in role group are accessible")
			void returnsTrueWhenAllItSystemsInRoleGroupAreAccessible() {
				// Arrange
				ItSystem itSystem1 = createItSystem("it-system-1-uuid", List.of(), List.of());
				itSystem1.setId(1L);
				ItSystem itSystem2 = createItSystem("it-system-2-uuid", List.of(), List.of());
				itSystem2.setId(2L);
				UserRole userRole1 = createUserRole("role-1-uuid", itSystem1, List.of(), List.of());
				UserRole userRole2 = createUserRole("role-2-uuid", itSystem2, List.of(), List.of());
				RoleGroup multiSystemRoleGroup = createRoleGroup(2L, List.of(userRole1, userRole2), List.of(), List.of(RequestableBy.AUTHORIZED));

				when(requestAuthorizedRoleService.accessibleOrgUnits(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToOrgUnits(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));
				when(requestAuthorizedRoleService.accessibleItsSystems(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToItSystems(
								RequestAuthorizedRoleService.LimitedToType.CONSTRAINED, Set.of(1L, 2L)));

				// Act
				boolean result = requestService.canRequest(requestingUser, multiSystemRoleGroup, receivingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Returns false when not all IT systems in role group are accessible")
			void returnsFalseWhenNotAllItSystemsInRoleGroupAreAccessible() {
				// Arrange
				ItSystem itSystem1 = createItSystem("it-system-1-uuid", List.of(), List.of());
				itSystem1.setId(1L);
				ItSystem itSystem2 = createItSystem("it-system-2-uuid", List.of(), List.of());
				itSystem2.setId(2L);
				UserRole userRole1 = createUserRole("role-1-uuid", itSystem1, List.of(), List.of());
				UserRole userRole2 = createUserRole("role-2-uuid", itSystem2, List.of(), List.of());
				RoleGroup multiSystemRoleGroup = createRoleGroup(2L, List.of(userRole1, userRole2), List.of(), List.of(RequestableBy.AUTHORIZED));

				when(requestAuthorizedRoleService.accessibleOrgUnits(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToOrgUnits(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));
				when(requestAuthorizedRoleService.accessibleItsSystems(requestingUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToItSystems(
								RequestAuthorizedRoleService.LimitedToType.CONSTRAINED, Set.of(1L))); // Only has access to IT system 1
				when(userService.getOptionalByUserId("requester-user-id")).thenReturn(Optional.of(requestingUser));

				// Act
				boolean result = requestService.canRequest(requestingUser, multiSystemRoleGroup, receivingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("MANAGERORSUBSTITUTE permission")
		class ManagerOrSubstitutePermission {

			private RoleGroup roleGroup;

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);

				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(), List.of());
				roleGroup = createRoleGroup(1L, List.of(userRole), List.of(), List.of(RequestableBy.MANAGERORSUBSTITUTE));

			}

			@Test
			@DisplayName("Returns true when user is manager of receiver and permission is MANAGERORSUBSTITUTE")
			void returnsTrueWhenManagerOfReceiver() {
				// Arrange
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("manager-user-id");
				requestingUser.setUserId("manager-user-id");

				when(userService.getOptionalByUserId("manager-user-id")).thenReturn(Optional.of(requestingUser));
				when(userService.isManagerOrSubstituteManagerFor(requestingUser, receivingUser)).thenReturn(true);

				// Act
				boolean result = requestService.canRequest(requestingUser, roleGroup, receivingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Returns false when user is not manager of receiver")
			void returnsFalseWhenNotManagerOfReceiver() {
				// Arrange
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("non-manager-user-id");
				requestingUser.setUserId("non-manager-user-id");

				when(userService.getOptionalByUserId("non-manager-user-id")).thenReturn(Optional.of(requestingUser));
				when(userService.isManagerOrSubstituteManagerFor(requestingUser, receivingUser)).thenReturn(false);
				when(orgUnitService.getByAuthorizationManagerMatchingUser(requestingUser)).thenReturn(List.of());

				// Act
				boolean result = requestService.canRequest(requestingUser, roleGroup, receivingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("AUTHRESPONSIBLE permission")
		class AuthResponsiblePermission {

			private RoleGroup roleGroup;

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);

				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(), List.of());
				roleGroup = createRoleGroup(1L, List.of(userRole), List.of(), List.of(RequestableBy.AUTHRESPONSIBLE));
			}

			@Test
			@DisplayName("Returns true when user is AuthorizationResponsible and permission is AUTHRESPONSIBLE")
			void returnsTrueWhenAuthResponsible() {
				// Arrange
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("auth-resp-user-id");
				requestingUser.setUserId("auth-resp-user-id");

				when(userService.getOptionalByUserId("auth-resp-user-id")).thenReturn(Optional.of(requestingUser));
				when(userService.isManagerOrSubstituteManagerFor(requestingUser, receivingUser)).thenReturn(false);
				when(orgUnitService.getByAuthorizationManagerMatchingUser(requestingUser)).thenReturn(List.of(receiversOrgUnit));

				// Act
				boolean result = requestService.canRequest(requestingUser, roleGroup, receivingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Returns false when user is not AuthorizationResponsible")
			void returnsFalseWhenNotAuthResponsible() {
				// Arrange
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("regular-user-id");
				requestingUser.setUserId("regular-user-id");

				when(userService.getOptionalByUserId("regular-user-id")).thenReturn(Optional.of(requestingUser));
				when(userService.isManagerOrSubstituteManagerFor(requestingUser, receivingUser)).thenReturn(false);
				when(orgUnitService.getByAuthorizationManagerMatchingUser(requestingUser)).thenReturn(List.of());

				// Act
				boolean result = requestService.canRequest(requestingUser, roleGroup, receivingUser, receiversOrgUnit);

				// Assert
				assertThat(result).isFalse();
			}
		}
	}

	@Nested
	@DisplayName("canApprove for UserRole")
	class CanApproveUserRole {

		private User loggedInUser;
		private User receivingUser;
		private OrgUnit receiversOrgUnit;

		@BeforeEach
		void setUpCommonEntities() {
			loggedInUser = createUser("logged-in-user-uuid");
			loggedInUser.setUserId("logged-in-user-id");
			receivingUser = createUser("receiver-uuid");
			receiversOrgUnit = createOrgUnit("orgunit-uuid", null);
		}

		@Nested
		@DisplayName("Admin can always approve")
		class AdminCanAlwaysApprove {

			@BeforeEach
			void setUpAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(true);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("logged-in-user-id");
			}

			@Test
			@DisplayName("Admin can approve any request")
			void adminCanApproveAnyRequest() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isTrue();
			}
		}

		@Nested
		@DisplayName("User cannot approve their own request")
		class CannotApproveSelfRequest {

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("logged-in-user-id");
			}

			@Test
			@DisplayName("Returns false when user tries to approve their own request")
			void returnsFalseWhenApprovingOwnRequest() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(), List.of());
				RoleRequest request = createRoleRequest(loggedInUser, receiversOrgUnit, userRole);

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("Uses the role's ApprovableBy unless INHERIT")
		class UsesRolePermission {

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("logged-in-user-id");
			}

			@Test
			@DisplayName("Does not use IT system permission when role has explicit permission")
			void doesNotUseItSystemPermissionWhenRoleHasExplicit() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(ApprovableBy.AUTOMATIC), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.MANAGERORSUBSTITUTE), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("Uses IT system's ApprovableBy when role is INHERIT")
		class UsesItSystemPermission {

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("logged-in-user-id");
			}

			@Test
			@DisplayName("Inherits IT system permission when role is set to INHERIT")
			void inheritsItSystemPermission() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(ApprovableBy.AUTOMATIC), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.INHERIT), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);

				// Act
				boolean result = requestService.canApprove(request);

				// Assert - should be true because IT system has AUTOMATIC
				assertThat(result).isTrue();
			}
		}

		@Nested
		@DisplayName("Uses global ApprovableBy when both role and IT system are INHERIT")
		class UsesGlobalPermission {

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("logged-in-user-id");
			}

			@Test
			@DisplayName("Falls back to global permission when both role and IT system are INHERIT")
			void fallsBackToGlobalPermission() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(ApprovableBy.INHERIT), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.INHERIT), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);
				when(settingsService.getRolerequestApprover()).thenReturn(List.of(ApprovableBy.AUTOMATIC));

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isTrue();
			}
		}

		@Nested
		@DisplayName("AUTOMATIC approval")
		class AutomaticApproval {

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("logged-in-user-id");
			}

			@Test
			@DisplayName("Returns true when permission is AUTOMATIC")
			void returnsTrueWhenPermissionIsAutomatic() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.AUTOMATIC), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isTrue();
			}
		}

		@Nested
		@DisplayName("SYSTEMRESPONSIBLE permission")
		class SystemResponsiblePermission {

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("logged-in-user-id");
			}

			@Test
			@DisplayName("Returns true when user is system responsible for the IT system")
			void returnsTrueWhenUserIsSystemResponsible() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				itSystem.setAttestationResponsible(loggedInUser);
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.SYSTEMRESPONSIBLE), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Returns false when user is not system responsible for the IT system")
			void returnsFalseWhenUserIsNotSystemResponsible() {
				// Arrange
				User differentUser = createUser("different-user-uuid");
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				itSystem.setAttestationResponsible(differentUser);
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.SYSTEMRESPONSIBLE), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);
				when(orgUnitService.getByAuthorizationManagerMatchingUser(loggedInUser)).thenReturn(List.of());

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("AUTHORIZED permission with constraints")
		class AuthorizedPermission {

			@BeforeEach
			void setUpAuthorizedUser() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(Constants.ROLE_REQUESTAUTHORIZED)).thenReturn(true);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("logged-in-user-id");
			}

			@Test
			@DisplayName("Returns true when user is AUTHORIZED and has access to all orgUnits and itSystems")
			void returnsTrueWhenAuthorizedWithAllAccess() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				itSystem.setId(1L);
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.AUTHORIZED), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);
				when(requestAuthorizedRoleService.accessibleOrgUnits(loggedInUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToOrgUnits(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));
				when(requestAuthorizedRoleService.accessibleItsSystems(loggedInUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToItSystems(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Returns true when user is AUTHORIZED and receiver's orgUnit is in constrained list")
			void returnsTrueWhenAuthorizedWithConstrainedOrgUnitAccess() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				itSystem.setId(1L);
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.AUTHORIZED), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);
				when(requestAuthorizedRoleService.accessibleOrgUnits(loggedInUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToOrgUnits(
								RequestAuthorizedRoleService.LimitedToType.CONSTRAINED, Set.of("orgunit-uuid")));
				when(requestAuthorizedRoleService.accessibleItsSystems(loggedInUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToItSystems(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Returns false when user is AUTHORIZED but receiver's orgUnit is not in constrained list")
			void returnsFalseWhenAuthorizedButOrgUnitNotInConstrainedList() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				itSystem.setId(1L);
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.AUTHORIZED), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);
				when(requestAuthorizedRoleService.accessibleOrgUnits(loggedInUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToOrgUnits(
								RequestAuthorizedRoleService.LimitedToType.CONSTRAINED, Set.of("other-orgunit-uuid")));
				when(requestAuthorizedRoleService.accessibleItsSystems(loggedInUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToItSystems(
								RequestAuthorizedRoleService.LimitedToType.ALL, Set.of()));
				when(orgUnitService.getByAuthorizationManagerMatchingUser(loggedInUser)).thenReturn(List.of());

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isFalse();
			}

			@Test
			@DisplayName("Returns false when user is AUTHORIZED but IT system is not in constrained list")
			void returnsFalseWhenAuthorizedButItSystemNotInConstrainedList() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				itSystem.setId(1L);
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.AUTHORIZED), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);
				when(requestAuthorizedRoleService.accessibleItsSystems(loggedInUser))
						.thenReturn(new RequestAuthorizedRoleService.LimitedToItSystems(
								RequestAuthorizedRoleService.LimitedToType.CONSTRAINED, Set.of(999L)));
				when(orgUnitService.getByAuthorizationManagerMatchingUser(loggedInUser)).thenReturn(List.of());

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("MANAGERORSUBSTITUTE permission")
		class ManagerOrSubstitutePermission {

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("logged-in-user-id");
			}

			@Test
			@DisplayName("Returns true when user is manager of receiver's OrgUnit")
			void returnsTrueWhenUserIsManagerOfReceiversOrgUnit() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.MANAGERORSUBSTITUTE), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				loggedInUser.setManagerSubstitutes(List.of());
				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);
				when(orgUnitService.getManager(receiversOrgUnit)).thenReturn(loggedInUser);

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Returns true when user is substitute manager for receiver's OrgUnit")
			void returnsTrueWhenUserIsSubstituteForReceiversOrgUnit() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.MANAGERORSUBSTITUTE), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				// Create the manager of receiver's OrgUnit
				User managerOfReceiversOrgUnit = mock(User.class);
				when(managerOfReceiversOrgUnit.getUserId()).thenReturn("manager-id");

				// Create ManagerSubstitute entry where loggedInUser IS the substitute
				ManagerSubstitute substituteEntry = mock(ManagerSubstitute.class);
				when(substituteEntry.getSubstitute()).thenReturn(loggedInUser);

				// The manager has loggedInUser as a substitute
				when(managerOfReceiversOrgUnit.getManagerSubstitutes()).thenReturn(List.of(substituteEntry));

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);
				when(orgUnitService.getManager(receiversOrgUnit)).thenReturn(managerOfReceiversOrgUnit);

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Returns false when user is manager of a different OrgUnit")
			void returnsFalseWhenUserIsManagerOfDifferentOrgUnit() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.MANAGERORSUBSTITUTE), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				User differentManager = mock(User.class);
				when(differentManager.getUserId()).thenReturn("different-manager-id");

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);
				when(orgUnitService.getManager(receiversOrgUnit)).thenReturn(differentManager);
				when(differentManager.getManagerSubstitutes()).thenReturn(List.of());

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isFalse();
			}

			@Test
			@DisplayName("Returns false when user is substitute for a different OrgUnit")
			void returnsFalseWhenUserIsSubstituteForDifferentOrgUnit() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.MANAGERORSUBSTITUTE), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				// Create a mock manager for the receiver's OrgUnit
				User managerOfReceiversOrgUnit = mock(User.class);
				when(managerOfReceiversOrgUnit.getUserId()).thenReturn("receivers-manager-id");

				// Create a mock ManagerSubstitute where loggedInUser is NOT the substitute
				User someOtherSubstitute = mock(User.class);
				when(someOtherSubstitute.getUserId()).thenReturn("other-substitute-id");

				ManagerSubstitute substituteEntry = mock(ManagerSubstitute.class);
				when(substituteEntry.getSubstitute()).thenReturn(someOtherSubstitute);

				// The manager has substitutes, but loggedInUser is not one of them
				when(managerOfReceiversOrgUnit.getManagerSubstitutes()).thenReturn(List.of(substituteEntry));

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);
				when(orgUnitService.getManager(receiversOrgUnit)).thenReturn(managerOfReceiversOrgUnit);

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isFalse();
			}

			@Test
			@DisplayName("Returns false when user is neither manager nor substitute of any OrgUnit")
			void returnsFalseWhenUserIsNeitherManagerNorSubstitute() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.MANAGERORSUBSTITUTE), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				// Create a mock manager who is NOT the logged-in user
				User someOtherManager = mock(User.class);
				when(someOtherManager.getUserId()).thenReturn("other-manager-id");
				when(someOtherManager.getManagerSubstitutes()).thenReturn(List.of());

				// Set userId on the real object (no stubbing needed)
				loggedInUser.setUserId("logged-in-user-id");

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);
				when(orgUnitService.getManager(receiversOrgUnit)).thenReturn(someOtherManager);

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isFalse();
			}
		}

		@Nested
		@DisplayName("AUTHRESPONSIBLE permission")
		class AuthResponsiblePermission {

			@BeforeEach
			void setUpNonAdmin() {
				securityUtilMock.when(SecurityUtil::hasDirectAdminRole).thenReturn(false);
				securityUtilMock.when(() -> SecurityUtil.hasRole(any())).thenReturn(false);
				securityUtilMock.when(SecurityUtil::getUserId).thenReturn("logged-in-user-id");
			}

			@Test
			@DisplayName("Returns true when user is AuthorizationResponsible")
			void returnsTrueWhenUserIsAuthResponsible() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.AUTHRESPONSIBLE), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);
				when(orgUnitService.getByAuthorizationManagerMatchingUser(loggedInUser)).thenReturn(List.of(receiversOrgUnit));

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isTrue();
			}

			@Test
			@DisplayName("Returns false when user is not AuthorizationResponsible")
			void returnsFalseWhenUserIsNotAuthResponsible() {
				// Arrange
				ItSystem itSystem = createItSystem("it-system-uuid", List.of(), List.of());
				UserRole userRole = createUserRole("role-uuid", itSystem, List.of(ApprovableBy.AUTHRESPONSIBLE), List.of());
				RoleRequest request = createRoleRequest(receivingUser, receiversOrgUnit, userRole);

				when(userService.getByUserId("logged-in-user-id")).thenReturn(loggedInUser);
				when(orgUnitService.getByAuthorizationManagerMatchingUser(loggedInUser)).thenReturn(List.of());

				// Act
				boolean result = requestService.canApprove(request);

				// Assert
				assertThat(result).isFalse();
			}
		}
	}
}
