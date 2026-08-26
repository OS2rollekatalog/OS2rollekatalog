package dk.digitalidentity.rc.controller.mvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.OrgUnitWithRole2;
import dk.digitalidentity.rc.service.model.RoleAssignedToOrgUnitDTO;
import dk.digitalidentity.saml.service.model.SamlGrantedAuthority;
import dk.digitalidentity.saml.service.model.TokenUser;

/**
 * Covers the two tabs on a user role: the edit/delete icons on the org unit tab, which must additionally
 * respect the org unit half of the assign constraint, and the button that adds an assignment on both tabs,
 * which must not be offered for a role that cannot be assigned at all. Whether the role itself may be
 * maintained is decided by UserRoleService and covered by UserRoleAssignabilityTest.
 */
@ExtendWith(MockitoExtension.class)
class UserRoleControllerTest {
	private static final long ROLE_ID = 1L;
	private static final String ASSIGNED_OU = "ou-assigned";
	private static final String OTHER_OU = "ou-other";

	@Mock
	private UserRoleService userRoleService;

	@Mock
	private OrgUnitService orgUnitService;

	@Mock
	private UserPermissionContext userPermissionContext;

	@Mock
	private AssignmentService assignmentService;

	@InjectMocks
	private UserRoleController userRoleController;

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Nested
	@DisplayName("The org unit tab shows edit/delete")
	class OrgUnitTab {

		@Test
		@DisplayName("to a role assigner for an org unit inside its constraint")
		void assignerInsideConstraint() {
			authenticateWith(Constants.ROLE_OU_ASSIGNER);

			assertThat(renderOrgUnitTab(true, onlyAllowing(ASSIGNED_OU))).isTrue();
		}

		@Test
		@DisplayName("but not for an org unit outside its constraint, even when the role itself may be assigned")
		void assignerOutsideConstraint() {
			authenticateWith(Constants.ROLE_OU_ASSIGNER);

			assertThat(renderOrgUnitTab(true, onlyAllowing(OTHER_OU))).isFalse();
		}

		@Test
		@DisplayName("but not when the role itself may not be assigned, even for an org unit inside its constraint")
		void roleNotAssignable() {
			authenticateWith(Constants.ROLE_OU_ASSIGNER);

			assertThat(renderOrgUnitTab(false, onlyAllowing(ASSIGNED_OU))).isFalse();
		}

		@Test
		@DisplayName("to an administrator regardless of the org unit constraint")
		void administrator() {
			authenticateWith(Constants.ROLE_ADMINISTRATOR);

			assertThat(renderOrgUnitTab(true, onlyAllowing(OTHER_OU))).isTrue();
		}

		/**
		 * @param roleAssignable what UserRoleService.isUserRoleAssignable answers for the role
		 */
		private boolean renderOrgUnitTab(boolean roleAssignable, PermissionConstraint assignConstraint) {
			UserRole role = userRole();
			when(userRoleService.getById(ROLE_ID)).thenReturn(role);
			when(userRoleService.isUserRoleAssignable(any(UserRole.class), anyBoolean(), any(PermissionConstraint.class)))
					.thenReturn(roleAssignable);
			when(userPermissionContext.getConstraint(Section.ORGUNIT, Permission.ASSIGN)).thenReturn(assignConstraint);
			when(userPermissionContext.getConstraint(Section.ORGUNIT, Permission.READ)).thenReturn(everythingAllowed());
			when(orgUnitService.getActiveOrgUnitsWithUserRole(role)).thenReturn(List.of(orgUnitAssignment()));

			Model model = new ExtendedModelMap();
			userRoleController.assignedOrgUnitsFragment(model, ROLE_ID, true);

			@SuppressWarnings("unchecked")
			List<UserRoleController.OrgunitWithRoleAssignedDTO> mapping =
					(List<UserRoleController.OrgunitWithRoleAssignedDTO>) model.getAttribute("orgUnitMapping");
			assertThat(mapping).hasSize(1);

			return mapping.get(0).assignment().isCanEdit();
		}
	}

	@Nested
	@DisplayName("The button that adds an assignment on the role page is offered")
	class AddAssignmentButton {

		@Test
		@DisplayName("on the users tab for a role that can be assigned")
		void usersTabAssignableRole() {
			authenticateWith(Constants.ROLE_ADMINISTRATOR);

			assertThat(usersTabAllowsAdding(true)).isTrue();
		}

		@Test
		@DisplayName("but not on the users tab for a role that can never be assigned, so the UI stops offering what the endpoint rejects")
		void usersTabNonAssignableRole() {
			authenticateWith(Constants.ROLE_ADMINISTRATOR);

			assertThat(usersTabAllowsAdding(false)).isFalse();
		}

		@Test
		@DisplayName("on the org unit tab for a role that can be assigned")
		void orgUnitTabAssignableRole() {
			authenticateWith(Constants.ROLE_ADMINISTRATOR);

			assertThat(orgUnitTabAllowsAdding(true)).isTrue();
		}

		@Test
		@DisplayName("but not on the org unit tab for a role that can never be assigned, so the UI stops offering what the endpoint rejects")
		void orgUnitTabNonAssignableRole() {
			authenticateWith(Constants.ROLE_ADMINISTRATOR);

			assertThat(orgUnitTabAllowsAdding(false)).isFalse();
		}

		private boolean usersTabAllowsAdding(boolean assignableRole) {
			UserRole role = userRole();
			when(userRoleService.getById(ROLE_ID)).thenReturn(role);
			when(userRoleService.isAssignableRole(role)).thenReturn(assignableRole);

			Model model = new ExtendedModelMap();
			userRoleController.assignedUsersFragment(model, ROLE_ID, true);

			return (boolean) model.getAttribute("assignmentAddingAllowed");
		}

		private boolean orgUnitTabAllowsAdding(boolean assignableRole) {
			UserRole role = userRole();
			when(userRoleService.getById(ROLE_ID)).thenReturn(role);
			when(userRoleService.isAssignableRole(role)).thenReturn(assignableRole);

			Model model = new ExtendedModelMap();
			userRoleController.assignedOrgUnitsFragment(model, ROLE_ID, true);

			return (boolean) model.getAttribute("assignmentAddingAllowed");
		}
	}

	private static UserRole userRole() {
		ItSystem itSystem = new ItSystem();
		itSystem.setId(1L);
		itSystem.setIdentifier("some-it-system");

		UserRole role = new UserRole();
		role.setItSystem(itSystem);
		return role;
	}

	private static OrgUnitWithRole2 orgUnitAssignment() {
		RoleAssignedToOrgUnitDTO assignment = new RoleAssignedToOrgUnitDTO();
		assignment.setAssignedThrough(AssignedThrough.DIRECT);

		OrgUnitWithRole2 mapping = new OrgUnitWithRole2();
		mapping.setOuUuid(ASSIGNED_OU);
		mapping.setOuName("Byrådet");
		mapping.setAssignment(assignment);
		return mapping;
	}

	private static PermissionConstraint everythingAllowed() {
		return new PermissionConstraint(null, null);
	}

	private static PermissionConstraint onlyAllowing(String ouUuid) {
		return new PermissionConstraint(null, Set.of(ouUuid));
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
