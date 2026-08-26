package dk.digitalidentity.rc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.saml.service.model.SamlGrantedAuthority;
import dk.digitalidentity.saml.service.model.TokenUser;

/**
 * Covers the shared decision of whether the current user may assign, edit or remove an assignment.
 * These two methods are the single source of truth behind the action icons on the user pages and the
 * role pages, so a "Rolletildeler" can maintain exactly what it is allowed to create - see GitLab #71.
 *
 * The methods under test touch none of the injected collaborators, so leaving them unmocked is fine.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserRoleAssignabilityTest {
	private static final long ALLOWED_IT_SYSTEM = 1L;
	private static final long OTHER_IT_SYSTEM = 2L;

	@InjectMocks
	private UserRoleService userRoleService;

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Nested
	@DisplayName("A user role assignment may be maintained")
	class UserRoles {

		@Test
		@DisplayName("by an administrator, even outside the constraint")
		void administrator() {
			authenticateWith(Constants.ROLE_ADMINISTRATOR);

			assertThat(userRoleService.isUserRoleAssignable(userRole(ALLOWED_IT_SYSTEM), true, nothingAllowed())).isTrue();
		}

		@Test
		@DisplayName("by a role assigner when the role's IT system is inside its constraint")
		void assignerInsideConstraint() {
			authenticateWith(Constants.ROLE_OU_ASSIGNER);

			assertThat(userRoleService.isUserRoleAssignable(userRole(ALLOWED_IT_SYSTEM), true, onlyAllowing(ALLOWED_IT_SYSTEM))).isTrue();
		}

		@Test
		@DisplayName("but not by a role assigner when the role's IT system is outside its constraint")
		void assignerOutsideConstraint() {
			authenticateWith(Constants.ROLE_OU_ASSIGNER);

			assertThat(userRoleService.isUserRoleAssignable(userRole(OTHER_IT_SYSTEM), true, onlyAllowing(ALLOWED_IT_SYSTEM))).isFalse();
		}

		@Test
		@DisplayName("but not by a user without the assign permission at all")
		void withoutAssignPermission() {
			authenticateWith(Constants.ROLE_READ_ACCESS);

			assertThat(userRoleService.isUserRoleAssignable(userRole(ALLOWED_IT_SYSTEM), true, nothingAllowed())).isFalse();
		}

		@Test
		@DisplayName("but never when the assignment is inherited rather than direct")
		void inheritedAssignment() {
			authenticateWith(Constants.ROLE_ADMINISTRATOR);

			assertThat(userRoleService.isUserRoleAssignable(userRole(ALLOWED_IT_SYSTEM), false, everythingAllowed())).isFalse();
		}

		@Test
		@DisplayName("but never on a read only role, not even by an administrator")
		void readOnlyRole() {
			authenticateWith(Constants.ROLE_ADMINISTRATOR);

			UserRole role = userRole(ALLOWED_IT_SYSTEM);
			role.setReadOnly(true);

			assertThat(userRoleService.isUserRoleAssignable(role, true, everythingAllowed())).isFalse();
		}

		@Test
		@DisplayName("and still on a role in a read only AD IT system, so an existing assignment can be removed")
		void readOnlyAdItSystem() {
			authenticateWith(Constants.ROLE_ADMINISTRATOR);

			// this flag also gates the delete icon and the bulk-remove checkbox, and the remove endpoints
			// accept these roles - only creating a new assignment is blocked, by isAssignableRole
			assertThat(userRoleService.isUserRoleAssignable(adRole(true), true, everythingAllowed())).isTrue();
		}

		@Test
		@DisplayName("but not by a role assigner on an internal role catalogue role, which stays administrator only")
		void internalRoleCatalogueRole() {
			authenticateWith(Constants.ROLE_OU_ASSIGNER);

			assertThat(userRoleService.isUserRoleAssignable(internalRole(), true, everythingAllowed())).isFalse();
		}

		@Test
		@DisplayName("on an internal role catalogue role by an administrator")
		void internalRoleCatalogueRoleAsAdministrator() {
			authenticateWith(Constants.ROLE_ADMINISTRATOR);

			assertThat(userRoleService.isUserRoleAssignable(internalRole(), true, everythingAllowed())).isTrue();
		}
	}

	@Nested
	@DisplayName("Whether a role can be assigned at all")
	class AssignableRole {

		@Test
		@DisplayName("an ordinary role can be assigned, and has no blocking reason")
		void ordinaryRole() {
			UserRole role = userRole(ALLOWED_IT_SYSTEM);

			assertThat(userRoleService.isAssignableRole(role)).isTrue();
			assertThat(userRoleService.assignmentBlockedReason(role)).isEmpty();
		}

		@Test
		@DisplayName("a read only role cannot, and the reason names the role")
		void readOnlyRole() {
			UserRole role = userRole(ALLOWED_IT_SYSTEM);
			role.setName("Skrivebeskyttet rolle");
			role.setReadOnly(true);

			assertThat(userRoleService.isAssignableRole(role)).isFalse();
			assertThat(userRoleService.assignmentBlockedReason(role)).get().asString()
					.contains("Skrivebeskyttet rolle")
					.contains("skrivebeskyttet");
		}

		@Test
		@DisplayName("a role in a read only AD IT system cannot, because such a system is never provisioned")
		void readOnlyAdItSystem() {
			UserRole role = adRole(true);

			assertThat(userRoleService.isAssignableRole(role)).isFalse();
			assertThat(userRoleService.assignmentBlockedReason(role)).get().asString()
					.contains("AD_DMP_Miljoportal");
		}

		@Test
		@DisplayName("a role in a read only IT system that is not AD can, since only AD provisioning is skipped")
		void readOnlyNonAdItSystem() {
			UserRole role = userRole(ALLOWED_IT_SYSTEM);
			role.getItSystem().setSystemType(ItSystemType.SAML);
			role.getItSystem().setReadonly(true);

			assertThat(userRoleService.isAssignableRole(role)).isTrue();
		}
	}

	@Nested
	@DisplayName("A role group assignment may be maintained")
	class RoleGroups {

		@Test
		@DisplayName("by a role assigner when every contained role's IT system is inside its constraint")
		void allItSystemsInsideConstraint() {
			authenticateWith(Constants.ROLE_OU_ASSIGNER);

			RoleGroup group = roleGroup(userRole(ALLOWED_IT_SYSTEM));

			assertThat(userRoleService.isRoleGroupAssignable(group, true, onlyAllowing(ALLOWED_IT_SYSTEM))).isTrue();
		}

		@Test
		@DisplayName("but not when only some of the contained roles' IT systems are inside its constraint")
		void someItSystemsOutsideConstraint() {
			authenticateWith(Constants.ROLE_OU_ASSIGNER);

			RoleGroup group = roleGroup(userRole(ALLOWED_IT_SYSTEM), userRole(OTHER_IT_SYSTEM));

			assertThat(userRoleService.isRoleGroupAssignable(group, true, onlyAllowing(ALLOWED_IT_SYSTEM))).isFalse();
		}

		@Test
		@DisplayName("but never when the assignment is inherited rather than direct")
		void inheritedAssignment() {
			authenticateWith(Constants.ROLE_ADMINISTRATOR);

			RoleGroup group = roleGroup(userRole(ALLOWED_IT_SYSTEM));

			assertThat(userRoleService.isRoleGroupAssignable(group, false, everythingAllowed())).isFalse();
		}

		@Test
		@DisplayName("but not by a role assigner when the group contains an internal role catalogue role")
		void containsInternalRoleCatalogueRole() {
			authenticateWith(Constants.ROLE_OU_ASSIGNER);

			RoleGroup group = roleGroup(userRole(ALLOWED_IT_SYSTEM), internalRole());

			assertThat(userRoleService.isRoleGroupAssignable(group, true, everythingAllowed())).isFalse();
		}

		@Test
		@DisplayName("when the group contains an internal role catalogue role and the user is an administrator")
		void containsInternalRoleCatalogueRoleAsAdministrator() {
			authenticateWith(Constants.ROLE_ADMINISTRATOR);

			RoleGroup group = roleGroup(userRole(ALLOWED_IT_SYSTEM), internalRole());

			assertThat(userRoleService.isRoleGroupAssignable(group, true, everythingAllowed())).isTrue();
		}
	}

	private static UserRole userRole(long itSystemId) {
		ItSystem itSystem = new ItSystem();
		itSystem.setId(itSystemId);
		itSystem.setIdentifier("it-system-" + itSystemId);

		UserRole role = new UserRole();
		role.setItSystem(itSystem);
		return role;
	}

	private static UserRole adRole(boolean readonly) {
		UserRole role = userRole(ALLOWED_IT_SYSTEM);
		role.getItSystem().setName("AD_DMP_Miljoportal");
		role.getItSystem().setSystemType(ItSystemType.AD);
		role.getItSystem().setReadonly(readonly);
		return role;
	}

	private static UserRole internalRole() {
		UserRole role = userRole(ALLOWED_IT_SYSTEM);
		role.getItSystem().setIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
		return role;
	}

	private static RoleGroup roleGroup(UserRole... userRoles) {
		RoleGroup group = new RoleGroup();
		List<RoleGroupUserRoleAssignment> assignments = new ArrayList<>();
		for (UserRole role : userRoles) {
			RoleGroupUserRoleAssignment assignment = new RoleGroupUserRoleAssignment();
			assignment.setRoleGroup(group);
			assignment.setUserRole(role);
			assignments.add(assignment);
		}
		group.setUserRoleAssignments(assignments);
		return group;
	}

	private static PermissionConstraint everythingAllowed() {
		return new PermissionConstraint(null, null);
	}

	private static PermissionConstraint nothingAllowed() {
		return new PermissionConstraint(Set.of(), Set.of());
	}

	private static PermissionConstraint onlyAllowing(long itSystemId) {
		return new PermissionConstraint(Set.of(itSystemId), null);
	}

	private static void authenticateWith(String... roles) {
		List<GrantedAuthority> authorities = Arrays.stream(roles)
				.map(SamlGrantedAuthority::new)
				.map(GrantedAuthority.class::cast)
				.toList();

		Map<String, Object> attributes = new HashMap<>();
		attributes.put("ATTRIBUTE_USER_UUID", "test-user-uuid");
		TokenUser tokenUser = TokenUser.builder()
				.username("test-user")
				.attributes(attributes)
				.build();

		TestingAuthenticationToken auth = new TestingAuthenticationToken("test-user", null, authorities);
		auth.setDetails(tokenUser);
		SecurityContextHolder.getContext().setAuthentication(auth);
	}
}
