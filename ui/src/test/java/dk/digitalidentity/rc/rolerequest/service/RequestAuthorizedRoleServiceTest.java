package dk.digitalidentity.rc.rolerequest.service;

import static dk.digitalidentity.rc.config.Constants.ROLE_REQUESTAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.TestContainersConfiguration;
import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.TestInterceptorConfiguration;
import dk.digitalidentity.rc.dao.PostponedConstraintDao;
import dk.digitalidentity.rc.dao.assignment.CurrentAssignmentDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.projections.PostponedConstraintValue;
import dk.digitalidentity.rc.dao.projections.SystemRoleConstraint;
import dk.digitalidentity.rc.rolerequest.service.scope.LimitedToType;
import dk.digitalidentity.rc.security.RolePostProcessor;
import dk.digitalidentity.saml.service.model.SamlGrantedAuthority;
import dk.digitalidentity.saml.service.model.TokenUser;

@ExtendWith({SpringExtension.class, RestDocumentationExtension.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles({ "test" })
@Transactional(rollbackFor = Exception.class)
@Import({TestContainersConfiguration.class, TestInterceptorConfiguration.class})
public class RequestAuthorizedRoleServiceTest {
	private static final String USER_UUID = "user-uuid";
	private static final String OTHER_USER_UUID = "other-user-uuid";
	private static final long USER_ROLE_ID = 1L;
	private static final long OTHER_USER_ROLE_ID = 2L;

	@Autowired
	private RequestAuthorizedRoleService requestAuthorizedRoleService;
	@MockitoBean
	private CurrentAssignmentDao currentAssignmentDao;
	@MockitoBean
	private PostponedConstraintDao postponedConstraintDao;

	@Test
	public void userDoesNotHaveAuthorizedRole() {
		// Given
		mockSecurityContext(false, OTHER_USER_UUID);
		final var user = user();
		// When
		final var limitedToOrgUnits = requestAuthorizedRoleService.accessibleOrgUnits(user);
		// Then
		assertThat(limitedToOrgUnits.type()).isEqualTo(LimitedToType.NONE);
	}

	@Test
	public void loggedInUserWithoutSessionAuthorityIsNotLookedUp() {
		// Given
		mockSecurityContext(false, USER_UUID);
		final var user = user();
		// When
		final var limitedToOrgUnits = requestAuthorizedRoleService.accessibleOrgUnits(user);
		// Then
		assertThat(limitedToOrgUnits.type()).isEqualTo(LimitedToType.NONE);
		verify(currentAssignmentDao, never()).findActiveSystemRoleConstraints(anyString(), anyString(), anyString(), any());
	}

	@Test
	public void userIsAuthorizedToOneOrgUnit() {
		// Given
		mockSecurityContext(true, USER_UUID);
		final var user = user();
		final var ouUuid = UUID.randomUUID().toString();
		mockConstraints(ouConstraint(ouUuid));

		// When
		final var limitedToOrgUnits = requestAuthorizedRoleService.accessibleOrgUnits(user);

		// Then
		assertThat(limitedToOrgUnits.type()).isEqualTo(LimitedToType.CONSTRAINED);
		assertThat(limitedToOrgUnits.orgUnits()).hasSize(1).allMatch(p -> p.equals(ouUuid));
	}

	@Test
	public void userIsAuthorizedWithDirectAssignmentNoPostponedRoles() {
		// Given
		mockSecurityContext(true, USER_UUID);
		final var ouUuid = UUID.randomUUID().toString();
		final var user = user();
		mockConstraints(ouConstraint(ouUuid));
		mockPostponed();

		// When
		final var limitedToOrgUnits = requestAuthorizedRoleService.accessibleOrgUnits(user);

		// Then
		assertThat(limitedToOrgUnits.type()).isEqualTo(LimitedToType.CONSTRAINED);
		assertThat(limitedToOrgUnits.orgUnits()).hasSize(1).allMatch(p -> p.equals(ouUuid));
	}

	// --- IT-system constraint tests (the "Bemyndiget på <IT-system>" scoping) ---

	@Test
	public void valueItSystemConstraintLimitsToThatItSystem() {
		// Given an authorized role limited to it-system 100 via a concrete VALUE constraint
		mockSecurityContext(true, USER_UUID);
		final var user = user();
		mockConstraints(itSystemConstraint(USER_ROLE_ID, ConstraintValueType.VALUE, "100"));

		// When
		final var result = requestAuthorizedRoleService.accessibleItSystemsForOrgUnit(user, orgUnit());

		// Then the user is constrained to exactly that it-system
		assertThat(result.type()).isEqualTo(LimitedToType.CONSTRAINED);
		assertThat(result.itSystems()).containsExactly(100L);
	}

	@Test
	public void postponedItSystemConstraintWithoutValueDoesNotGrantAllItSystems() {
		// Given
		mockSecurityContext(true, USER_UUID);
		final var user = user();
		mockConstraints(itSystemConstraint(USER_ROLE_ID, ConstraintValueType.POSTPONED, null));

		// When
		final var result = requestAuthorizedRoleService.accessibleItSystemsForOrgUnit(user, orgUnit());

		// Then
		assertThat(result.type()).isEqualTo(LimitedToType.NONE);
	}

	@Test
	public void valueItSystemConstraintMustNotBeDroppedByPostponedConstraintOfAnotherRole() {
		// Given
		mockSecurityContext(true, USER_UUID);
		final var user = user();
		mockConstraints(
			itSystemConstraint(USER_ROLE_ID, ConstraintValueType.VALUE, "100"),
			itSystemConstraint(OTHER_USER_ROLE_ID, ConstraintValueType.POSTPONED, null));
		mockPostponed(postponed(OTHER_USER_ROLE_ID, Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID, "200"));

		// When
		final var result = requestAuthorizedRoleService.accessibleItSystemsForOrgUnit(user, orgUnit());

		// Then
		assertThat(result.type()).isEqualTo(LimitedToType.CONSTRAINED);
		assertThat(result.itSystems()).containsExactlyInAnyOrder(100L, 200L);
	}

	// --- OU and it-system pairing ---

	@Test
	public void multipleRolesDoNotCrossProductOuAndItSystemScopes() {
		// Given
		mockSecurityContext(true, USER_UUID);
		final var user = user();
		final var ouLimitedRoleOu = orgUnit();
		final var otherOu = orgUnit();
		mockConstraints(
			ouConstraint(USER_ROLE_ID, ouLimitedRoleOu.getUuid()),
			itSystemConstraint(OTHER_USER_ROLE_ID, ConstraintValueType.VALUE, "100"));

		// When
		final var forOtherOu = requestAuthorizedRoleService.accessibleItSystemsForOrgUnit(user, otherOu);
		final var forOuLimitedRoleOu = requestAuthorizedRoleService.accessibleItSystemsForOrgUnit(user, ouLimitedRoleOu);

		// Then
		assertThat(forOtherOu.type()).isEqualTo(LimitedToType.CONSTRAINED);
		assertThat(forOtherOu.itSystems()).containsExactly(100L);
		assertThat(forOuLimitedRoleOu.type()).isEqualTo(LimitedToType.ALL);
	}

	@Test
	public void ouOnlyUnionStillCoversEveryRole() {
		// Given
		mockSecurityContext(true, USER_UUID);
		final var user = user();
		mockConstraints(
			ouConstraint(USER_ROLE_ID, orgUnit().getUuid()),
			itSystemConstraint(OTHER_USER_ROLE_ID, ConstraintValueType.VALUE, "100"));

		// When
		final var result = requestAuthorizedRoleService.accessibleOrgUnits(user);

		// Then
		assertThat(result.type()).isEqualTo(LimitedToType.ALL);
	}

	@Test
	public void valueOuConstraintCoversPostponedItSystemConstraintOnSameRole() {
		// Given
		mockSecurityContext(true, USER_UUID);
		final var user = user();
		final var orgUnit = orgUnit();
		mockConstraints(
			ouConstraint(USER_ROLE_ID, orgUnit.getUuid()),
			itSystemConstraint(USER_ROLE_ID, ConstraintValueType.POSTPONED, null));
		mockPostponed(postponed(USER_ROLE_ID, Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID, "100"));

		// When
		final var result = requestAuthorizedRoleService.accessibleItSystemsForOrgUnit(user, orgUnit);

		// Then
		assertThat(result.type()).isEqualTo(LimitedToType.CONSTRAINED);
		assertThat(result.itSystems()).containsExactly(100L);
	}

	@Test
	public void postponedOuConstraintWithoutPostponedItSystemConstraintDoesNotGrantAllItSystems() {
		// Given
		mockSecurityContext(true, USER_UUID);
		final var user = user();
		final var orgUnit = orgUnit();
		mockConstraints(
			ouPostponedConstraint(USER_ROLE_ID),
			itSystemConstraint(USER_ROLE_ID, ConstraintValueType.POSTPONED, null));
		mockPostponed(postponed(USER_ROLE_ID, Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID, orgUnit.getUuid()));

		// When
		final var result = requestAuthorizedRoleService.accessibleItSystemsForOrgUnit(user, orgUnit);

		// Then
		assertThat(result.type()).isEqualTo(LimitedToType.NONE);
	}

	@Test
	public void postponedItSystemConstraintWithBlankValueDoesNotGrantAllItSystems() {
		// Given
		mockSecurityContext(true, USER_UUID);
		final var user = user();
		final var orgUnit = orgUnit();
		mockConstraints(
			ouPostponedConstraint(USER_ROLE_ID),
			itSystemConstraint(USER_ROLE_ID, ConstraintValueType.POSTPONED, null));
		mockPostponed(
			postponed(USER_ROLE_ID, Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID, orgUnit.getUuid()),
			postponed(USER_ROLE_ID, Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID, ""));

		// When
		final var result = requestAuthorizedRoleService.accessibleItSystemsForOrgUnit(user, orgUnit);

		// Then
		assertThat(result.type()).isEqualTo(LimitedToType.NONE);
	}

	@Test
	public void postponedOuConstraintAppliesRoleValueItSystemConstraint() {
		// Given
		mockSecurityContext(true, USER_UUID);
		final var user = user();
		final var orgUnit = orgUnit();
		mockConstraints(
			ouPostponedConstraint(USER_ROLE_ID),
			itSystemConstraint(USER_ROLE_ID, ConstraintValueType.VALUE, "100"));
		mockPostponed(postponed(USER_ROLE_ID, Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID, orgUnit.getUuid()));

		// When
		final var result = requestAuthorizedRoleService.accessibleItSystemsForOrgUnit(user, orgUnit);
		final var otherResult = requestAuthorizedRoleService.accessibleItSystemsForOrgUnit(user, orgUnit());

		// Then
		assertThat(result.type()).isEqualTo(LimitedToType.CONSTRAINED);
		assertThat(result.itSystems()).containsExactly(100L);
		assertThat(otherResult.type()).isEqualTo(LimitedToType.NONE);
	}

	@Test
	public void postponedOuConstraintWithoutItSystemConstraintGrantsAllItSystems() {
		// Given
		mockSecurityContext(true, USER_UUID);
		final var user = user();
		final var orgUnit = orgUnit();
		mockConstraints(ouPostponedConstraint(USER_ROLE_ID));
		mockPostponed(postponed(USER_ROLE_ID, Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID, orgUnit.getUuid()));

		// When
		final var result = requestAuthorizedRoleService.accessibleItSystemsForOrgUnit(user, orgUnit);

		// Then
		assertThat(result.type()).isEqualTo(LimitedToType.ALL);
	}

	@Test
	public void postponedOuConstraintLimitsOuUnionToAssignedOus() {
		// Given
		mockSecurityContext(true, USER_UUID);
		final var user = user();
		final var orgUnit = orgUnit();
		mockConstraints(ouPostponedConstraint(USER_ROLE_ID));
		mockPostponed(postponed(USER_ROLE_ID, Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID, orgUnit.getUuid()));

		// When
		final var result = requestAuthorizedRoleService.accessibleOrgUnits(user);

		// Then
		assertThat(result.type()).isEqualTo(LimitedToType.CONSTRAINED);
		assertThat(result.orgUnits()).containsExactly(orgUnit.getUuid());
	}

	private void mockConstraints(final SystemRoleConstraint... constraints) {
		doReturn(List.of(constraints)).when(currentAssignmentDao).findActiveSystemRoleConstraints(
			eq(USER_UUID), eq(Constants.ROLE_CATALOGUE_IDENTIFIER), eq(ROLE_REQUESTAUTHORIZED), any());
	}

	private void mockPostponed(final PostponedConstraintValue... postponed) {
		doReturn(List.of(postponed)).when(postponedConstraintDao).findValuesByUserAndSystemRole(USER_UUID, ROLE_REQUESTAUTHORIZED);
	}

	// one direct assignment per role
	private static PostponedConstraintValue postponed(final long userRoleId, final String entityId, final String value) {
		return new PostponedConstraintValue(userRoleId * 10, userRoleId, entityId, value);
	}

	private static SystemRoleConstraint ouConstraint(final String ouUuid) {
		return ouConstraint(USER_ROLE_ID, ouUuid);
	}

	private static SystemRoleConstraint ouConstraint(final long userRoleId, final String ouUuid) {
		return new SystemRoleConstraint(userRoleId, ConstraintValueType.VALUE, Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID, ouUuid);
	}

	private static SystemRoleConstraint ouPostponedConstraint(final long userRoleId) {
		return new SystemRoleConstraint(userRoleId, ConstraintValueType.POSTPONED, Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID, null);
	}

	private static SystemRoleConstraint itSystemConstraint(final long userRoleId, final ConstraintValueType valueType, final String value) {
		return new SystemRoleConstraint(userRoleId, valueType, Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID, value);
	}

	private static OrgUnit orgUnit() {
		final var orgUnit = new OrgUnit();
		orgUnit.setUuid(UUID.randomUUID().toString());
		return orgUnit;
	}

	private static void mockSecurityContext(final boolean hasAuthorizedRole, final String loggedInUserUuid) {
		final var authentication = Mockito.mock(Authentication.class);
		if (hasAuthorizedRole) {
			doReturn(Collections.singletonList(new SamlGrantedAuthority(ROLE_REQUESTAUTHORIZED)))
				.when(authentication).getAuthorities();
		} else {
			doReturn(Collections.emptyList()).when(authentication).getAuthorities();
		}
		final Map<String, Object> attributes = new HashMap<>();
		attributes.put(RolePostProcessor.ATTRIBUTE_USER_UUID, loggedInUserUuid);
		doReturn(TokenUser.builder().attributes(attributes).build()).when(authentication).getDetails();
		final var securityContext = Mockito.mock(SecurityContext.class);
		Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
		SecurityContextHolder.setContext(securityContext);
	}

	private static User user() {
		final var user = new User();
		user.setUuid(USER_UUID);
		return user;
	}
}
