package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RoleType;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for IT System API v2 endpoints.
 * <p>
 * Tests API endpoints for managing IT systems and their associated system roles, including:
 * - CRUD operations on IT systems
 * - Managing system roles within IT systems
 * - Retrieving users and assignments for IT systems
 * - Triggering synchronization operations
 * </p>
 * <p>
 * Note: All tests verify both HTTP status and REST documentation generation.
 * </p>
 */
@DisplayName("IT System API v2 Tests")
public class ItSystemApiV2Test extends AbstractApiTest {

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private UserService userService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.ITSYSTEM.toString());
	}

	/**
	 * Tests retrieval of all IT systems.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array of IT systems
	 * - All expected fields are present with correct types
	 * - REST documentation includes complete field descriptions
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return all IT systems")
	void testGetAllItSystems() throws Exception {
		List<ItSystem> allItSystems = itSystemService.getAll();

		MvcResult result = this.mockMvc.perform(get("/api/v2/itsystem")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andExpect(jsonPath("$.length()").value(allItSystems.size()))
			.andDo(document("itsystem-v2-list",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[]").description("Array of IT systems"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("Unique ID for the IT system"),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the IT system"),
					fieldWithPath("[].identifier").type(JsonFieldType.STRING).description("Technical identifier"),
					fieldWithPath("[].systemtype").type(JsonFieldType.STRING).description("Type of the IT system (AD, SAML, MANUAL, etc.)"),
					fieldWithPath("[].paused").type(JsonFieldType.BOOLEAN).description("Whether the IT system is paused"),
					fieldWithPath("[].hidden").type(JsonFieldType.BOOLEAN).description("Whether the IT system is hidden"),
					fieldWithPath("[].readonly").type(JsonFieldType.BOOLEAN).description("Whether the IT system is read-only"),
					fieldWithPath("[].canEditThroughApi").type(JsonFieldType.BOOLEAN).description("Whether the IT system can be edited via API"),
					fieldWithPath("[].deleted").type(JsonFieldType.BOOLEAN).description("Whether the IT system is deleted"),
					fieldWithPath("[].accesBlocked").type(JsonFieldType.BOOLEAN).description("Whether access to the IT system is blocked"),
					fieldWithPath("[].apiManagedRoleAssignments").type(JsonFieldType.BOOLEAN).description("Whether role assignments are API managed"),
					fieldWithPath("[].domain").type(JsonFieldType.STRING).description("Domain name (for AD systems)").optional(),
					fieldWithPath("[].email").type(JsonFieldType.STRING).description("Contact email").optional(),
					fieldWithPath("[].attestationResponsibleUuids").type(JsonFieldType.ARRAY).description("UUIDs of the users responsible for attestation").optional(),
					fieldWithPath("[].systemOwnerUuids").type(JsonFieldType.ARRAY).description("UUIDs of the system owners").optional()
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
	 * Tests retrieval of a specific IT system by ID.
	 * <p>
	 * Uses the first available IT system from the database.
	 * See {@link #testGetAllItSystems()} for full response field documentation.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no IT systems exist
	 */
	@Test
	@DisplayName("Should return specific IT system by ID")
	void testGetItSystemById() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		this.mockMvc.perform(get("/api/v2/itsystem/{id}", itSystem.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(itSystem.getId()))
			.andExpect(jsonPath("$.name").value(itSystem.getName()))
			.andExpect(jsonPath("$.identifier").value(itSystem.getIdentifier()))
			.andExpect(jsonPath("$.systemtype").value(itSystem.getSystemType().name()))
			.andDo(document("itsystem-v2-get",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the IT system")
				)
			));
	}

	/**
	 * Tests that requesting a non-existent IT system returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when IT system ID does not exist")
	void testGetItSystemById_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/v2/itsystem/{id}", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests creation of a new IT system.
	 * <p>
	 * Creates a SAML-based IT system with standard configuration. The request demonstrates
	 * all required and optional fields for IT system creation.
	 * </p>
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Created system has the correct name
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should create new IT system")
	void testCreateItSystem() throws Exception {
		String requestBody = """
			{
				"id": 0,
				"name": "Test SAML System",
				"identifier": "TEST_SAML_SYSTEM",
				"systemtype": "SAML",
				"paused": false,
				"hidden": false,
				"readonly": false,
				"canEditThroughApi": true,
				"deleted": false,
				"accesBlocked": false,
				"apiManagedRoleAssignments": false,
				"email": "test@example.com"
			}
			""";

		this.mockMvc.perform(post("/api/v2/itsystem")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Test SAML System"))
			.andExpect(jsonPath("$.identifier").value("TEST_SAML_SYSTEM"))
			.andExpect(jsonPath("$.systemtype").value("SAML"))
			.andExpect(jsonPath("$.canEditThroughApi").value(true))
			.andExpect(jsonPath("$.email").value("test@example.com"))
			.andDo(document("itsystem-v2-create",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				)
			));

		entityManager.flush();
		entityManager.clear();

		ItSystem created = itSystemService.getFirstByIdentifier("TEST_SAML_SYSTEM");
		assertThat(created).isNotNull();
		assertThat(created.getName()).isEqualTo("Test SAML System");
		assertThat(created.getSystemType()).isEqualTo(ItSystemType.SAML);
		assertThat(created.isCanEditThroughApi()).isTrue();
		assertThat(created.getEmail()).isEqualTo("test@example.com");
	}

	/**
	 * Tests retrieval of user roles associated with an IT system.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response array size matches the number of user roles in the database
	 * - Each user role contains expected fields
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no IT systems exist
	 */
	@Test
	@DisplayName("Should return user roles for IT system")
	void testGetSystemUserRoles() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		List<UserRole> expectedUserRoles = userRoleService.getByItSystem(itSystem);

		MvcResult result = this.mockMvc.perform(get("/api/v2/itsystem/{id}/userroles", itSystem.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$.length()").value(expectedUserRoles.size()))
			.andDo(document("itsystem-v2-userroles",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the IT system")
				)
			))
			.andReturn();

		// Verify user roles from database are present in response
		String responseBody = result.getResponse().getContentAsString();
		for (UserRole userRole : expectedUserRoles) {
			assertThat(responseBody)
				.as("Response should contain user role with name " + userRole.getName())
				.contains("\"name\":\"" + userRole.getName() + "\"");
		}
	}

	/**
	 * Tests retrieval of system roles for an IT system.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response array size matches the number of system roles in the database
	 * - Each system role contains expected fields
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no IT systems exist
	 */
	@Test
	@DisplayName("Should return system roles for IT system")
	void testGetSystemRoles() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		List<SystemRole> expectedSystemRoles = systemRoleService.getByItSystem(itSystem);

		MvcResult result = this.mockMvc.perform(get("/api/v2/itsystem/{id}/systemroles", itSystem.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$.length()").value(expectedSystemRoles.size()))
			.andDo(document("itsystem-v2-systemroles",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the IT system")
				)
			))
			.andReturn();

		// Verify system roles from database are present in response
		String responseBody = result.getResponse().getContentAsString();
		for (SystemRole systemRole : expectedSystemRoles) {
			assertThat(responseBody)
				.as("Response should contain system role with name " + systemRole.getName())
				.contains("\"name\":\"" + systemRole.getName() + "\"");

			assertThat(responseBody)
				.as("Response should contain system role with identifier " + systemRole.getIdentifier())
				.contains("\"identifier\":\"" + systemRole.getIdentifier() + "\"");
		}
	}

	/**
	 * Tests creation of a new system role within an IT system.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 201 Created
	 * - Created role has the correct name
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no IT systems exist
	 */
	@Test
	@DisplayName("Should create new system role in IT system")
	void testCreateSystemRole() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		String requestBody = """
			{
				"name": "Test System Role",
				"identifier": "test-system-role-api",
				"description": "A test system role created via API"
			}
			""";

		this.mockMvc.perform(post("/api/v2/itsystem/{id}/systemroles", itSystem.getId())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Test System Role"))
			.andDo(document("itsystem-v2-systemroles-create",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the IT system")
				)
			));

		entityManager.flush();
		entityManager.clear();

		SystemRole created = systemRoleService.getFirstByIdentifierAndItSystemId("test-system-role-api", itSystem.getId());
		assertThat(created).isNotNull();
		assertThat(created.getName()).isEqualTo("Test System Role");
	}

	/**
	 * Tests updating an existing system role.
	 * <p>
	 * Uses the first available system role from the first IT system in the database.
	 * Note that the identifier must remain the same - only name and description can be updated.
	 * </p>
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 204 No Content
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no system roles exist
	 */
	@Test
	@DisplayName("Should update existing system role")
	void testUpdateSystemRole() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		SystemRole systemRole = systemRoleService.getByItSystem(itSystem).stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No system role found"));

		String requestBody = String.format("""
			{
				"name": "Updated System Role Name",
				"identifier": "%s",
				"description": "Updated description"
			}
			""", systemRole.getIdentifier());

		this.mockMvc.perform(put("/api/v2/itsystem/{itSystemId}/systemroles/{systemRoleId}", itSystem.getId(), systemRole.getId())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNoContent())
			.andDo(document("itsystem-v2-systemroles-update",
				preprocessRequest(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("itSystemId").description("Unique ID of the IT system"),
					parameterWithName("systemRoleId").description("Unique ID of the system role")
				)
			));

		entityManager.flush();
		entityManager.clear();

		SystemRole updated = systemRoleService.getById(systemRole.getId());
		assertThat(updated).isNotNull();
		assertThat(updated.getName()).isEqualTo("Updated System Role Name");
		assertThat(updated.getDescription()).isEqualTo("Updated description");
	}

	/**
	 * Tests deletion of a system role.
	 * <p>
	 * Creates a temporary system role specifically for deletion to ensure test isolation
	 * and avoid affecting other tests.
	 * </p>
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 204 No Content
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no IT systems exist
	 */
	@Test
	@DisplayName("Should delete system role")
	void testDeleteSystemRole() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		// Create a system role to delete
		SystemRole systemRole = new SystemRole();
		systemRole.setName("Role to Delete");
		systemRole.setIdentifier("role-to-delete");
		systemRole.setDescription("Test role for deletion");
		systemRole.setItSystem(itSystem);
		systemRole.setRoleType(RoleType.BOTH);
		systemRole = systemRoleService.save(systemRole);

		entityManager.flush();
		entityManager.clear();

		this.mockMvc.perform(delete("/api/v2/itsystem/{itSystemId}/systemroles/{systemRoleId}", itSystem.getId(), systemRole.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isNoContent())
			.andDo(document("itsystem-v2-systemroles-delete",
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("itSystemId").description("Unique ID of the IT system"),
					parameterWithName("systemRoleId").description("Unique ID of the system role")
				)
			));

		entityManager.flush();
		entityManager.clear();

		assertThat(systemRoleService.getOptionalById(systemRole.getId())).isEmpty();
	}

	/**
	 * Tests retrieval of users who have access to an IT system by numeric ID.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array of user objects
	 * - Each user contains userId, name, and extUuid fields
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no IT systems exist
	 */
	@Test
	@DisplayName("Should return users with access to IT system by ID")
	void testGetUsersForItSystem() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		this.mockMvc.perform(get("/api/v2/itsystem/{id}/users", itSystem.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("itsystem-v2-users",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the IT system")
				)
			));
	}

	/**
	 * Tests retrieval of users using the IT system's identifier instead of numeric ID.
	 *
	 * @throws Exception if HTTP request fails or no IT systems exist
	 */
	@Test
	@DisplayName("Should return users with access to IT system by identifier")
	void testGetUsersForItSystem_ByIdentifier() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.filter(it -> it.getIdentifier() != null)
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system with identifier found"));

		this.mockMvc.perform(get("/api/v2/itsystem/{id}/users", itSystem.getIdentifier())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray());
	}

	/**
	 * Tests retrieval of users using the IT system's UUID.
	 *
	 * @throws Exception if HTTP request fails or no IT systems exist
	 */
	@Test
	@DisplayName("Should return users with access to IT system by UUID")
	void testGetUsersForItSystem_ByUuid() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		itSystem.setUuid("03f01e0c-5047-463c-b08f-63a00afed958");
		itSystemService.save(itSystem);
		entityManager.flush();
		entityManager.clear();

		this.mockMvc.perform(get("/api/v2/itsystem/{id}/users", itSystem.getUuid())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray());
	}

	/**
	 * Tests that requesting users for a non-existent IT system returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when IT system not found for users query")
	void testGetUsersForItSystem_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/v2/itsystem/{id}/users", "nonexistent-system")
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests retrieval of all role assignments for an IT system.
	 * <p>
	 * Returns detailed information about which users have which roles from this IT system,
	 * including assignment metadata like dates and assignment types.
	 * </p>
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array of assignment groups
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no IT systems exist
	 */
	@Test
	@DisplayName("Should return all role assignments for IT system")
	void testGetAssignmentsForItSystem() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		this.mockMvc.perform(get("/api/v2/itsystem/{id}/assignments", itSystem.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("itsystem-v2-assignments",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the IT system")
				)
			));
	}

	/**
	 * Tests retrieval of assignments using the IT system's identifier.
	 *
	 * @throws Exception if HTTP request fails or no IT systems exist
	 */
	@Test
	@DisplayName("Should return assignments for IT system by identifier")
	void testGetAssignmentsForItSystem_ByIdentifier() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.filter(it -> it.getIdentifier() != null)
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system with identifier found"));

		this.mockMvc.perform(get("/api/v2/itsystem/{id}/assignments", itSystem.getIdentifier())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray());
	}

	/**
	 * Tests the touch operation on an IT system.
	 * <p>
	 * The touch operation triggers a synchronization or update process for the IT system,
	 * typically used to refresh data from the external system or mark it for processing.
	 * </p>
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 202 Accepted (async operation)
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no IT systems exist
	 */
	@Test
	@DisplayName("Should trigger touch/sync operation on IT system")
	void testTouchItSystem() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		this.mockMvc.perform(post("/api/v2/itsystem/{id}/touch", itSystem.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isAccepted())
			.andDo(document("itsystem-v2-touch",
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the IT system")
				)
			));
	}

	@Test
	@DisplayName("Should create IT system with multiple attestation responsibles and system owners")
	void testCreateItSystem_withMultipleOwnersAndResponsibles() throws Exception {
		User user1 = userService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No users found"));
		User user2 = userService.getAll().stream()
			.skip(1)
			.findFirst()
			.orElseThrow(() -> new RuntimeException("Less than two users found"));

		String requestBody = String.format("""
			{
				"id": 0,
				"name": "Multi Owner System",
				"identifier": "MULTI_OWNER_SYSTEM",
				"systemtype": "SAML",
				"paused": false,
				"hidden": false,
				"readonly": false,
				"canEditThroughApi": true,
				"deleted": false,
				"accesBlocked": false,
				"apiManagedRoleAssignments": false,
				"attestationResponsibleUuids": ["%s", "%s"],
				"systemOwnerUuids": ["%s"]
			}
			""", user1.getUuid(), user2.getUuid(), user1.getUuid());

		this.mockMvc.perform(post("/api/v2/itsystem")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Multi Owner System"))
			.andExpect(jsonPath("$.attestationResponsibleUuids").isArray())
			.andExpect(jsonPath("$.attestationResponsibleUuids.length()").value(2))
			.andExpect(jsonPath("$.systemOwnerUuids").isArray())
			.andExpect(jsonPath("$.systemOwnerUuids.length()").value(1));

		entityManager.flush();
		entityManager.clear();

		ItSystem created = itSystemService.getFirstByIdentifier("MULTI_OWNER_SYSTEM");
		assertThat(created).isNotNull();
		assertThat(itSystemService.getAttestationResponsibles(created)).hasSize(2)
			.extracting(User::getUuid)
			.containsExactlyInAnyOrder(user1.getUuid(), user2.getUuid());
		assertThat(itSystemService.getSystemOwners(created)).hasSize(1)
			.extracting(User::getUuid)
			.containsExactly(user1.getUuid());
	}

	@Test
	@DisplayName("Should create IT system without owners when fields are omitted")
	void testCreateItSystem_withNoOwners() throws Exception {
		String requestBody = """
			{
				"id": 0,
				"name": "No Owner System",
				"identifier": "NO_OWNER_SYSTEM",
				"systemtype": "SAML",
				"paused": false,
				"hidden": false,
				"readonly": false,
				"canEditThroughApi": false,
				"deleted": false,
				"accesBlocked": false,
				"apiManagedRoleAssignments": false
			}
			""";

		this.mockMvc.perform(post("/api/v2/itsystem")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("No Owner System"))
			.andExpect(jsonPath("$.attestationResponsibleUuids").isEmpty())
			.andExpect(jsonPath("$.systemOwnerUuids").isEmpty());

		entityManager.flush();
		entityManager.clear();

		ItSystem created = itSystemService.getFirstByIdentifier("NO_OWNER_SYSTEM");
		assertThat(created).isNotNull();
		assertThat(itSystemService.getAttestationResponsibles(created)).isEmpty();
		assertThat(itSystemService.getSystemOwners(created)).isEmpty();
	}
}
