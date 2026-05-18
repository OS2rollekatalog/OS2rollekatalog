package dk.digitalidentity.rc.controller.api.v1;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MvcResult;

import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentService;
import dk.digitalidentity.rc.test.AbstractApiTest;

/**
 * Test suite for IT System API endpoints.
 * <p>
 * Tests API endpoints for managing IT systems, including retrieving all systems,
 * managing system configurations, updating system roles, and querying user assignments.
 * Some IT systems can be edited via API (canEditThroughApi flag), while others are readonly.
 * </p>
 */
@DisplayName("IT System API Tests")
public class ItSystemApiTest extends AbstractApiTest {

	@Value("${tests.username}")
	private String username;

	@Autowired
	private UserService userService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private DomainService domainService;

	@Autowired
	private CurrentAssignmentService assignmentService;

	@Autowired
	private UserRoleService userRoleService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.ITSYSTEM.toString());
	}
	/**
	 * Tests that all IT systems can be retrieved successfully.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array of IT systems
	 * - Each system includes id, name, and identifier
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve all IT systems")
	void testGetAllItSystems() throws Exception {
		// Get all IT systems from database to compare against
		List<ItSystem> allItSystems = itSystemService.getAll();

		MvcResult result = this.mockMvc.perform(get("/api/itsystem/all")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andExpect(jsonPath("$.length()").value(allItSystems.size()))
			.andDo(document("itsystem-all",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[]").description("Array of all IT systems"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("Unique identifier of the IT system"),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the IT system"),
					fieldWithPath("[].identifier").type(JsonFieldType.STRING).description("Technical identifier of the IT system")
				)
			))
			.andReturn();

		// Verify all IT systems from database are present in response
		String responseBody = result.getResponse().getContentAsString();

		for (ItSystem itSystem : allItSystems) {
			assertThat(responseBody)
				.as("Response should contain IT system with id " + itSystem.getId())
				.contains("\"id\":" + itSystem.getId());

			assertThat(responseBody)
				.as("Response should contain IT system with name " + itSystem.getName())
				.contains("\"name\":\"" + itSystem.getName() + "\"");

			assertThat(responseBody)
				.as("Response should contain IT system with identifier " + itSystem.getIdentifier())
				.contains("\"identifier\":\"" + itSystem.getIdentifier() + "\"");
		}
	}

	/**
	 * Tests that manageable IT systems can be retrieved.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes only systems that can be edited via API
	 * - Systems have canEditThroughApi flag set to true
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve manageable IT systems")
	void testGetManageableItSystems() throws Exception {
		// Create/ensure we have an IT system with canEditThroughApi = true
		ItSystem manageableSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		manageableSystem.setCanEditThroughApi(true);
		itSystemService.save(manageableSystem);

		// Find an IT system with canEditThroughApi = false
		ItSystem nonManageableSystem = itSystemService.getAll().stream()
			.filter(itSystem -> !itSystem.isCanEditThroughApi())
			.findAny().orElseThrow(() -> new RuntimeException("No IT system found"));

		entityManager.flush();
		entityManager.clear();

		MvcResult result = this.mockMvc.perform(get("/api/itsystem/manage")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$.length()").value(1))
			.andDo(document("itsystem-manage-list",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[]").description("Array of IT systems that can be managed via API"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("Unique identifier of the IT system"),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the IT system"),
					fieldWithPath("[].identifier").type(JsonFieldType.STRING).description("Technical identifier of the IT system")
				)
			))
			.andReturn();

		String responseBody = result.getResponse().getContentAsString();

		// Verify manageable system IS included
		assertThat(responseBody)
			.as("Response should contain manageable IT system")
			.contains("\"id\":" + manageableSystem.getId())
			.contains("\"name\":\"" + manageableSystem.getName() + "\"")
			.contains("\"identifier\":\"" + manageableSystem.getIdentifier() + "\"");

		// Verify non-manageable system is NOT included
		assertThat(responseBody)
			.as("Response should NOT contain non-manageable IT system")
			.doesNotContain("\"id\":" + nonManageableSystem.getId())
			.doesNotContain("\"name\":\"" + nonManageableSystem.getName() + "\"")
			.doesNotContain("\"identifier\":\"" + nonManageableSystem.getIdentifier() + "\"");
	}

	/**
	 * Tests that a specific manageable IT system's details can be retrieved.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK for manageable systems
	 * - Response includes system details, system roles, and user roles
	 * - REST documentation is generated with field descriptions
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve manageable IT system details")
	void testGetManageableItSystem() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.filter(ItSystem::isCanEditThroughApi)
			.filter(its -> its.getSystemType() == ItSystemType.MANUAL ||
				its.getSystemType() == ItSystemType.AD ||
				its.getSystemType() == ItSystemType.SAML)
			.findFirst()
			.orElse(null);

		this.mockMvc.perform(get("/api/itsystem/manage/{id}", itSystem.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andDo(document("itsystem-manage-get",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("The ID of the IT system")
				),
				responseFields(
					fieldWithPath("id").type(JsonFieldType.NUMBER).description("Unique identifier of the IT system"),
					fieldWithPath("name").type(JsonFieldType.STRING).description("Name of the IT system"),
					fieldWithPath("identifier").type(JsonFieldType.STRING).description("Technical identifier of the IT system"),
					fieldWithPath("readonly").type(JsonFieldType.BOOLEAN).description("Whether the system is readonly").optional(),
					fieldWithPath("convertRolesEnabled").type(JsonFieldType.BOOLEAN).description("Can be ignored when reading the IT system data").optional(),
					fieldWithPath("systemRoles").type(JsonFieldType.ARRAY).description("Array of system roles"),
					fieldWithPath("systemRoles[].name").type(JsonFieldType.STRING).description("Name of the system role").optional(),
					fieldWithPath("systemRoles[].identifier").type(JsonFieldType.STRING).description("Unique identifier of the system role").optional(),
					fieldWithPath("systemRoles[].description").type(JsonFieldType.STRING).description("Description of the system role").optional(),
					subsectionWithPath("systemRoles[].users").description("Users with this assignment").optional(),
					fieldWithPath("userRoles").type(JsonFieldType.ARRAY).description("Array of user roles"),
					fieldWithPath("userRoles[].id").type(JsonFieldType.NUMBER).description("ID of the user role").optional(),
					fieldWithPath("userRoles[].name").type(JsonFieldType.STRING).description("Name of the user role").optional(),
					fieldWithPath("userRoles[].identifier").type(JsonFieldType.STRING).description("Unique identifier of the user role").optional(),
					fieldWithPath("userRoles[].description").type(JsonFieldType.STRING).description("Description of the user role").optional(),
					fieldWithPath("userRoles[].systemRoleAssignments").ignored().optional()
				)
			));
	}

	/**
	 * Tests that requesting a non-existent IT system returns 404.
	 * <p>
	 * Verifies that:
	 * - Non-existent IT system ID returns HTTP 404 Not Found
	 * - Error handling works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when manageable IT system not found")
	void testGetManageableItSystem_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/itsystem/manage/{id}", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that requesting a non-manageable IT system returns 403.
	 * <p>
	 * Verifies that:
	 * - IT systems with canEditThroughApi=false return HTTP 403 Forbidden
	 * - Access control is enforced correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 403 when IT system cannot be edited via API")
	void testGetManageableItSystem_Forbidden() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.filter(its -> !its.isCanEditThroughApi())
			.findFirst()
			.orElse(null);

		if (itSystem == null) {
			throw new RuntimeException("No itsystem found in test env");
		}

		this.mockMvc.perform(get("/api/itsystem/manage/{id}", itSystem.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isForbidden());
	}

	/**
	 * Tests that a manageable IT system can be updated successfully.
	 * <p>
	 * Verifies that:
	 * - Endpoint accepts IT system configuration updates
	 * - System name, identifier, and system roles can be updated
	 * - REST documentation is generated with request/response examples
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully update manageable IT system")
	void testUpdateManageableItSystem() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElse(null);

		if (itSystem == null) {
			throw new RuntimeException("No itsystem found in env");
		}
		itSystem.setCanEditThroughApi(true);
		itSystem.setSystemType(ItSystemType.MANUAL);
		itSystemService.save(itSystem);

		entityManager.flush();
		entityManager.clear();

		String requestBody = """
            {
                "name": "Updated IT System",
                "identifier": "updated-identifier",
                "readonly": false,
                "convertRolesEnabled": true,
                "systemRoles": [
                    {
                        "name": "Admin Role",
                        "identifier": "ADMIN",
                        "description": "Administrator access"
                    },
                    {
                        "name": "User Role",
                        "identifier": "USER",
                        "description": "Standard user access"
                    }
                ]
            }
            """;

		this.mockMvc.perform(post("/api/itsystem/manage/{id}", itSystem.getId())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("itsystem-manage-update",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("The ID of the IT system")
				),
				requestFields(
					fieldWithPath("name").type(JsonFieldType.STRING).description("Name of the IT system"),
					fieldWithPath("identifier").type(JsonFieldType.STRING).description("Technical identifier of the IT system"),
					fieldWithPath("readonly").type(JsonFieldType.BOOLEAN).description("Whether the system is readonly").optional(),
					fieldWithPath("convertRolesEnabled").type(JsonFieldType.BOOLEAN).description("Enable automatic conversion of system roles to user roles").optional(),
					fieldWithPath("systemRoles").type(JsonFieldType.ARRAY).description("Array of system roles"),
					fieldWithPath("systemRoles[].name").type(JsonFieldType.STRING).description("Name of the system role"),
					fieldWithPath("systemRoles[].identifier").type(JsonFieldType.STRING).description("Unique identifier of the system role"),
					fieldWithPath("systemRoles[].description").type(JsonFieldType.STRING).description("Description of the system role"),
					fieldWithPath("systemRoles[].users").type(JsonFieldType.ARRAY).description("List of user IDs to assign this role").optional()
				)
			));
	}

	/**
	 * Tests that IT system can be updated with user assignments.
	 * <p>
	 * Verifies that:
	 * - User assignments can be updated via the API
	 * - updateUserAssignments parameter controls assignment updates
	 * - System roles can include user lists
	 * - REST documentation includes user assignment examples
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully update IT system with user assignments")
	void testUpdateManageableItSystem_WithUserAssignments() throws Exception {
		ItSystem itSystem = new ItSystem();
		itSystem.setName("Test System With Users");
		itSystem.setIdentifier("test-system-users");
		itSystem.setSystemType(ItSystemType.MANUAL);
		itSystem.setCanEditThroughApi(true);
		itSystem.setReadonly(true);
		itSystemService.save(itSystem);
		entityManager.flush();

		User user = userService.getByUserId(username);

		String requestBody = String.format("""
            {
                "name": "Test System With Users",
                "identifier": "test-system-users",
                "readonly": true,
                "convertRolesEnabled": true,
                "systemRoles": [
                    {
                        "name": "Test Role",
                        "identifier": "TEST_ROLE",
                        "description": "Test role with user",
                        "users": ["%s"]
                    }
                ]
            }
            """, user.getUserId());

		this.mockMvc.perform(post("/api/itsystem/manage/{id}", itSystem.getId())
				.header("ApiKey", API_KEY)
				.param("updateUserAssignments", "true")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("itsystem-manage-update-with-users",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("The ID of the IT system")
				),
				queryParameters(
					parameterWithName("updateUserAssignments").description("If true, updates user assignments based on the systemRoles.users field (default: false)")
				)
			));
	}

	/**
	 * Tests that users with access to an IT system can be retrieved.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array of user IDs (sAMAccountNames)
	 * - IT system can be identified by ID
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve users for IT system")
	void testGetUsersForItSystem() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		this.mockMvc.perform(get("/api/itsystem/{id}/users", itSystem.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("itsystem-users",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("The ID, identifier, or UUID of the IT system")
				),
				responseFields(
					fieldWithPath("[]").description("Array of user IDs (sAMAccountNames) who have access to this IT system")
				)
			));
	}

	/**
	 * Tests that users with access to an IT system can be retrieved using identifier.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK when using identifier
	 * - Response is an array of user IDs (sAMAccountNames)
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve users for IT system using identifier")
	void testGetUsersForItSystemByIdentifier() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.filter(it -> it.getIdentifier() != null)
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system with identifier found"));

		this.mockMvc.perform(get("/api/itsystem/{id}/users", itSystem.getIdentifier())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("itsystem-users-by-identifier",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("The identifier of the IT system")
				),
				responseFields(
					fieldWithPath("[]").description("Array of user IDs (sAMAccountNames) who have access to this IT system")
				)
			));
	}

	/**
	 * Tests that users with access to an IT system can be retrieved using UUID.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK when using UUID
	 * - Response is an array of user IDs (sAMAccountNames)
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve users for IT system using UUID")
	void testGetUsersForItSystemByUuid() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system with UUID found"));

		itSystem.setUuid("03f01e0c-5047-463c-b08f-63a00afed958");

		itSystemService.save(itSystem);

		entityManager.flush();
		entityManager.clear();

		this.mockMvc.perform(get("/api/itsystem/{id}/users", itSystem.getUuid())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("itsystem-users-by-uuid",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("The UUID of the IT system")
				),
				responseFields(
					fieldWithPath("[]").description("Array of user IDs (sAMAccountNames) who have access to this IT system")
				)
			));
	}

	/**
	 * Tests that users can be filtered by domain.
	 * <p>
	 * Verifies that:
	 * - Domain parameter filters users by their domain
	 * - Response includes only users from specified domain
	 * - Users from other domains are excluded
	 * - REST documentation includes domain parameter
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve users filtered by domain")
	void testGetUsersForItSystem_WithDomain() throws Exception {
		// Create test domains
		Domain domain1 = new Domain();
		domain1.setName("DOMAIN1");
		domain1 = domainService.save(domain1);

		Domain domain2 = new Domain();
		domain2.setName("DOMAIN2");
		domain2 = domainService.save(domain2);

		// Create test IT system
		ItSystem itSystem = new ItSystem();
		itSystem.setName("Test IT System");
		itSystem.setIdentifier("test-system");
		itSystem.setIdentifier("banana");
		itSystem.setSystemType(ItSystemType.MANUAL);
		itSystem = itSystemService.save(itSystem);

		// Create a UserRole for the IT system
		UserRole userRole = new UserRole();
		userRole.setItSystem(itSystem);
		userRole.setName("Test Role");
		userRole.setIdentifier("TEST_ROLE");
		userRole = userRoleService.save(userRole);

		// Create test users in different domains
		User userInDomain1 = new User();
		userInDomain1.setUuid(UUID.randomUUID().toString());
		userInDomain1.setExtUuid(UUID.randomUUID().toString());
		userInDomain1.setName("User 1");
		userInDomain1.setUserId("user1");
		userInDomain1.setDomain(domain1);
		userInDomain1.setDeleted(false);
		userInDomain1.setDisabled(false);
		userInDomain1 = userService.save(userInDomain1);

		User userInDomain2 = new User();
		userInDomain2.setUuid(UUID.randomUUID().toString());
		userInDomain2.setExtUuid(UUID.randomUUID().toString());
		userInDomain2.setName("User 2");
		userInDomain2.setUserId("user2");
		userInDomain2.setDomain(domain2);
		userInDomain2.setDeleted(false);
		userInDomain2.setDisabled(false);
		userInDomain2 = userService.save(userInDomain2);

		// Create CurrentAssignments linking users to the UserRole
		CurrentAssignment assignment1 = new CurrentAssignment();
		assignment1.setUser(userInDomain1);
		assignment1.setUserRole(userRole);
		assignment1.setItSystem(itSystem);
		assignment1.setAssignmentId(1L);
		assignment1.setCreatedAt(LocalDateTime.now());
		assignment1.setRecordHash(assignment1.generateRecordHash());

		CurrentAssignment assignment2 = new CurrentAssignment();
		assignment2.setUser(userInDomain2);
		assignment2.setUserRole(userRole);
		assignment2.setItSystem(itSystem);
		assignment2.setAssignmentId(2L);
		assignment2.setCreatedAt(LocalDateTime.now());
		assignment2.setRecordHash(assignment2.generateRecordHash());
		assignmentService.saveAllForUsers(Map.of(userInDomain2, Set.of(assignment2, assignment1)));

		entityManager.flush();
		entityManager.clear();

		// Test filtering by domain1 - should return only user1
		this.mockMvc.perform(get("/api/itsystem/{id}/users", itSystem.getId())
				.header("ApiKey", API_KEY)
				.param("domain", domain1.getName()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0]").value("user1"))
			.andDo(document("itsystem-users-with-domain",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("The ID, identifier, or UUID of the IT system")
				),
				queryParameters(
					parameterWithName("domain").description("Filter users by domain (optional, uses primary domain if not specified)")
				),
				responseFields(
					fieldWithPath("[]").description("Array of user IDs (sAMAccountNames) who have access to this IT system in the specified domain")
				)
			));

		// Verify filtering by domain2 returns only user2
		this.mockMvc.perform(get("/api/itsystem/{id}/users", itSystem.getId())
				.header("ApiKey", API_KEY)
				.param("domain", domain2.getName()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0]").value("user2"));
	}

	/**
	 * Tests that requesting users for non-existent IT system returns 404.
	 * <p>
	 * Verifies that:
	 * - Non-existent IT system returns HTTP 404 Not Found
	 * - Error handling works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when IT system not found for users query")
	void testGetUsersForItSystem_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/itsystem/{id}/users", "nonexistent-system")
				.header("ApiKey", API_KEY))
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
	void testGetUsersForItSystem_InvalidDomain() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		this.mockMvc.perform(get("/api/itsystem/{id}/users", itSystem.getId())
				.header("ApiKey", API_KEY)
				.param("domain", "nonexistent-domain"))
			.andExpect(status().isNotFound());
	}
}
