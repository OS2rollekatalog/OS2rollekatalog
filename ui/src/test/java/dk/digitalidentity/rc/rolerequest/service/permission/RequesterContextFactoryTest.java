package dk.digitalidentity.rc.rolerequest.service.permission;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.rolerequest.service.RequestAuthorizedRoleService;
import dk.digitalidentity.rc.rolerequest.service.scope.LimitedToItSystems;
import dk.digitalidentity.rc.rolerequest.service.scope.LimitedToType;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequesterContextFactory")
class RequesterContextFactoryTest {

	@Mock private SecurityUtil securityUtil;
	@Mock private UserService userService;
	@Mock private OrgUnitService orgUnitService;
	@Mock private RequestAuthorizedRoleService requestAuthorizedRoleService;

	@InjectMocks
	private RequesterContextFactory factory;

	private User requester;
	private User receiver;
	private OrgUnit receiverOrgUnit;

	@BeforeEach
	void setUp() {
		requester = user("requester-uuid");
		receiver = user("receiver-uuid");
		receiverOrgUnit = new OrgUnit();
		receiverOrgUnit.setUuid("receiver-ou-uuid");
		lenient().when(requestAuthorizedRoleService.accessibleItSystemsForOrgUnit(requester, receiverOrgUnit))
			.thenReturn(new LimitedToItSystems(LimitedToType.NONE, Set.of()));
	}

	@Nested
	@DisplayName("Relative to the receiver")
	class RelativeToReceiver {

		@Test
		@DisplayName("a manager elsewhere is not a manager of this receiver")
		void managerElsewhere() {
			// ---- Given ---- //
			lenient().when(userService.isManager(requester)).thenReturn(true);
			when(userService.isManagerOrSubstituteManagerFor(requester, receiver)).thenReturn(false);

			// ---- When ---- //
			RequesterContext context = factory.of(requester, receiver, receiverOrgUnit);

			// ---- Then ---- //
			assertThat(context.managerOrSubstitute()).isFalse();
		}

		@Test
		@DisplayName("an authorization responsible elsewhere is not authorization responsible for this receiver")
		void authResponsibleElsewhere() {
			// ---- Given ---- //
			lenient().when(orgUnitService.getByAuthorizationManagerMatchingUser(requester)).thenReturn(List.of(new OrgUnit()));
			when(orgUnitService.isAuthorizationManagerFor(requester, receiver)).thenReturn(false);

			// ---- When ---- //
			RequesterContext context = factory.of(requester, receiver, receiverOrgUnit);

			// ---- Then ---- //
			assertThat(context.authResponsible()).isFalse();
		}
	}

	@Nested
	@DisplayName("Authorized")
	class Authorized {

		@Test
		@DisplayName("is not authorized when no scope covers the receiver's OU")
		void receiverOrgUnitOutsideScope() {
			// ---- Given ---- //
			authorizedFor(new LimitedToItSystems(LimitedToType.NONE, Set.of()));

			// ---- When ---- //
			RequesterContext context = factory.of(requester, receiver, receiverOrgUnit);

			// ---- Then ---- //
			assertThat(context.authorized()).isFalse();
		}

		@Test
		@DisplayName("is authorized when a scope covers the receiver's OU")
		void receiverOrgUnitInsideScope() {
			// ---- Given ---- //
			authorizedFor(new LimitedToItSystems(LimitedToType.CONSTRAINED, Set.of(7L)));

			// ---- When ---- //
			RequesterContext context = factory.of(requester, receiver, receiverOrgUnit);

			// ---- Then ---- //
			assertThat(context.authorized()).isTrue();
		}

		@Test
		@DisplayName("is limited to the IT systems of the scopes covering the receiver's OU")
		void itSystemsOfCoveringScopes() {
			// ---- Given ---- //
			authorizedFor(new LimitedToItSystems(LimitedToType.CONSTRAINED, Set.of(7L)));

			// ---- When ---- //
			RequesterContext context = factory.of(requester, receiver, receiverOrgUnit);

			// ---- Then ---- //
			assertThat(context.authorizedItSystemIds()).containsExactly(7L);
		}

	}

	private void authorizedFor(LimitedToItSystems itSystems) {
		when(requestAuthorizedRoleService.accessibleItSystemsForOrgUnit(requester, receiverOrgUnit)).thenReturn(itSystems);
	}

	private static User user(String uuid) {
		User user = new User();
		user.setUuid(uuid);
		return user;
	}
}
