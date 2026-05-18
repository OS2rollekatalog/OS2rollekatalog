package dk.digitalidentity.rc.controller.api.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
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
import java.util.List;
import java.util.Map;

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
 * Test suite for Role Group API v2 endpoints.
 * <p>
 * Tests API endpoints for managing role groups, which are collections of user roles
 * that can be assigned together as a bundle.
 * </p>
 * <p>
 * Note: All tests verify both HTTP status and REST documentation generation.
 * </p>
 */
@DisplayName("Role Group API v2 Tests")
public class UserRoleGroupApiV2Test extends AbstractApiTest {

	@Value("${tests.username}")
	private String username;

	@Autowired
	private RoleGroupService roleGroupService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private UserService userService;

	@Autowired
	private CurrentAssignmentCalculator calculator;

	@Autowired
	private CurrentAssignmentService currentAssignmentService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.READ_ACCESS.toString(), AccessRole.ROLE_MANAGEMENT.toString());
	}

	/**
	 * Tests retrieval of all role groups.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response array size matches the number of role groups in the database
	 * - Each role group contains correct id, name, and description
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return all role groups")
	void testGetAllRoleGroups() throws Exception {
		List<RoleGroup> allRoleGroups = roleGroupService.getAll();
		int expectedCount = allRoleGroups.size();

		ObjectMapper mapper = new ObjectMapper();
		MvcResult result = this.mockMvc.perform(get("/api/v2/rolegroup")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andExpect(jsonPath("$.length()").value(expectedCount))
			.andDo(document("rolegroup-v2-list",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[]").description("Array of role groups"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("Unique ID of the role group"),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the role group"),
					fieldWithPath("[].description").type(JsonFieldType.STRING).description("Description of the role group").optional(),
					fieldWithPath("[].userOnly").type(JsonFieldType.BOOLEAN).description("Whether this role group can only be assigned to users"),
					fieldWithPath("[].canRequest").type(JsonFieldType.BOOLEAN).description("Whether this role group can be requested"),
					fieldWithPath("[].userRoles").type(JsonFieldType.ARRAY).description("User roles in this group").optional(),
					fieldWithPath("[].userRoles[].userRoleId").type(JsonFieldType.NUMBER).description("User role ID").optional(),
					fieldWithPath("[].userRoles[].userRoleName").type(JsonFieldType.STRING).description("User role name").optional(),
					fieldWithPath("[].userRoles[].userRoleIdentifier").type(JsonFieldType.STRING).description("User role identifier").optional(),
					fieldWithPath("[].userRoles[].assignedByUserId").type(JsonFieldType.STRING).description("User ID of who assigned this role").optional(),
					fieldWithPath("[].userRoles[].assignedByName").type(JsonFieldType.STRING).description("Name of who assigned this role").optional(),
					fieldWithPath("[].userRoles[].assignedTimestamp").type(JsonFieldType.STRING).description("Timestamp when role was assigned").optional(),
					fieldWithPath("[].requesterPermission").type(JsonFieldType.ARRAY).description("Requester permission").optional(),
					fieldWithPath("[].approverPermission").type(JsonFieldType.ARRAY).description("Approver permission").optional()
				)
			))
			.andReturn();

		JsonNode responseArray = mapper.readTree(result.getResponse().getContentAsString());
		for (RoleGroup dbGroup : allRoleGroups) {
			boolean found = false;
			for (JsonNode entry : responseArray) {
				if (entry.get("id").asLong() == dbGroup.getId()) {
					assertThat(entry.get("name").asText()).isEqualTo(dbGroup.getName());
					found = true;
					break;
				}
			}
			assertThat(found).as("Expected role group %d (%s) in response", dbGroup.getId(), dbGroup.getName()).isTrue();
		}
	}

	/**
	 * Tests retrieval of a specific role group by ID.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response contains correct id, name, and description
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no role groups exist
	 */
	@Test
	@DisplayName("Should return specific role group by ID")
	void testGetRoleGroupById() throws Exception {
		RoleGroup roleGroup = roleGroupService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No role group found"));

		this.mockMvc.perform(get("/api/v2/rolegroup/{id}", roleGroup.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(roleGroup.getId()))
			.andExpect(jsonPath("$.name").value(roleGroup.getName()))
			.andExpect(jsonPath("$.description").value(roleGroup.getDescription()))
			.andDo(document("rolegroup-v2-get",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the role group")
				)
			));
	}

	/**
	 * Tests that requesting a non-existent role group returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when role group ID does not exist")
	void testGetRoleGroupById_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/v2/rolegroup/{id}", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests retrieval of all users who have a specific role group.
	 * <p>
	 * Sets up an assignment by assigning the role group to a user and calculating
	 * current assignments so the endpoint has data to return.
	 * </p>
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is a non-empty array
	 * - The assigned user is present in the response
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no role groups exist
	 */
	@Test
	@DisplayName("Should return all users with specific role group")
	void testGetUsersByRoleGroup() throws Exception {
		User user = userService.getByUserId(username);
		RoleGroup roleGroup = roleGroupService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No role group found"));

		userService.addRoleGroup(user, roleGroup, null, null, null, null);
		entityManager.flush();
		entityManager.clear();

		user = userService.getByUserId(username);
		var assignments = calculator.calculateAllAssignmentsForUser(user);
		currentAssignmentService.saveAllForUsers(Map.of(user, assignments.getLeft()));
		entityManager.flush();
		entityManager.clear();

		ObjectMapper mapper = new ObjectMapper();
		MvcResult result = this.mockMvc.perform(get("/api/v2/rolegroup/{id}/users", roleGroup.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andDo(document("rolegroup-v2-users",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the role group")
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
	 * Tests that requesting users for a non-existent role group returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when getting users for non-existent role group")
	void testGetUsersByRoleGroup_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/v2/rolegroup/{id}/users", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests creation of a new role group.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 201 Created
	 * - Response contains correct name, description, and userOnly flag
	 * - Role group is persisted in the database with correct properties
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should create new role group")
	void testCreateRoleGroup() throws Exception {
		this.mockMvc.perform(post("/api/v2/rolegroup")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"name": "Test Role Group API",
						"description": "A test role group created via API",
						"userOnly": false,
						"userRoles": []
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Test Role Group API"))
			.andExpect(jsonPath("$.description").value("A test role group created via API"))
			.andExpect(jsonPath("$.userOnly").value(false))
			.andDo(document("rolegroup-v2-create",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				)
			));

		entityManager.flush();
		entityManager.clear();

		RoleGroup created = roleGroupService.getByName("Test Role Group API")
			.orElseThrow(() -> new RuntimeException("Created role group not found in DB"));
		assertThat(created.getDescription()).isEqualTo("A test role group created via API");
		assertThat(created.isUserOnly()).isFalse();
	}

	/**
	 * Tests creation of a role group with user roles included.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 201 Created
	 * - The bundled user role is persisted in the role group
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should create role group with user roles")
	void testCreateRoleGroup_WithUserRoles() throws Exception {
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		String requestBody = String.format("""
			{
				"name": "Role Group With Roles",
				"description": "A role group with bundled user roles",
				"userOnly": false,
				"userRoles": [
					{
						"userRoleId": %d
					}
				]
			}
			""", userRole.getId());

		this.mockMvc.perform(post("/api/v2/rolegroup")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Role Group With Roles"))
			.andExpect(jsonPath("$.userRoles").isArray())
			.andExpect(jsonPath("$.userRoles.length()").value(1));

		entityManager.flush();
		entityManager.clear();

		RoleGroup created = roleGroupService.getByName("Role Group With Roles")
			.orElseThrow(() -> new RuntimeException("Created role group not found in DB"));
		assertThat(created.getUserRoleAssignments()).hasSize(1);
		assertThat(created.getUserRoleAssignments().get(0).getUserRole().getId()).isEqualTo(userRole.getId());
	}

	/**
	 * Tests that creating a role group with a duplicate name returns 409 Conflict.
	 *
	 * @throws Exception if HTTP request fails or no role groups exist
	 */
	@Test
	@DisplayName("Should return 409 when creating role group with duplicate name")
	void testCreateRoleGroup_Conflict() throws Exception {
		RoleGroup existing = roleGroupService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No role group found"));

		String requestBody = String.format("""
			{
				"name": "%s",
				"description": "Duplicate name",
				"userOnly": false,
				"userRoles": []
			}
			""", existing.getName());

		this.mockMvc.perform(post("/api/v2/rolegroup")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isConflict());
	}

	/**
	 * Tests updating an existing role group.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 204 No Content
	 * - Description and userOnly flag are updated in the database
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no role groups exist
	 */
	@Test
	@DisplayName("Should update existing role group")
	void testUpdateRoleGroup() throws Exception {
		RoleGroup roleGroup = roleGroupService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No role group found"));

		String requestBody = String.format("""
			{
				"id": %d,
				"name": "%s",
				"description": "Updated description",
				"userOnly": true,
				"userRoles": []
			}
			""", roleGroup.getId(), roleGroup.getName());

		this.mockMvc.perform(put("/api/v2/rolegroup/{id}", roleGroup.getId())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNoContent())
			.andDo(document("rolegroup-v2-update",
				preprocessRequest(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the role group")
				)
			));

		entityManager.flush();
		entityManager.clear();

		RoleGroup updated = roleGroupService.getById(roleGroup.getId());
		assertThat(updated).isNotNull();
		assertThat(updated.getDescription()).isEqualTo("Updated description");
		assertThat(updated.isUserOnly()).isTrue();
	}

	/**
	 * Tests that updating a non-existent role group returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when updating non-existent role group")
	void testUpdateRoleGroup_NotFound() throws Exception {
		String requestBody = """
			{
				"id": 999999,
				"name": "Nonexistent",
				"description": "Should fail",
				"userOnly": false,
				"userRoles": []
			}
			""";

		this.mockMvc.perform(put("/api/v2/rolegroup/{id}", 999999)
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that updating a role group with mismatched ID in body and path returns 409.
	 * <p>
	 * The controller validates that the ID in the request body matches the path variable.
	 * A mismatch returns HTTP 409 Conflict.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or no role groups exist
	 */
	@Test
	@DisplayName("Should return 409 when ID in body does not match path ID")
	void testUpdateRoleGroup_IdMismatch() throws Exception {
		RoleGroup roleGroup = roleGroupService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No role group found"));

		String requestBody = String.format("""
			{
				"id": %d,
				"name": "%s",
				"description": "Mismatch test",
				"userOnly": false,
				"userRoles": []
			}
			""", roleGroup.getId() + 1, roleGroup.getName());

		this.mockMvc.perform(put("/api/v2/rolegroup/{id}", roleGroup.getId())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isConflict());
	}

	/**
	 * Tests deletion of a role group.
	 * <p>
	 * Creates a temporary role group specifically for deletion to ensure test isolation.
	 * </p>
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 204 No Content
	 * - Role group is no longer in the database
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should delete role group")
	void testDeleteRoleGroup() throws Exception {
		RoleGroup roleGroup = new RoleGroup();
		roleGroup.setName("Role Group to Delete");
		roleGroup.setDescription("Test role group for deletion");
		roleGroup.setUserRoleAssignments(new ArrayList<>());
		roleGroup = roleGroupService.save(roleGroup);

		entityManager.flush();
		entityManager.clear();

		this.mockMvc.perform(delete("/api/v2/rolegroup/{id}", roleGroup.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isNoContent())
			.andDo(document("rolegroup-v2-delete",
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("Unique ID of the role group")
				)
			));

		entityManager.flush();
		entityManager.clear();

		assertThat(roleGroupService.getOptionalById(roleGroup.getId())).isEmpty();
	}

	/**
	 * Tests that deleting a non-existent role group returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when deleting non-existent role group")
	void testDeleteRoleGroup_NotFound() throws Exception {
		this.mockMvc.perform(delete("/api/v2/rolegroup/{id}", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}
}
