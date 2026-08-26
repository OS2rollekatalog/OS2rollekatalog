package dk.digitalidentity.rc.controller.api.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentCalculator;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentService;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for User Role API v2 endpoints.
 * <p>
 * Tests API endpoints for managing user roles, which are high-level roles that bundle
 * multiple system roles together. User roles can be assigned to users either directly
 * or through organizational units, positions, or titles.
 * </p>
 * <p>
 * Note: All tests verify both HTTP status and REST documentation generation.
 * </p>
 */
@DisplayName("User Role API v2 Tests")
public class UserRoleApiV2Test extends AbstractApiTest {

	@Value("${tests.username}")
	private String username;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private UserService userService;

	@Autowired
	private CurrentAssignmentCalculator calculator;

	@Autowired
	private CurrentAssignmentService currentAssignmentService;

	@Autowired
	private RoleGroupService roleGroupService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.READ_ACCESS.toString(), AccessRole.ROLE_MANAGEMENT.toString());
	}

	// ========== GET /api/v2/userrole ==========

	/**
	 * Tests retrieval of all user roles.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response array size matches the number of roles in the database
	 * - Each role contains expected fields (id, name, identifier, itSystemId)
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return all user roles")
	void testGetAllUserRoles() throws Exception {
		List<UserRole> allRoles = userRoleService.getAll();
		int expectedCount = allRoles.size();

		ObjectMapper mapper = new ObjectMapper();
		MvcResult result = this.mockMvc.perform(get("/api/v2/userrole")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andExpect(jsonPath("$.length()").value(expectedCount))
			.andDo(document("userrole-v2-list",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[]").description("Array of user roles"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("Unique ID of the user role"),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the user role"),
					fieldWithPath("[].identifier").type(JsonFieldType.STRING).description("Technical identifier"),
					fieldWithPath("[].description").type(JsonFieldType.STRING).description("Description of the user role").optional(),
					fieldWithPath("[].itSystemId").type(JsonFieldType.NUMBER).description("ID of the associated IT system"),
					fieldWithPath("[].delegatedFromCvr").type(JsonFieldType.STRING).description("CVR delegated from").optional(),
					fieldWithPath("[].sensitiveRole").type(JsonFieldType.BOOLEAN).description("Whether this is a sensitive role"),
					fieldWithPath("[].userOnly").type(JsonFieldType.BOOLEAN).description("Whether this role can only be assigned to users"),
					fieldWithPath("[].canRequest").type(JsonFieldType.BOOLEAN).description("Whether this role can be requested"),
					fieldWithPath("[].systemRoleAssignments").type(JsonFieldType.ARRAY).description("System role assignments").optional(),
					fieldWithPath("[].systemRoleAssignments[].systemRoleId").type(JsonFieldType.NUMBER).description("System role ID").optional(),
					fieldWithPath("[].systemRoleAssignments[].systemRoleName").type(JsonFieldType.STRING).description("System role name").optional(),
					fieldWithPath("[].systemRoleAssignments[].systemRoleIdentifier").type(JsonFieldType.STRING).description("System role identifier").optional(),
					fieldWithPath("[].systemRoleAssignments[].constraintValues").type(JsonFieldType.ARRAY).description("Constraint values").optional(),
					fieldWithPath("[].systemRoleAssignments[].constraintValues[].constraintTypeId").type(JsonFieldType.NUMBER).description("Constraint type ID").optional(),
					fieldWithPath("[].systemRoleAssignments[].constraintValues[].constraintTypeEntityId").type(JsonFieldType.STRING).description("Constraint type entity ID").optional(),
					fieldWithPath("[].systemRoleAssignments[].constraintValues[].constraintValueType").type(JsonFieldType.STRING).description("Constraint value type").optional(),
					fieldWithPath("[].systemRoleAssignments[].constraintValues[].constraintValue").type(JsonFieldType.STRING).description("Constraint value").optional(),
					fieldWithPath("[].systemRoleAssignments[].constraintValues[].constraintIdentifier").type(JsonFieldType.STRING).description("Constraint identifier").optional(),
					fieldWithPath("[].systemRoleAssignments[].constraintValues[].postponed").type(JsonFieldType.BOOLEAN).description("Whether constraint is postponed").optional(),
					fieldWithPath("[].requesterPermission").type(JsonFieldType.ARRAY).description("Requester permission").optional(),
					fieldWithPath("[].approverPermission").type(JsonFieldType.ARRAY).description("Approver permission").optional(),
					fieldWithPath("[].approverPermissionInherited").type(JsonFieldType.BOOLEAN).description("Whether approver permission is inherited"),
					fieldWithPath("[].contactEmail").type(JsonFieldType.STRING).description("Contact email (performer) for the user role").optional(),
					fieldWithPath("[].advisEmail").type(JsonFieldType.STRING).description("Contact email (notification only) for the user role").optional(),
					fieldWithPath("[].ouFilterEnabled").type(JsonFieldType.BOOLEAN).description("Whether OU filter is enabled"),
					fieldWithPath("[].orgUnitFilterOrgUnits").type(JsonFieldType.ARRAY).description("Org units for OU filter"),
					fieldWithPath("[].roleAssignmentAttestationByAttestationResponsible").type(JsonFieldType.BOOLEAN).description("Whether role assignment attestation is by attestation responsible"),
					fieldWithPath("[].extraSensitiveRole").type(JsonFieldType.BOOLEAN).description("Whether role is extra sensitive"),
					fieldWithPath("[].allowPostponing").type(JsonFieldType.BOOLEAN).description("Whether postponing is allowed")
				)
			))
			.andReturn();

		JsonNode responseArray = mapper.readTree(result.getResponse().getContentAsString());
		for (UserRole dbRole : allRoles) {
			boolean found = false;
			for (JsonNode entry : responseArray) {
				if (entry.get("id").asLong() == dbRole.getId()) {
					assertThat(entry.get("name").asText()).isEqualTo(dbRole.getName());
					assertThat(entry.get("identifier").asText()).isEqualTo(dbRole.getIdentifier());
					assertThat(entry.get("itSystemId").asLong()).isEqualTo(dbRole.getItSystem().getId());
					found = true;
					break;
				}
			}
			assertThat(found).as("Expected user role %d (%s) in response", dbRole.getId(), dbRole.getName()).isTrue();
		}
	}

	// ========== GET /api/v2/userrole/{id} ==========

	/**
	 * Tests retrieval of a specific user role by ID.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response contains correct id, name, identifier, and itSystemId
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no user roles exist
	 */
	@Test
	@DisplayName("Should return specific user role by ID")
	void testGetUserRoleById() throws Exception {
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		this.mockMvc.perform(get("/api/v2/userrole/{id}", userRole.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(userRole.getId()))
			.andExpect(jsonPath("$.name").value(userRole.getName()))
			.andExpect(jsonPath("$.identifier").value(userRole.getIdentifier()))
			.andExpect(jsonPath("$.itSystemId").value(userRole.getItSystem().getId()))
			.andDo(document("userrole-v2-get",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the user role")
				)
			));
	}

	/**
	 * Tests that requesting a non-existent user role returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when user role ID does not exist")
	void testGetUserRoleById_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/v2/userrole/{id}", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	// ========== GET /api/v2/userrole/{id}/users ==========

	/**
	 * Tests retrieval of all users who have a specific user role.
	 * <p>
	 * Sets up a current assignment to ensure the user appears in the response.
	 * </p>
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is a non-empty array
	 * - The assigned user is present in the response
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no user roles exist
	 */
	@Test
	@DisplayName("Should return all users with specific user role")
	void testGetUsersWithRole() throws Exception {
		User user = userService.getByUserId(username);
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		userService.addUserRole(user, userRole, null, null);
		entityManager.flush();
		entityManager.clear();

		user = userService.getByUserId(username);
		var assignments = calculator.calculateAllAssignmentsForUser(user);
		currentAssignmentService.saveAllForUsers(Map.of(user, assignments.getLeft()));
		entityManager.flush();
		entityManager.clear();

		ObjectMapper mapper = new ObjectMapper();
		MvcResult result = this.mockMvc.perform(get("/api/v2/userrole/{id}/users", userRole.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andDo(document("userrole-v2-users",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the user role")
				)
			))
			.andReturn();

		JsonNode responseArray = mapper.readTree(result.getResponse().getContentAsString());
		boolean foundUser = false;
		for (JsonNode entry : responseArray) {
			if (entry.get("userId").asText().equals(username)) {
				foundUser = true;
				break;
			}
		}
		assertThat(foundUser).as("Expected user %s in response", username).isTrue();
	}

	/**
	 * Tests filtering users with role by domain.
	 * <p>
	 * Verifies that:
	 * - The domain query parameter is accepted
	 * - Response is an array
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no user roles exist
	 */
	@Test
	@DisplayName("Should return users with role filtered by domain")
	void testGetUsersWithRole_WithDomainFilter() throws Exception {
		User user = userService.getByUserId(username);
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		userService.addUserRole(user, userRole, null, null);
		entityManager.flush();
		entityManager.clear();

		user = userService.getByUserId(username);
		var assignments = calculator.calculateAllAssignmentsForUser(user);
		currentAssignmentService.saveAllForUsers(Map.of(user, assignments.getLeft()));
		entityManager.flush();
		entityManager.clear();

		this.mockMvc.perform(get("/api/v2/userrole/{id}/users", userRole.getId())
				.header("ApiKey", API_KEY)
				.param("domain", user.getDomain().getName()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("userrole-v2-users-domain",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the user role")
				),
				queryParameters(
					parameterWithName("domain").description("Domain name to filter users by (optional)")
				)
			));
	}

	/**
	 * Tests that requesting users for a non-existent user role returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when getting users for non-existent user role")
	void testGetUsersWithRole_RoleNotFound() throws Exception {
		this.mockMvc.perform(get("/api/v2/userrole/{id}/users", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that requesting users with a non-existent domain filter returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when domain filter does not exist")
	void testGetUsersWithRole_DomainNotFound() throws Exception {
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		this.mockMvc.perform(get("/api/v2/userrole/{id}/users", userRole.getId())
				.header("ApiKey", API_KEY)
				.param("domain", "nonexistent-domain"))
			.andExpect(status().isNotFound());
	}

	// ========== POST /api/v2/userrole ==========

	/**
	 * Tests creation of a new user role.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 201 Created
	 * - Response contains correct name, identifier, itSystemId, and description
	 * - Role is persisted in the database with correct properties
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no IT systems exist
	 */
	@Test
	@DisplayName("Should create new user role")
	void testCreateUserRole() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		String requestBody = String.format("""
			{
				"name": "Test User Role API",
				"identifier": "test-user-role-api",
				"description": "A test user role created via API",
				"itSystemId": %d,
				"userOnly": false,
				"sensitiveRole": false,
				"systemRoleAssignments": []
			}
			""", itSystem.getId());

		this.mockMvc.perform(post("/api/v2/userrole")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Test User Role API"))
			.andExpect(jsonPath("$.identifier").value("test-user-role-api"))
			.andExpect(jsonPath("$.description").value("A test user role created via API"))
			.andExpect(jsonPath("$.itSystemId").value(itSystem.getId()))
			.andExpect(jsonPath("$.userOnly").value(false))
			.andExpect(jsonPath("$.sensitiveRole").value(false))
			.andDo(document("userrole-v2-create",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				)
			));

		entityManager.flush();
		entityManager.clear();

		UserRole created = userRoleService.getByIdentifier("test-user-role-api");
		assertThat(created).isNotNull();
		assertThat(created.getName()).isEqualTo("Test User Role API");
		assertThat(created.getDescription()).isEqualTo("A test user role created via API");
		assertThat(created.getItSystem().getId()).isEqualTo(itSystem.getId());
		assertThat(created.isUserOnly()).isFalse();
		assertThat(created.isSensitiveRole()).isFalse();
	}

	/**
	 * Tests that creating a user role with a non-existent IT system returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when creating user role with non-existent IT system")
	void testCreateUserRole_ItSystemNotFound() throws Exception {
		String requestBody = """
			{
				"name": "Test Role Bad System",
				"identifier": "test-role-bad-system",
				"description": "Should fail",
				"itSystemId": 999999,
				"userOnly": false,
				"sensitiveRole": false,
				"systemRoleAssignments": []
			}
			""";

		this.mockMvc.perform(post("/api/v2/userrole")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	// ========== PUT /api/v2/userrole/{id} ==========

	/**
	 * Tests updating an existing user role.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Name and description are updated in the database
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no user roles exist
	 */
	@Test
	@DisplayName("Should update existing user role")
	void testUpdateUserRole() throws Exception {
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		String requestBody = String.format("""
			{
				"id": %d,
				"name": "Updated User Role Name",
				"identifier": "%s",
				"description": "Updated description",
				"itSystemId": %d,
				"userOnly": false,
				"sensitiveRole": false,
				"systemRoleAssignments": []
			}
			""", userRole.getId(), userRole.getIdentifier(), userRole.getItSystem().getId());

		this.mockMvc.perform(put("/api/v2/userrole/{id}", userRole.getId())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("userrole-v2-update",
				preprocessRequest(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the user role")
				)
			));

		entityManager.flush();
		entityManager.clear();

		UserRole updated = userRoleService.getById(userRole.getId());
		assertThat(updated).isNotNull();
		assertThat(updated.getName()).isEqualTo("Updated User Role Name");
		assertThat(updated.getDescription()).isEqualTo("Updated description");
	}

	/**
	 * Tests that updating a non-existent user role returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when updating non-existent user role")
	void testUpdateUserRole_NotFound() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		String requestBody = String.format("""
			{
				"id": 999999,
				"name": "Nonexistent Role",
				"identifier": "nonexistent",
				"description": "Should fail",
				"itSystemId": %d,
				"userOnly": false,
				"sensitiveRole": false,
				"systemRoleAssignments": []
			}
			""", itSystem.getId());

		this.mockMvc.perform(put("/api/v2/userrole/{id}", 999999)
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that updating a user role with mismatched ID in body and path returns 409.
	 * <p>
	 * The controller validates that the ID in the request body matches the path variable.
	 * A mismatch returns HTTP 409 Conflict.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no user roles exist
	 */
	@Test
	@DisplayName("Should return 409 when ID in body does not match path ID")
	void testUpdateUserRole_IdMismatch() throws Exception {
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		String requestBody = String.format("""
			{
				"id": %d,
				"name": "Mismatched Role",
				"identifier": "%s",
				"description": "Should fail",
				"itSystemId": %d,
				"userOnly": false,
				"sensitiveRole": false,
				"systemRoleAssignments": []
			}
			""", userRole.getId() + 1, userRole.getIdentifier(), userRole.getItSystem().getId());

		this.mockMvc.perform(put("/api/v2/userrole/{id}", userRole.getId())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isConflict());
	}

	// ========== DELETE /api/v2/userrole/{id} ==========

	/**
	 * Tests deletion of a user role.
	 * <p>
	 * Creates a temporary user role specifically for deletion to ensure test isolation.
	 * </p>
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 204 No Content
	 * - Role is no longer in the database
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no IT systems exist
	 */
	@Test
	@DisplayName("Should delete user role")
	void testDeleteUserRole() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		UserRole userRole = new UserRole();
		userRole.setName("Role to Delete");
		userRole.setIdentifier("role-to-delete-api");
		userRole.setDescription("Test role for deletion");
		userRole.setItSystem(itSystem);
		userRole.setSystemRoleAssignments(new ArrayList<>());
		userRole = userRoleService.save(userRole);

		entityManager.flush();
		entityManager.clear();

		this.mockMvc.perform(delete("/api/v2/userrole/{id}", userRole.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isNoContent())
			.andDo(document("userrole-v2-delete",
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the user role")
				)
			));

		entityManager.flush();
		entityManager.clear();

		assertThat(userRoleService.getOptionalById(userRole.getId())).isEmpty();
	}

	/**
	 * Tests that deleting a non-existent user role returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when deleting non-existent user role")
	void testDeleteUserRole_NotFound() throws Exception {
		this.mockMvc.perform(delete("/api/v2/userrole/{id}", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	// ========== GET /api/v2/userrole/{id}/assignments ==========

	/**
	 * Tests that a user role assigned directly to a user is reported as DIRECT.
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should report user role assigned directly to user as DIRECT")
	void testGetAssignmentsByUserRole_direct() throws Exception {
		User user = userService.getByUserId(username);
		UserRole userRole = unassignedUserRoleFor(user);

		userService.addUserRole(user, userRole, null, null);
		recalculateFor(username);

		JsonNode entry = singleEntryForUser(userRole.getId(), username, "userrole-v2-assignments");

		assertThat(entry.path("assignedThrough").asText()).isEqualTo("DIRECT");
		assertThat(entry.hasNonNull("orgUnit")).as("orgUnit should not be set for a direct assignment").isFalse();
		assertThat(entry.hasNonNull("roleGroup")).as("roleGroup should not be set for a direct assignment").isFalse();
		assertThat(entry.path("assignmentId").asLong()).isPositive();
		assertThat(entry.path("assignmentType").asText()).isEqualTo("USER_USER_ROLE");
	}

	/**
	 * Tests that the endpoint does NOT collapse per user: a user holding the same user role both
	 * directly and through an org unit must appear twice.
	 * <p>
	 * This is the central difference from the role group endpoint, which deliberately does dedupe.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should return one entry per origin, not one per user")
	void testGetAssignmentsByUserRole_notDeduplicatedPerUser() throws Exception {
		User user = userService.getByUserId(username);
		UserRole userRole = unassignedUserRoleFor(user);
		OrgUnit orgUnit = user.getPositions().stream()
			.map(Position::getOrgUnit)
			.findFirst()
			.orElseThrow(() -> new RuntimeException("Test user has no position"));

		userService.addUserRole(user, userRole, null, null);
		orgUnitService.addUserRole(orgUnit, userRole, false, null, null, new HashSet<>(), new HashSet<>());
		recalculateFor(username);

		List<JsonNode> entries = entriesForUser(getAssignments(userRole.getId(), null), username);

		assertThat(entries)
			.as("Expected two entries for %s: one direct and one through the org unit", username)
			.hasSize(2);
		assertThat(entries.stream().map(e -> e.path("assignedThrough").asText()))
			.containsExactlyInAnyOrder("DIRECT", "ORG_UNIT");
	}

	/**
	 * Tests that a user role inherited through a role group is reported as ROLE_GROUP with the
	 * role group it came through.
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should report user role inherited via role group as ROLE_GROUP with the role group set")
	void testGetAssignmentsByUserRole_viaRoleGroup() throws Exception {
		User user = userService.getByUserId(username);
		RoleGroup roleGroup = roleGroupService.getAll().stream()
			.filter(rg -> rg.getUserRoleAssignments() != null && !rg.getUserRoleAssignments().isEmpty())
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No role group with user roles found"));
		UserRole userRoleInGroup = roleGroup.getUserRoleAssignments().getFirst().getUserRole();

		userService.addRoleGroup(user, roleGroup, null, null, null, null);
		recalculateFor(username);

		JsonNode entry = singleEntryForUser(userRoleInGroup.getId(), username, null);

		assertThat(entry.path("assignedThrough").asText()).isEqualTo("ROLE_GROUP");
		assertThat(entry.path("roleGroup").path("id").asLong()).isEqualTo(roleGroup.getId());
		assertThat(entry.path("assignmentType").asText()).isEqualTo("USER_ROLE_GROUP");
	}

	/**
	 * Tests that requesting assignments for a non-existent user role returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when getting assignments for non-existent user role")
	void testGetAssignmentsByUserRole_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/v2/userrole/{id}/assignments", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Recalculates and persists current assignments for a user, so the endpoint has data to read.
	 */
	private void recalculateFor(String userId) {
		entityManager.flush();
		entityManager.clear();

		User user = userService.getByUserId(userId);
		var assignments = calculator.calculateAllAssignmentsForUser(user);
		currentAssignmentService.saveAllForUsers(Map.of(user, assignments.getLeft()));

		entityManager.flush();
		entityManager.clear();
	}

	/**
	 * Picks a user role the user does not already hold directly.
	 * <p>
	 * Necessary because the bootstrap makes {@code rolunittest01} an administrator, so the
	 * lowest-id user role is already assigned to the default test user. Taking the first role
	 * blindly would produce an extra row and make per-user counts unreliable.
	 * </p>
	 */
	private UserRole unassignedUserRoleFor(User user) {
		Set<Long> alreadyAssigned = user.getUserRoleAssignments() == null
			? Set.of()
			: user.getUserRoleAssignments().stream().map(a -> a.getUserRole().getId()).collect(Collectors.toSet());

		return userRoleService.getAll().stream()
			.filter(ur -> !alreadyAssigned.contains(ur.getId()))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role that is unassigned for " + user.getUserId()));
	}

	private JsonNode getAssignments(long userRoleId, String documentationId) throws Exception {
		var actions = this.mockMvc.perform(get("/api/v2/userrole/{id}/assignments", userRoleId)
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty());

		if (documentationId != null) {
			actions = actions.andDo(document(documentationId,
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the user role")
				)
			));
		}

		MvcResult result = actions.andReturn();
		return new ObjectMapper().readTree(result.getResponse().getContentAsString());
	}

	private static List<JsonNode> entriesForUser(JsonNode responseArray, String userId) {
		List<JsonNode> entries = new ArrayList<>();
		for (JsonNode entry : responseArray) {
			if (entry.path("user").path("userId").asText().equals(userId)) {
				entries.add(entry);
			}
		}
		return entries;
	}

	/**
	 * Returns the one entry for the given user, failing if there is not exactly one. Asserting the
	 * count matters here, since this endpoint deliberately allows several entries per user.
	 */
	private JsonNode singleEntryForUser(long userRoleId, String userId, String documentationId) throws Exception {
		List<JsonNode> entries = entriesForUser(getAssignments(userRoleId, documentationId), userId);
		assertThat(entries).as("Expected exactly one assignment for %s", userId).hasSize(1);
		return entries.getFirst();
	}
}
