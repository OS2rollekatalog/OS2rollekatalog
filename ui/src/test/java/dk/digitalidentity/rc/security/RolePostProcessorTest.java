package dk.digitalidentity.rc.security;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManagerDelegate;
import dk.digitalidentity.rc.dao.model.ReportTemplate;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.ManagerDelegateService;
import dk.digitalidentity.rc.service.NotificationService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.ReportTemplateService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.permission.PermissionService;
import dk.digitalidentity.samlmodule.model.SamlGrantedAuthority;
import dk.digitalidentity.samlmodule.model.TokenUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.security.MockFactory.createUserRoleWithSystemRole;
import static dk.digitalidentity.rc.mockfactory.security.MockFactory.mockTokenUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RolePostProcessorTest {

	@Mock
	private UserService userService;
	@Mock
	private AuditLogger auditLogger;
	@Mock
	private SettingsService settingsService;
	@Mock
	private ItSystemService itSystemService;
	@Mock
	private ReportTemplateService reportTemplateService;
	@Mock
	private NotificationService notificationService;
	@Mock
	private OrgUnitService orgUnitService;
	@Mock
	private ManagerDelegateService managerDelegateService;
	@Mock
	private AccessConstraintService accessConstraintService;
	@Mock
	private PermissionService permissionService;
	@Mock
	private AssignmentService assignmentService;

	@InjectMocks
	private RolePostProcessor rolePostProcessor;

	private ItSystem testRoleCatalogue;
	private User testUser;

	@BeforeEach
	void setUp() {
		testRoleCatalogue = new ItSystem();
		testRoleCatalogue.setId(1L);
		testRoleCatalogue.setIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
		testUser = createUser("test-user-uuid");

	}

	@Nested
	@DisplayName("AuditLog")
	class AuditLog {
		private TokenUser tokenUser;

		@BeforeEach
		void setUp() {
			List<String> roles = List.of();
			tokenUser = mockTokenUser("tst", "Test user", "test-user-uuid", roles, List.of());

			when(userService.getByUserId(tokenUser.getUsername()))
				.thenReturn(testUser);

			when(itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER))
				.thenReturn(List.of(testRoleCatalogue));

			Map<Section, Map<Permission, PermissionConstraint>> permissionMap = Map.of();
			when(accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue))
				.thenReturn(permissionMap);

			when(userService.getByUserId(tokenUser.getUsername()))
				.thenReturn(testUser);
		}

		@Test
		@DisplayName("Login is logged by AuditLog")
		void loginIsLoggedByAuditLog() {
			// Arrange
			ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

			// Act
			rolePostProcessor.process(tokenUser);

			// Assert
			verify(auditLogger).log(captor.capture(), any(EventType.class));
			User user = captor.getValue();
			assertThat(user.getUuid()).isEqualTo(testUser.getUuid());
		}

	}


	@Nested
	@DisplayName("Authorities")
	class authorities {
		@Nested
		@DisplayName("Hierarchical roles")
		class hierarchicalRoles {
			private TokenUser tokenUser;

			@BeforeEach
			void setUp() {
				List<String> roles = List.of();
				tokenUser = mockTokenUser("tst", "Test user", "test-user-uuid", roles, List.of());

				when(userService.getByUserId(tokenUser.getUsername()))
					.thenReturn(testUser);

				when(itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER))
					.thenReturn(List.of(testRoleCatalogue));

				Map<Section, Map<Permission, PermissionConstraint>> permissionMap = Map.of();
				when(accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue))
					.thenReturn(permissionMap);
			}

			@Test
			@DisplayName("User with administration role gets Admin authorities")
			void userGetsAdminAuthority() {
				// Arrange
				String testSystemIdentifier = Constants.ROLE_ADMINISTRATOR_ID;
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_ADMINISTRATOR,
					Constants.ROLE_REPORT_ACCESS,
					Constants.ROLE_KLE_ADMINISTRATOR,
					Constants.ROLE_AUDITLOG,
					Constants.ROLE_USER_ASSIGNER,
					Constants.ROLE_OU_ASSIGNER,
					Constants.ROLE_READ_ACCESS);

				UserRole userRole = createUserRoleWithSystemRole(testSystemIdentifier);
				when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
					.thenReturn(Set.of(userRole));

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities);
			}


			@Test
			@DisplayName("User with Global assigner gets correct authorities")
			void userWithGlobalAssigner() {
				// Arrange
				String testSystemIdentifier = Constants.ROLE_GLOBAL_ASSIGNER_ID;
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_AUDITLOG,
					Constants.ROLE_USER_ASSIGNER,
					Constants.ROLE_OU_ASSIGNER,
					Constants.ROLE_READ_ACCESS);
				List<String> notAcceptedAuthorities = List.of(
					Constants.ROLE_ADMINISTRATOR,
					Constants.ROLE_REPORT_ACCESS,
					Constants.ROLE_KLE_ADMINISTRATOR
				);

				UserRole userRole = createUserRoleWithSystemRole(testSystemIdentifier);
				when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
					.thenReturn(Set.of(userRole));

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities)
					.doesNotContainAnyElementsOf(notAcceptedAuthorities);
			}

			@Test
			@DisplayName("User with User assigner gets correct authorities")
			void userWithUserAssigner() {
				// Arrange
				String testSystemIdentifier = Constants.ROLE_USER_ASSIGNER_ID;
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_USER_ASSIGNER,
					Constants.ROLE_READ_ACCESS);
				List<String> notAcceptedAuthorities = List.of(
					Constants.ROLE_ADMINISTRATOR,
					Constants.ROLE_REPORT_ACCESS,
					Constants.ROLE_KLE_ADMINISTRATOR,
					Constants.ROLE_AUDITLOG,
					Constants.ROLE_OU_ASSIGNER
				);

				UserRole userRole = createUserRoleWithSystemRole(testSystemIdentifier);
				when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
					.thenReturn(Set.of(userRole));

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities)
					.doesNotContainAnyElementsOf(notAcceptedAuthorities);
			}

			@Test
			@DisplayName("User with OU assigner gets correct authorities")
			void userWithOUAssigner() {
				// Arrange
				String testSystemIdentifier = Constants.ROLE_OU_ASSIGNER_ID;
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_OU_ASSIGNER,
					Constants.ROLE_READ_ACCESS);
				List<String> notAcceptedAuthorities = List.of(
					Constants.ROLE_ADMINISTRATOR,
					Constants.ROLE_REPORT_ACCESS,
					Constants.ROLE_KLE_ADMINISTRATOR,
					Constants.ROLE_AUDITLOG,
					Constants.ROLE_USER_ASSIGNER
				);

				UserRole userRole = createUserRoleWithSystemRole(testSystemIdentifier);
				when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
					.thenReturn(Set.of(userRole));

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities)
					.doesNotContainAnyElementsOf(notAcceptedAuthorities);
			}

			@Test
			@DisplayName("User with Read role gets correct authorities")
			void userWithReadRole() {
				// Arrange
				String testSystemIdentifier = Constants.ROLE_READ_ACCESS_ID;
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_READ_ACCESS);
				List<String> notAcceptedAuthorities = List.of(
					Constants.ROLE_ADMINISTRATOR,
					Constants.ROLE_REPORT_ACCESS,
					Constants.ROLE_KLE_ADMINISTRATOR,
					Constants.ROLE_AUDITLOG,
					Constants.ROLE_USER_ASSIGNER,
					Constants.ROLE_OU_ASSIGNER
				);

				UserRole userRole = createUserRoleWithSystemRole(testSystemIdentifier);
				when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
					.thenReturn(Set.of(userRole));

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities)
					.doesNotContainAnyElementsOf(notAcceptedAuthorities);
			}

			@Test
			@DisplayName("User with no hierarchy role does not get authorities")
			void userWithNoRole() {
				// Arrange
				String testSystemIdentifier = "not-recognized-identifier";
				List<String> acceptedAuthorities = List.of(
				);
				List<String> notAcceptedAuthorities = List.of(
					Constants.ROLE_ADMINISTRATOR,
					Constants.ROLE_REPORT_ACCESS,
					Constants.ROLE_KLE_ADMINISTRATOR,
					Constants.ROLE_AUDITLOG,
					Constants.ROLE_USER_ASSIGNER,
					Constants.ROLE_OU_ASSIGNER,
					Constants.ROLE_READ_ACCESS
				);

				UserRole userRole = createUserRoleWithSystemRole(testSystemIdentifier);
				when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
					.thenReturn(Set.of(userRole));

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities)
					.doesNotContainAnyElementsOf(notAcceptedAuthorities);
			}


			@Test
			@DisplayName("User with several roles get combines authorities")
			void userWithSeveralRoles() {
				// Arrange
				String testSystemIdentifier1 = Constants.ROLE_USER_ASSIGNER_ID;
				String testSystemIdentifier2 = Constants.ROLE_OU_ASSIGNER_ID;
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_USER_ASSIGNER,
					Constants.ROLE_OU_ASSIGNER,
					Constants.ROLE_READ_ACCESS
				);
				List<String> notAcceptedAuthorities = List.of(
					Constants.ROLE_ADMINISTRATOR,
					Constants.ROLE_REPORT_ACCESS,
					Constants.ROLE_KLE_ADMINISTRATOR,
					Constants.ROLE_AUDITLOG
				);

				UserRole userRole1 = createUserRoleWithSystemRole(testSystemIdentifier1);
				UserRole userRole2 = createUserRoleWithSystemRole(testSystemIdentifier2);
				when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
					.thenReturn(Set.of(userRole1, userRole2));

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities)
					.doesNotContainAnyElementsOf(notAcceptedAuthorities);
			}

			@Test
			@DisplayName("User with no userroles gets no authorities")
			void userWithNoUserroles() {
				// Arrange

				List<String> acceptedAuthorities = List.of(
				);
				List<String> notAcceptedAuthorities = List.of(
					Constants.ROLE_ADMINISTRATOR,
					Constants.ROLE_REPORT_ACCESS,
					Constants.ROLE_KLE_ADMINISTRATOR,
					Constants.ROLE_AUDITLOG,
					Constants.ROLE_USER_ASSIGNER,
					Constants.ROLE_OU_ASSIGNER,
					Constants.ROLE_READ_ACCESS
				);

				when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
					.thenReturn(Set.of());

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities)
					.doesNotContainAnyElementsOf(notAcceptedAuthorities);
			}

		}

		@Nested
		@DisplayName("Non-Hierarchical roles")
		class nonHierarchicalRoles {

			private TokenUser tokenUser;

			@BeforeEach
			void setUp() {
				List<String> roles = List.of();
				tokenUser = mockTokenUser("tst", "Test user", "test-user-uuid", roles, List.of());

				when(userService.getByUserId(tokenUser.getUsername()))
					.thenReturn(testUser);

				when(itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER))
					.thenReturn(List.of(testRoleCatalogue));

				Map<Section, Map<Permission, PermissionConstraint>> permissionMap = Map.of();
				when(accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue))
					.thenReturn(permissionMap);
			}

			@Test
			@DisplayName("User with KLE admin role gets KLE admin authorities")
			void userGetsAdminAuthority() {
				// Arrange
				String testSystemIdentifier = Constants.ROLE_KLE_ADMINISTRATOR_ID;
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_KLE_ADMINISTRATOR,
					Constants.ROLE_READ_ACCESS);
				List<String> notAcceptedAuthorities = List.of(
					Constants.ROLE_IT_SYSTEM_RESPONSIBLE,
					Constants.ROLE_TEMPLATE_ACCESS,
					Constants.ROLE_REPORT_ACCESS,
					Constants.ROLE_REQUESTAUTHORIZED,
					Constants.ROLE_ATTESTATION_ADMINISTRATOR
				);

				UserRole userRole = createUserRoleWithSystemRole(testSystemIdentifier);
				when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
					.thenReturn(Set.of(userRole));

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities)
					.doesNotContainAnyElementsOf(notAcceptedAuthorities);
			}

			@Test
			@DisplayName("User with Attestation admin role gets attestation admin authorities")
			void userGetsAttestationAuthority() {
				// Arrange
				String testSystemIdentifier = Constants.ROLE_ATTESTATION_ADMINISTRATOR_ID;
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_ATTESTATION_ADMINISTRATOR
				);
				List<String> notAcceptedAuthorities = List.of(
					Constants.ROLE_KLE_ADMINISTRATOR,
					Constants.ROLE_IT_SYSTEM_RESPONSIBLE,
					Constants.ROLE_TEMPLATE_ACCESS,
					Constants.ROLE_REPORT_ACCESS,
					Constants.ROLE_REQUESTAUTHORIZED,
					Constants.ROLE_READ_ACCESS
				);

				UserRole userRole = createUserRoleWithSystemRole(testSystemIdentifier);
				when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
					.thenReturn(Set.of(userRole));

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities)
					.doesNotContainAnyElementsOf(notAcceptedAuthorities);
			}

			@Test
			@DisplayName("User with Requestauthorized role gets correct authorities")
			void userWithRequestAuthorizedRole() {
				// Arrange
				String testSystemIdentifier = Constants.ROLE_REQUESTAUTHORIZED;
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_REQUESTAUTHORIZED
				);
				List<String> notAcceptedAuthorities = List.of(
					Constants.ROLE_ATTESTATION_ADMINISTRATOR,
					Constants.ROLE_KLE_ADMINISTRATOR,
					Constants.ROLE_IT_SYSTEM_RESPONSIBLE,
					Constants.ROLE_TEMPLATE_ACCESS,
					Constants.ROLE_REPORT_ACCESS,
					Constants.ROLE_READ_ACCESS
				);

				UserRole userRole = createUserRoleWithSystemRole(testSystemIdentifier);
				when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
					.thenReturn(Set.of(userRole));

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities)
					.doesNotContainAnyElementsOf(notAcceptedAuthorities);
			}

			@Test
			@DisplayName("User with Report role gets correct authorities")
			void userWithReportRole() {
				// Arrange
				String testSystemIdentifier = Constants.ROLE_REPORT_ACCESS_ID;
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_REPORT_ACCESS,
					Constants.ROLE_READ_ACCESS
				);
				List<String> notAcceptedAuthorities = List.of(
					Constants.ROLE_REQUESTAUTHORIZED,
					Constants.ROLE_ATTESTATION_ADMINISTRATOR,
					Constants.ROLE_KLE_ADMINISTRATOR,
					Constants.ROLE_IT_SYSTEM_RESPONSIBLE,
					Constants.ROLE_TEMPLATE_ACCESS
				);

				UserRole userRole = createUserRoleWithSystemRole(testSystemIdentifier);
				when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
					.thenReturn(Set.of(userRole));

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities)
					.doesNotContainAnyElementsOf(notAcceptedAuthorities);
			}

			@Test
			@DisplayName("User who is It system responsible gets correct authorities")
			void userItSystemResponsibleRole() {
				// Arrange
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_IT_SYSTEM_RESPONSIBLE
				);
				List<String> notAcceptedAuthorities = List.of(
					Constants.ROLE_REQUESTAUTHORIZED,
					Constants.ROLE_ATTESTATION_ADMINISTRATOR,
					Constants.ROLE_KLE_ADMINISTRATOR,
					Constants.ROLE_TEMPLATE_ACCESS,
					Constants.ROLE_REPORT_ACCESS,
					Constants.ROLE_READ_ACCESS
				);

				when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
					.thenReturn(Set.of());

				when(itSystemService.findByAttestationResponsible(testUser))
					.thenReturn(List.of(testRoleCatalogue));

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert


				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities)
					.doesNotContainAnyElementsOf(notAcceptedAuthorities);
			}

			@Test
			@DisplayName("User with assigned Template gets special authorities")
			void userWithAssignedTemplate() {
				// Arrange
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_TEMPLATE_ACCESS
				);
				List<String> notAcceptedAuthorities = List.of(
					Constants.ROLE_REPORT_ACCESS,
					Constants.ROLE_READ_ACCESS,
					Constants.ROLE_REQUESTAUTHORIZED,
					Constants.ROLE_ATTESTATION_ADMINISTRATOR,
					Constants.ROLE_KLE_ADMINISTRATOR,
					Constants.ROLE_IT_SYSTEM_RESPONSIBLE
				);

				when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
					.thenReturn(Set.of());

				when(reportTemplateService.getByUser(testUser))
					.thenReturn(List.of(new ReportTemplate()));

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities)
					.doesNotContainAnyElementsOf(notAcceptedAuthorities);
			}
		}

		@Nested
		@DisplayName("Manager roles")
		class managerRoles {

			private TokenUser tokenUser;

			@BeforeEach
			void setUp() {
				List<String> roles = List.of();
				tokenUser = mockTokenUser("tst", "Test user", "test-user-uuid", roles, List.of());

				when(userService.getByUserId(tokenUser.getUsername()))
					.thenReturn(testUser);

				when(itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER))
					.thenReturn(List.of(testRoleCatalogue));

				Map<Section, Map<Permission, PermissionConstraint>> permissionMap = Map.of();
				when(accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue))
					.thenReturn(permissionMap);

				when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
					.thenReturn(Set.of());
			}

			@Test
			@DisplayName("Managers are marked")
			void managersAreMarked() {
				// Arrange
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_MANAGER
				);

				when(userService.isManager(testUser))
					.thenReturn(true);

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities);

			}

			@Test
			@DisplayName("Substitutes for any managers are marked")
			void substitutesAreMarked() {
				// Arrange
				final String ATTRIBUTE_SUBSTITUTE_FOR = "ATTRIBUTE_SUBSTITUTE_FOR";
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_SUBSTITUTE
				);

				User manager = new User();
				manager.setUuid("manager-user-uuid");
				when(userService.getSubstitutesManager(testUser))
					.thenReturn(List.of(manager));

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities);

				String[] managerUuids = (String[]) tokenUser.getAttributes().getOrDefault(ATTRIBUTE_SUBSTITUTE_FOR, new String[0]);
				assertThat(managerUuids)
					.containsExactly(manager.getUuid());
			}

			@Test
			@DisplayName("Delegates for any managers are marked")
			void delegatesAreMarked() {
				// Arrange
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_MANAGER_SUBSTITUDE
				);

				User manager = new User();
				manager.setUuid("manager-user-uuid");
				ManagerDelegate managerDelegate = new ManagerDelegate();
				managerDelegate.setManager(manager);
				when(managerDelegateService.getByDelegate(testUser))
					.thenReturn(List.of(managerDelegate));

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities);
			}

			@Test
			@DisplayName("Managers get requester role for Request/Approve")
			void requestApproveManagerAuthority() {
				// Arrange
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_REQUESTER
				);

				User manager = new User();
				manager.setUuid("manager-user-uuid");
				when(userService.getSubstitutesManager(testUser))
					.thenReturn(List.of(manager));
				when(settingsService.isRequestApproveEnabled())
					.thenReturn(true);

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities);
			}

			@Test
			@DisplayName("Substitutes get requester role for Request/Approve")
			void requestApproveSubstituteAuthority() {
				// Arrange
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_REQUESTER
				);

				when(orgUnitService.getByAuthorizationManagerMatchingUser(testUser))
					.thenReturn(List.of(createOrgUnit("mock-ou-uuid", null)));

				when(settingsService.isRequestApproveEnabled())
					.thenReturn(true);

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities);
			}
			@Test
			@DisplayName("Delegated get requester role for Request/Approve")
			void requestApproveDelegateAuthority() {
				// Arrange
				List<String> acceptedAuthorities = List.of(
					Constants.ROLE_REQUESTER
				);

				when(orgUnitService.getByManagerMatchingUser(testUser))
					.thenReturn(List.of(createOrgUnit("mock-ou-uuid", null)));
				when(settingsService.isRequestApproveEnabled())
					.thenReturn(true);

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.containsAll(acceptedAuthorities);
			}

			@Test
			@DisplayName("Regular users does not get requester role for Request/Approve")
			void requestApproveRegularUserHasNoAuthority() {
				// Arrange
				when(settingsService.isRequestApproveEnabled())
					.thenReturn(true);

				// Act
				rolePostProcessor.process(tokenUser);

				// Assert
				List<String> authorities = tokenUser.getAuthorities().stream().map(SamlGrantedAuthority::getAuthority).toList();
				assertThat(authorities)
					.isEmpty();
			}
		}
	}
}
