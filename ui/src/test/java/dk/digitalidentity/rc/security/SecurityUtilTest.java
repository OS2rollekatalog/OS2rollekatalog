package dk.digitalidentity.rc.security;

import static dk.digitalidentity.rc.config.Constants.ROLE_ADMINISTRATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.saml.service.model.SamlGrantedAuthority;
import dk.digitalidentity.saml.service.model.TokenUser;

@ExtendWith(MockitoExtension.class)
class SecurityUtilTest {

	@Mock
	private UserService userService;

	@Mock
	private AdminStatusChecker adminStatusChecker;

	@InjectMocks
	private SecurityUtil securityUtil;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private void mockSession(String loggedInUserUuid, boolean hasAdminAuthority) {
		Authentication authentication = mock(Authentication.class);
		List<SamlGrantedAuthority> authorities = hasAdminAuthority
			? Collections.singletonList(new SamlGrantedAuthority(ROLE_ADMINISTRATOR))
			: Collections.emptyList();
		// getAuthorities() is only consulted when isAdmin resolves to the session fast-path;
		// stubbing it unconditionally here keeps this helper reusable for both branches.
		lenient().doReturn(authorities).when(authentication).getAuthorities();
		TokenUser tokenUser = TokenUser.builder()
			.attributes(Map.of(RolePostProcessor.ATTRIBUTE_USER_UUID, loggedInUserUuid))
			.build();
		doReturn(tokenUser).when(authentication).getDetails();

		SecurityContext securityContext = mock(SecurityContext.class);
		when(securityContext.getAuthentication()).thenReturn(authentication);
		SecurityContextHolder.setContext(securityContext);
	}

	@Nested
	@DisplayName("isAdmin")
	class IsAdmin {

		@Test
		@DisplayName("Null user is denied without querying the database")
		void nullUserIsDenied() {
			// ---- When ---- //
			boolean result = securityUtil.isAdmin(null);

			// ---- Then ---- //
			assertThat(result).isFalse();
			verifyNoInteractions(adminStatusChecker);
		}

		@Test
		@DisplayName("Logged in user checking their own admin status uses the session, not the database")
		void loggedInUserCheckingSelfUsesSession() {
			// ---- Given ---- //
			User self = new User();
			self.setUuid("self-uuid");
			mockSession("self-uuid", true);

			// ---- When ---- //
			boolean result = securityUtil.isAdmin(self);

			// ---- Then ---- //
			assertThat(result).isTrue();
			verifyNoInteractions(adminStatusChecker);
		}

		@Test
		@DisplayName("Logged in user checking a different user's admin status falls back to the database")
		void loggedInUserCheckingDifferentUserFallsBackToDatabase() {
			// ---- Given ---- //
			User otherUser = new User();
			otherUser.setUuid("other-uuid");
			mockSession("self-uuid", true);
			when(adminStatusChecker.checkAdminInDatabase(otherUser)).thenReturn(true);

			// ---- When ---- //
			boolean result = securityUtil.isAdmin(otherUser);

			// ---- Then ---- //
			assertThat(result).isTrue();
		}

		@Test
		@DisplayName("Logged in user checking a different, non-admin user's status is correctly denied via the database")
		void loggedInUserCheckingDifferentNonAdminUserIsDenied() {
			// ---- Given ---- //
			User otherUser = new User();
			otherUser.setUuid("other-uuid");
			mockSession("self-uuid", true);
			when(adminStatusChecker.checkAdminInDatabase(otherUser)).thenReturn(false);

			// ---- When ---- //
			boolean result = securityUtil.isAdmin(otherUser);

			// ---- Then ---- //
			assertThat(result).isFalse();
		}

		@Test
		@DisplayName("No session (e.g. API-key authenticated caller) falls back to the database")
		void noSessionFallsBackToDatabase() {
			// ---- Given ---- //
			User user = new User();
			user.setUuid("some-uuid");
			when(adminStatusChecker.checkAdminInDatabase(user)).thenReturn(true);

			// ---- When ---- //
			boolean result = securityUtil.isAdmin(user);

			// ---- Then ---- //
			assertThat(result).isTrue();
		}

		@Test
		@DisplayName("Database failure on the fallback path is logged and denied, not propagated")
		void databaseFailureOnFallbackIsDeniedNotPropagated() {
			// ---- Given ---- //
			User user = new User();
			user.setUuid("some-uuid");
			when(adminStatusChecker.checkAdminInDatabase(user))
				.thenThrow(new RuntimeException("database unavailable"));

			// ---- When ---- //
			boolean result = securityUtil.isAdmin(user);

			// ---- Then ---- //
			assertThat(result).isFalse();
		}
	}
}
