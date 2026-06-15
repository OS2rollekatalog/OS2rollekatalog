package dk.digitalidentity.rc.controller.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import dk.digitalidentity.rc.security.RolePostProcessor;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.test.AbstractApiTest;
import dk.digitalidentity.saml.service.model.SamlGrantedAuthority;
import dk.digitalidentity.saml.service.model.TokenUser;
import jakarta.servlet.ServletException;

/**
 * Tests that API endpoints correctly enforce role-based authorization.
 * For each role group, verifies that:
 * <ul>
 *     <li>Unauthenticated requests are rejected</li>
 *     <li>Requests with wrong roles are rejected</li>
 *     <li>Requests with the correct role are not rejected for authorization reasons</li>
 * </ul>
 */
@DisplayName("API Security Tests")
public class ApiSecurityTest extends AbstractApiTest {

	@Autowired
	private DomainService domainService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of();
	}

	private void setSecurityContext(String... roles) {
		List<SamlGrantedAuthority> authorities = new ArrayList<>();
		for (String role : roles) {
			authorities.add(new SamlGrantedAuthority("ROLE_API_" + role));
		}

		Map<String, Object> attributes = new HashMap<>();
		attributes.put(RolePostProcessor.ATTRIBUTE_NAME, "system");
		attributes.put(RolePostProcessor.ATTRIBUTE_USERID, "system");
		attributes.put(RolePostProcessor.ATTRIBUTE_CLIENT, "system-uuid");
		attributes.put(RolePostProcessor.ATTRIBUTE_USER_UUID, "system-uuid");

		TokenUser tokenUser = TokenUser.builder()
			.cvr("N/A")
			.attributes(attributes)
			.authorities(authorities)
			.username("System")
			.build();

		UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
			"System", "N/A", tokenUser.getAuthorities()
		);
		token.setDetails(tokenUser);
		SecurityContextHolder.getContext().setAuthentication(token);
	}

	/**
	 * Asserts that a request is denied due to missing or wrong authorization.
	 * Depending on the controller, Spring may either throw a ServletException
	 * or return a non-2xx status (e.g. 403 or 500 via ApiControllerAdvice).
	 */
	private void assertRequestDenied(MockHttpServletRequestBuilder request) throws Exception {
		try {
			ResultActions result = mockMvc.perform(request);
			int status = result.andReturn().getResponse().getStatus();
			assertTrue(status == 403 || status == 500,
				"Expected 403 or 500 but got " + status);
		} catch (ServletException e) {
			// Expected for controllers without ApiControllerAdvice
		}
	}

	// READ_ACCESS — GET /api/read/userroles

	@Nested
	@DisplayName("READ_ACCESS - GET /api/read/userroles")
	class ReadAccessTests {

		@Test
		@DisplayName("No authentication should be rejected")
		void noAuth() throws Exception {
			SecurityContextHolder.clearContext();
			assertRequestDenied(get("/api/read/userroles")
				.header("ApiKey", API_KEY));
		}

		@Test
		@DisplayName("Wrong role should be rejected")
		void wrongRole() throws Exception {
			setSecurityContext("ORGANISATION");
			assertRequestDenied(get("/api/read/userroles")
				.header("ApiKey", API_KEY));
		}

		@Test
		@DisplayName("Correct role should return 200")
		void correctRole() throws Exception {
			setSecurityContext("READ_ACCESS");
			mockMvc.perform(get("/api/read/userroles")
					.header("ApiKey", API_KEY))
				.andExpect(status().isOk());
		}
	}

	// ROLE_MANAGEMENT — GET /api/itSystem/managed

	@Nested
	@DisplayName("ROLE_MANAGEMENT - GET /api/itSystem/managed")
	class RoleManagementTests {

		@Test
		@DisplayName("No authentication should be rejected")
		void noAuth() throws Exception {
			SecurityContextHolder.clearContext();
			assertRequestDenied(get("/api/itSystem/managed")
				.header("ApiKey", API_KEY));
		}

		@Test
		@DisplayName("Wrong role should be rejected")
		void wrongRole() throws Exception {
			setSecurityContext("READ_ACCESS");
			assertRequestDenied(get("/api/itSystem/managed")
				.header("ApiKey", API_KEY));
		}

		@Test
		@DisplayName("Correct role should return 200")
		void correctRole() throws Exception {
			setSecurityContext("ROLE_MANAGEMENT");
			mockMvc.perform(get("/api/itSystem/managed")
					.header("ApiKey", API_KEY))
				.andExpect(status().isOk());
		}
	}

	// ITSYSTEM — GET /api/itsystem/all

	@Nested
	@DisplayName("ITSYSTEM - GET /api/itsystem/all")
	class ItSystemTests {

		@Test
		@DisplayName("No authentication should be rejected")
		void noAuth() throws Exception {
			SecurityContextHolder.clearContext();
			assertRequestDenied(get("/api/itsystem/all")
				.header("ApiKey", API_KEY));
		}

		@Test
		@DisplayName("Wrong role should be rejected")
		void wrongRole() throws Exception {
			setSecurityContext("ORGANISATION");
			assertRequestDenied(get("/api/itsystem/all")
				.header("ApiKey", API_KEY));
		}

		@Test
		@DisplayName("Correct role should return 200")
		void correctRole() throws Exception {
			setSecurityContext("ITSYSTEM");
			mockMvc.perform(get("/api/itsystem/all")
					.header("ApiKey", API_KEY))
				.andExpect(status().isOk());
		}
	}

	// AUDITLOG_ACCESS — GET /api/auditlog/head

	@Nested
	@DisplayName("AUDITLOG_ACCESS - GET /api/auditlog/head")
	class AuditlogAccessTests {

		@Test
		@DisplayName("No authentication should be rejected")
		void noAuth() throws Exception {
			SecurityContextHolder.clearContext();
			assertRequestDenied(get("/api/auditlog/head")
				.header("ApiKey", API_KEY));
		}

		@Test
		@DisplayName("Wrong role should be rejected")
		void wrongRole() throws Exception {
			setSecurityContext("READ_ACCESS");
			assertRequestDenied(get("/api/auditlog/head")
				.header("ApiKey", API_KEY));
		}

		@Test
		@DisplayName("Correct role should return 200")
		void correctRole() throws Exception {
			setSecurityContext("AUDITLOG_ACCESS");
			mockMvc.perform(get("/api/auditlog/head")
					.header("ApiKey", API_KEY))
				.andExpect(status().isOk());
		}
	}

	// ORGANISATION — GET /api/organisation/v3

	@Nested
	@DisplayName("ORGANISATION - GET /api/organisation/v3")
	class OrganisationTests {

		@Test
		@DisplayName("No authentication should be rejected")
		void noAuth() throws Exception {
			SecurityContextHolder.clearContext();
			assertRequestDenied(get("/api/organisation/v3")
				.header("ApiKey", API_KEY));
		}

		@Test
		@DisplayName("Wrong role should be rejected")
		void wrongRole() throws Exception {
			setSecurityContext("READ_ACCESS");
			assertRequestDenied(get("/api/organisation/v3")
				.header("ApiKey", API_KEY));
		}

		@Test
		@DisplayName("Correct role should return 200")
		void correctRole() throws Exception {
			setSecurityContext("ORGANISATION");
			mockMvc.perform(get("/api/organisation/v3")
					.header("ApiKey", API_KEY))
				.andExpect(status().isOk());
		}
	}

	// CICS_ADMIN — POST /api/cics/changepassword

	@Nested
	@DisplayName("CICS_ADMIN - POST /api/cics/changepassword")
	class CicsAdminTests {

		@Test
		@DisplayName("No authentication should be rejected")
		void noAuth() throws Exception {
			SecurityContextHolder.clearContext();
			assertRequestDenied(post("/api/cics/changepassword")
				.header("ApiKey", API_KEY)
				.contentType("application/json")
				.content("{\"username\":\"test\",\"newPassword\":\"test\"}"));
		}

		@Test
		@DisplayName("Wrong role should be rejected")
		void wrongRole() throws Exception {
			setSecurityContext("READ_ACCESS");
			assertRequestDenied(post("/api/cics/changepassword")
				.header("ApiKey", API_KEY)
				.contentType("application/json")
				.content("{\"username\":\"test\",\"newPassword\":\"test\"}"));
		}

		@Test
		@DisplayName("Correct role should not be rejected for authorization")
		void correctRole() throws Exception {
			setSecurityContext("CICS_ADMIN");
			mockMvc.perform(post("/api/cics/changepassword")
					.header("ApiKey", API_KEY)
					.contentType("application/json")
					.content("{\"username\":\"test\",\"newPassword\":\"test\"}"))
				.andExpect(status().is(org.hamcrest.Matchers.not(403)));
		}
	}

	// AD_SYNC_SERVICE — POST /api/v2/ad/syncAll/{domain}

	@Nested
	@DisplayName("AD_SYNC_SERVICE - POST /api/v2/ad/syncAll/{domain}")
	class AdSyncServiceTests {

		@Test
		@DisplayName("No authentication should be rejected")
		void noAuth() throws Exception {
			SecurityContextHolder.clearContext();
			assertRequestDenied(post("/api/v2/ad/syncAll/{domain}", "testdomain")
				.header("ApiKey", API_KEY));
		}

		@Test
		@DisplayName("Wrong role should be rejected")
		void wrongRole() throws Exception {
			setSecurityContext("READ_ACCESS");
			assertRequestDenied(post("/api/v2/ad/syncAll/{domain}", "testdomain")
				.header("ApiKey", API_KEY));
		}

		@Test
		@DisplayName("Correct role should return 200")
		void correctRole() throws Exception {
			setSecurityContext("AD_SYNC_SERVICE");
			String primaryDomainName = domainService.getPrimaryDomain().getName();
			mockMvc.perform(post("/api/v2/ad/syncAll/{domain}", primaryDomainName)
					.header("ApiKey", API_KEY))
				.andExpect(status().isOk());
		}
	}
}
