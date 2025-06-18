package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.samlmodule.model.SamlGrantedAuthority;
import dk.digitalidentity.samlmodule.model.TokenUser;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dk.digitalidentity.rc.config.Constants.ROLE_REQUESTAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

@SpringBootTest
@ActiveProfiles({ "test" })
@ContextConfiguration(classes = {RequestAuthorizedRoleService.class})
public class RequestAuthorizedRoleServiceTest {
	@Autowired
	private RequestAuthorizedRoleService requestAuthorizedRoleService;
	@MockBean
	private ItSystemService itSystemServiceMock;
	@MockBean
	private UserService userServiceMock;

	@BeforeEach
	public void setup() {
		doReturn(Collections.singletonList(new ItSystem())).when(itSystemServiceMock).findByAnyIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
	}

	@Test
	public void userDoesNotHaveAuthorizedRole() {
		// Given
		mockSecurityContext(false);
		final var user = userWithNoDirectAssignments();
		// When
		final var limitedToOrgUnits = requestAuthorizedRoleService.accessibleOrgUnits(user);
		// Then
		assertThat(limitedToOrgUnits.type()).isEqualTo(RequestAuthorizedRoleService.LimitedToType.NONE);
	}

	@Test
	public void userIsAuthorizedToOneOrgUnit() {
		// Given
		mockSecurityContext(true);
		final var user = userWithNoDirectAssignments();
		final var ouUuid = UUID.randomUUID().toString();
		doReturn(Collections.singletonList(constrainedUserRole(Set.of(ouUuid)))).when(userServiceMock).getAllUserRoles(eq(user), anyList());

		// When
		final var limitedToOrgUnits = requestAuthorizedRoleService.accessibleOrgUnits(user);

		// Then
		assertThat(limitedToOrgUnits.type()).isEqualTo(RequestAuthorizedRoleService.LimitedToType.CONSTRAINED);
		assertThat(limitedToOrgUnits.orgUnits()).hasSize(1).allMatch(p -> p.equals(ouUuid));
	}
	@Test
	public void userIsAuthorizedWithDirectAssignmentNoPostponedRoles() {
		// Given
		mockSecurityContext(true);
		final var ouUuid = UUID.randomUUID().toString();
		final var userRole = constrainedUserRole(Set.of(ouUuid));
		final var user = userWithDirectAssignments(Collections.singletonList(directAssignment(userRole)));
		doReturn(Collections.singletonList(userRole)).when(userServiceMock).getAllUserRoles(eq(user), anyList());

		// When
		final var limitedToOrgUnits = requestAuthorizedRoleService.accessibleOrgUnits(user);

		// Then
		assertThat(limitedToOrgUnits.type()).isEqualTo(RequestAuthorizedRoleService.LimitedToType.CONSTRAINED);
		assertThat(limitedToOrgUnits.orgUnits()).hasSize(1).allMatch(p -> p.equals(ouUuid));
	}

	private UserRole constrainedUserRole(final Set<String> constrainedTo) {
		final var systemRole = new SystemRole();
		systemRole.setIdentifier(Constants.ROLE_REQUESTAUTHORIZED);

		final var constraintType = new ConstraintType();
		constraintType.setEntityId(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID);

		final var roleAssignmentConstraintValue = new SystemRoleAssignmentConstraintValue();
		roleAssignmentConstraintValue.setConstraintType(constraintType);
		roleAssignmentConstraintValue.setConstraintValueType(ConstraintValueType.VALUE);
		roleAssignmentConstraintValue.setConstraintValue(String.join(",", constrainedTo));

		final var systemRoleAssignment = new SystemRoleAssignment();
		systemRoleAssignment.setSystemRole(systemRole);
		systemRoleAssignment.setConstraintValues(Collections.singletonList(roleAssignmentConstraintValue));

		final var userRole = new UserRole();
		userRole.setIdentifier("dummy");
		userRole.setSystemRoleAssignments(Collections.singletonList(systemRoleAssignment));

		return userRole;
	}

	private static void mockSecurityContext(boolean hasAuthorizedRole) {
		final var authentication = Mockito.mock(Authentication.class);
		if (hasAuthorizedRole) {
			doReturn(Collections.singletonList(new SamlGrantedAuthority(ROLE_REQUESTAUTHORIZED)))
				.when(authentication).getAuthorities();
		} else {
			doReturn(Collections.emptyList()).when(authentication).getAuthorities();
		}
		doReturn(TokenUser.builder().build()).when(authentication).getDetails();
		final var securityContext = Mockito.mock(SecurityContext.class);
		Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
		SecurityContextHolder.setContext(securityContext);
	}

	private static UserUserRoleAssignment directAssignment(final UserRole userRole) {
		final var directAssignment = new UserUserRoleAssignment();
		directAssignment.setPostponedConstraints(Collections.emptyList());
		directAssignment.setUserRole(userRole);
		return directAssignment;
	}

	private static User userWithNoDirectAssignments() {
		final var user = new User();
		user.setUserRoleAssignments(Collections.emptyList());
		return user;
	}

	private static User userWithDirectAssignments(List<UserUserRoleAssignment> directAssignments) {
		final var user = new User();
		user.setUserRoleAssignments(directAssignments);
		return user;
	}

}
