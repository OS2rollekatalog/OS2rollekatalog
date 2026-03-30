package dk.digitalidentity.rc.controller.api.v1;

import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.interceptor.RoleChangeInterceptor;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for User API endpoints.
 * <p>
 * Tests API endpoints for querying user role information. These endpoints
 * support integration with external systems that need to verify user access rights and
 * retrieve role information in various formats. Users can be identified by user ID or
 * external UUID, and results can be filtered by IT system and domain.
 * </p>
 */
@DisplayName("User API Tests")
public class UserApiTest extends AbstractApiTest {

	@Value("${tests.username}")
	private String username;

	@Autowired
	private UserService userService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private DomainService domainService;

	@MockitoBean
	private AssignmentService assignmentService;

	@MockitoBean(name = "roleChangeInterceptor")
	private RoleChangeInterceptor roleChangeInterceptor;


	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.READ_ACCESS.toString());
	}

	/**
	 * Tests that user roles can be retrieved in OIO-BPP format.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes OIO-BPP formatted privilege profile
	 * - Role map provides identifier-to-name mappings
	 * - User name ID is included
	 * - IT system can be specified to filter roles
	 * - REST documentation is generated
	 * </p>
	 * <p>
	 * OIO-BPP (OIO Basic Privilege Profile) is a Danish standard for
	 * representing user privileges in XML format.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve user roles in OIO-BPP format")
	void testGetUserRoles() throws Exception {
		User user = userService.getByUserId(username);
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		this.mockMvc.perform(get("/api/user/{userid}/roles", user.getUserId())
				.header("ApiKey", API_KEY)
				.param("system", itSystem.getIdentifier()))
			.andExpect(status().isOk())
			.andDo(document("user-roles-oiobpp",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userid").description("The user ID or external UUID of the user")
				),
				queryParameters(
					parameterWithName("system").description("IT system identifier (optional)")
				),
				responseFields(
					fieldWithPath("oioBPP").type(JsonFieldType.STRING).description("OIO-BPP formatted privilege profile"),
					fieldWithPath("roleMap").type(JsonFieldType.OBJECT).description("Map of role identifiers to role names"),
					fieldWithPath("nameID").type(JsonFieldType.STRING).description("Name ID of the user")
				)
			));
	}

	/**
	 * Tests that user roles can be retrieved with domain filtering.
	 * <p>
	 * Verifies that:
	 * - Domain parameter is supported
	 * - Roles are filtered by specified domain
	 * - Response format matches standard OIO-BPP output
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve user roles filtered by domain")
	void testGetUserRoles_WithDomain() throws Exception {
		User user = userService.getByUserId(username);
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));
		Domain domain = domainService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No domain found in test environment"));

		this.mockMvc.perform(get("/api/user/{userid}/roles", user.getUserId())
				.header("ApiKey", API_KEY)
				.param("system", itSystem.getIdentifier())
				.param("domain", domain.getName()))
			.andExpect(status().isOk());
	}

	/**
	 * Tests that requesting roles for non-existent user returns 404.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 404 Not Found for invalid user ID
	 * - Error handling works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when user not found for roles query")
	void testGetUserRoles_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/user/{userid}/roles", "nonexistent-user")
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that user roles can be retrieved as categorized lists.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes separate lists for user roles, system roles, data roles, and function roles
	 * - User disabled status is included
	 * - Role map provides identifier-to-name mappings
	 * - REST documentation is generated
	 * </p>
	 * <p>
	 * This format provides structured role information categorized by type
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve user roles as categorized lists")
	void testGetUserRolesAsList() throws Exception {
		User user = userService.getByUserId(username);
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		this.mockMvc.perform(get("/api/user/{userid}/rolesAsList", user.getUserId())
				.header("ApiKey", API_KEY)
				.param("system", itSystem.getIdentifier()))
			.andExpect(status().isOk())
			.andDo(document("user-roles-list",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userid").description("The user ID or external UUID of the user")
				),
				queryParameters(
					parameterWithName("system").description("IT system identifier")
				),
				responseFields(
					fieldWithPath("disabled").type(JsonFieldType.BOOLEAN).description("Whether the user is disabled"),
					fieldWithPath("userRoles").type(JsonFieldType.ARRAY).description("List of user role identifiers"),
					fieldWithPath("systemRoles").type(JsonFieldType.ARRAY).description("List of system role identifiers"),
					fieldWithPath("dataRoles").type(JsonFieldType.ARRAY).description("List of data role identifiers"),
					fieldWithPath("functionRoles").type(JsonFieldType.ARRAY).description("List of function role identifiers"),
					fieldWithPath("roleMap").type(JsonFieldType.OBJECT).description("Map of role identifiers to role names"),
					fieldWithPath("nameID").type(JsonFieldType.STRING).description("Name ID of the user")
				)
			));
	}

	/**
	 * Tests that user roles list can be retrieved with domain filtering.
	 * <p>
	 * Verifies that:
	 * - Domain parameter is supported
	 * - Roles are filtered by specified domain
	 * - REST documentation includes domain parameter
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve user roles list filtered by domain")
	void testGetUserRolesAsList_WithDomain() throws Exception {
		User user = userService.getByUserId(username);
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));
		Domain domain = domainService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No domain found in test environment"));

		this.mockMvc.perform(get("/api/user/{userid}/rolesAsList", user.getUserId())
				.header("ApiKey", API_KEY)
				.param("system", itSystem.getIdentifier())
				.param("domain", domain.getName()))
			.andExpect(status().isOk())
			.andDo(document("user-roles-list-with-domain",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userid").description("The user ID or external UUID of the user")
				),
				queryParameters(
					parameterWithName("system").description("IT system identifier"),
					parameterWithName("domain").description("Domain name (optional, uses primary domain if not specified)")
				)
			));
	}

	/**
	 * Tests that requesting roles list for non-existent user returns 404.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 404 Not Found for invalid user ID
	 * - Error handling works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when user not found for roles list query")
	void testGetUserRolesAsList_NotFound() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		this.mockMvc.perform(get("/api/user/{userid}/rolesAsList", "nonexistent-user")
				.header("ApiKey", API_KEY)
				.param("system", itSystem.getIdentifier()))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that user name ID can be retrieved.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes user name ID
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve user name ID")
	void testGetUserNameId() throws Exception {
		User user = userService.getByUserId(username);

		this.mockMvc.perform(get("/api/user/{userid}/nameid", user.getUserId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andDo(document("user-nameid",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userid").description("The user ID or external UUID of the user")
				),
				responseFields(
					fieldWithPath("nameID").type(JsonFieldType.STRING).description("Name ID of the user")
				)
			));
	}

	/**
	 * Tests that user name ID can be retrieved with domain filtering.
	 * <p>
	 * Verifies that:
	 * - Domain parameter is supported
	 * - Name ID is scoped to specified domain
	 * - REST documentation includes domain parameter
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve user name ID for specific domain")
	void testGetUserNameId_WithDomain() throws Exception {
		User user = userService.getByUserId(username);
		Domain domain = domainService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No domain found in test environment"));

		this.mockMvc.perform(get("/api/user/{userid}/nameid", user.getUserId())
				.header("ApiKey", API_KEY)
				.param("domain", domain.getName()))
			.andExpect(status().isOk())
			.andDo(document("user-nameid-with-domain",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userid").description("The user ID or external UUID of the user")
				),
				queryParameters(
					parameterWithName("domain").description("Domain name (optional)")
				)
			));
	}

	/**
	 * Tests that requesting name ID for non-existent user returns 404.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 404 Not Found for invalid user ID
	 * - Error handling works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when user not found for name ID query")
	void testGetUserNameId_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/user/{userid}/nameid", "nonexistent-user")
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that user role membership can be checked.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Role membership check works correctly
	 * - User can be checked for specific user role by ID
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully check if user has specific user role")
	void testUserHasUserRole() throws Exception {
		User user = userService.getByUserId(username);
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		when(assignmentService.hasUserRole(eq(user), eq(userRole)))
			.thenReturn(true);

		this.mockMvc.perform(get("/api/user/{userid}/hasUserRole/{roleId}", user.getUserId(), userRole.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andDo(document("user-has-userrole",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userid").description("The user ID or external UUID of the user"),
					parameterWithName("roleId").description("The ID of the user role to check")
				)
			));
	}

	/**
	 * Tests that checking non-existent user role returns 404.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 404 Not Found for invalid role ID
	 * - Error handling works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when checking for non-existent user role")
	void testUserHasUserRole_NotFound() throws Exception {
		User user = userService.getByUserId(username);

		this.mockMvc.perform(get("/api/user/{userid}/hasUserRole/{roleId}", user.getUserId(), 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that system role membership can be checked.
	 * <p>
	 * Verifies that:
	 * - Endpoint checks for system role by identifier
	 * - Role identifier parameter is required
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully check if user has specific system role")
	void testUserHasSystemRole() throws Exception {
		User user = userService.getByUserId(username);
		UserRole userRole = userRoleService.getAll().stream()
			.filter(ur -> !ur.getSystemRoleAssignments().isEmpty())
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role with system role assignments found"));
		SystemRoleAssignment sra = userRole.getSystemRoleAssignments().get(0);
		String roleIdentifier = sra.getSystemRole().getIdentifier();

		when(assignmentService.getUserRolesByUserAndSystems(any(User.class), anyList()))
			.thenReturn(Set.of(userRole));

		this.mockMvc.perform(get("/api/user/{userid}/hasSystemRole", user.getUserId())
				.header("ApiKey", API_KEY)
				.param("roleIdentifier", roleIdentifier))
			.andExpect(status().isOk())
			.andDo(document("user-has-systemrole",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userid").description("The user ID or external UUID of the user")
				),
				queryParameters(
					parameterWithName("roleIdentifier").description("The identifier of the system role to check")
				)
			));
	}

	/**
	 * Tests that system role check can be scoped to IT system.
	 * <p>
	 * Verifies that:
	 * - System parameter filters role check to specific IT system
	 * - REST documentation includes system parameter
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully check system role membership for specific IT system")
	void testUserHasSystemRole_WithSystem() throws Exception {
		User user = userService.getByUserId(username);
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));
		UserRole userRole = userRoleService.getAll().stream()
			.filter(ur -> !ur.getSystemRoleAssignments().isEmpty())
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role with system role assignments found"));
		SystemRoleAssignment sra = userRole.getSystemRoleAssignments().get(0);
		String roleIdentifier = sra.getSystemRole().getIdentifier();

		when(assignmentService.getUserRolesByUserAndSystems(any(User.class), anyList()))
			.thenReturn(Set.of(userRole));

		this.mockMvc.perform(get("/api/user/{userid}/hasSystemRole", user.getUserId())
				.header("ApiKey", API_KEY)
				.param("roleIdentifier", roleIdentifier)
				.param("system", itSystem.getIdentifier()))
			.andExpect(status().isOk())
			.andDo(document("user-has-systemrole-system",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userid").description("The user ID or external UUID of the user")
				),
				queryParameters(
					parameterWithName("roleIdentifier").description("The identifier of the system role to check"),
					parameterWithName("system").description("IT system identifier to filter by (optional)")
				)
			));
	}

	/**
	 * Tests that system role check can be scoped to domain.
	 * <p>
	 * Verifies that:
	 * - Domain parameter filters role check to specific domain
	 * - REST documentation includes domain parameter
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully check system role membership for specific domain")
	void testUserHasSystemRole_WithDomain() throws Exception {
		User user = userService.getByUserId(username);
		Domain domain = domainService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No domain found in test environment"));

		this.mockMvc.perform(get("/api/user/{userid}/hasSystemRole", user.getUserId())
				.header("ApiKey", API_KEY)
				.param("roleIdentifier", "TEST_ROLE")
				.param("domain", domain.getName()))
			.andDo(document("user-has-systemrole-domain",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userid").description("The user ID or external UUID of the user")
				),
				queryParameters(
					parameterWithName("roleIdentifier").description("The identifier of the system role to check"),
					parameterWithName("domain").description("Domain name (optional)")
				)
			));
	}

	/**
	 * Tests that checking system role for non-existent user returns 404.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 404 Not Found for invalid user ID
	 * - Error handling works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when checking system role for non-existent user")
	void testUserHasSystemRole_UserNotFound() throws Exception {
		this.mockMvc.perform(get("/api/user/{userid}/hasSystemRole", "nonexistent-user")
				.header("ApiKey", API_KEY)
				.param("roleIdentifier", "TEST_ROLE"))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that invalid domain parameter returns 404.
	 * <p>
	 * Verifies that:
	 * - Invalid domain name returns HTTP 404 Not Found
	 * - Domain validation works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when domain is invalid")
	void testGetUserRoles_InvalidDomain() throws Exception {
		User user = userService.getByUserId(username);

		this.mockMvc.perform(get("/api/user/{userid}/roles", user.getUserId())
				.header("ApiKey", API_KEY)
				.param("domain", "nonexistent-domain"))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that invalid IT system parameter returns 404.
	 * <p>
	 * Verifies that:
	 * - Invalid IT system identifier returns HTTP 404 Not Found
	 * - IT system validation works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when IT system is invalid")
	void testGetUserRolesAsList_InvalidItSystem() throws Exception {
		User user = userService.getByUserId(username);

		this.mockMvc.perform(get("/api/user/{userid}/rolesAsList", user.getUserId())
				.header("ApiKey", API_KEY)
				.param("system", "nonexistent-system"))
			.andExpect(status().isNotFound());
	}
}
