package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for Manager Substitute API v2 endpoints.
 * <p>
 * Tests API endpoints for managing manager-substitute relationships, including:
 * - Listing all managers with their substitutes
 * - Getting substitutes for a specific manager
 * - Adding new substitutes
 * - Updating existing substitutes
 * - Removing substitutes
 * </p>
 * <p>
 * Note: All tests verify both HTTP status and REST documentation generation.
 * </p>
 */
@DisplayName("Manager Substitute API v2 Tests")
public class ManagerSubstituteApiV2Test extends AbstractApiTest {

	@Value("${tests.username}")
	private String username;

	@Autowired
	private UserService userService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.ORGANISATION.toString());
	}

	/**
	 * Creates a manager-substitute relationship for testing.
	 * Sets up a user as manager for an org unit and assigns a substitute.
	 *
	 * @return the manager user with the substitute assigned
	 */
	private User createManagerWithSubstitute(User manager, User substitute, OrgUnit orgUnit) {
		orgUnit.setManager(manager);
		orgUnitService.save(orgUnit);

		if (manager.getManagerSubstitutes() == null) {
			manager.setManagerSubstitutes(new ArrayList<>());
		}

		ManagerSubstitute managerSubstitute = new ManagerSubstitute();
		managerSubstitute.setManager(manager);
		managerSubstitute.setSubstitute(substitute);
		managerSubstitute.setOrgUnit(orgUnit);
		managerSubstitute.setAssignedTts(new Date());

		manager.getManagerSubstitutes().add(managerSubstitute);
		userService.save(manager);

		entityManager.flush();
		entityManager.clear();

		return userService.getByUserId(manager.getUserId());
	}

	/**
	 * Tests retrieval of all managers with their assigned substitutes.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array of managers
	 * - Created manager-substitute relationship is present in response
	 * - Manager and substitute fields contain correct values
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return all managers with their substitutes")
	void testGetAllManagers() throws Exception {
		User manager = userService.getByUserId(username);
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		User substitute = userService.getAll().stream()
			.filter(u -> !u.getUserId().equals(username))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No substitute user found"));

		createManagerWithSubstitute(manager, substitute, orgUnit);

		MvcResult result = this.mockMvc.perform(get("/api/v2/manager")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andDo(document("manager-v2-list",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[]").description("Array of managers"),
					fieldWithPath("[].uuid").type(JsonFieldType.STRING).description("UUID of the manager"),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the manager"),
					fieldWithPath("[].userId").type(JsonFieldType.STRING).description("User ID of the manager"),
					fieldWithPath("[].managerSubstitutes").type(JsonFieldType.ARRAY).description("List of substitutes for this manager"),
					fieldWithPath("[].managerSubstitutes[].uuid").type(JsonFieldType.STRING).description("UUID of the substitute").optional(),
					fieldWithPath("[].managerSubstitutes[].name").type(JsonFieldType.STRING).description("Name of the substitute").optional(),
					fieldWithPath("[].managerSubstitutes[].userId").type(JsonFieldType.STRING).description("User ID of the substitute").optional(),
					fieldWithPath("[].managerSubstitutes[].orgUnitUuid").type(JsonFieldType.STRING).description("UUID of the org unit").optional(),
					fieldWithPath("[].managerSubstitutes[].orgUnitName").type(JsonFieldType.STRING).description("Name of the org unit").optional(),
					fieldWithPath("[].managerSubstitutes[].managerUuid").type(JsonFieldType.STRING).description("UUID of the manager").optional(),
					fieldWithPath("[].managerSubstitutes[].managerUserId").type(JsonFieldType.STRING).description("User ID of the manager").optional()
				)
			))
			.andReturn();

		String responseBody = result.getResponse().getContentAsString();

		// Verify manager is present in response
		assertThat(responseBody)
			.as("Response should contain manager with userId")
			.contains("\"userId\":\"" + manager.getUserId() + "\"");

		assertThat(responseBody)
			.as("Response should contain manager with name")
			.contains("\"name\":\"" + manager.getName() + "\"");

		// Verify substitute is present in the response
		assertThat(responseBody)
			.as("Response should contain substitute userId")
			.contains("\"userId\":\"" + substitute.getUserId() + "\"");

		assertThat(responseBody)
			.as("Response should contain orgUnit UUID")
			.contains("\"orgUnitUuid\":\"" + orgUnit.getUuid() + "\"");
	}

	/**
	 * Tests retrieval of substitutes for a specific manager.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array of substitutes
	 * - Substitute fields contain correct values
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return substitutes for a specific manager")
	void testGetManagerSubstitutes() throws Exception {
		User manager = userService.getByUserId(username);
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		User substitute = userService.getAll().stream()
			.filter(u -> !u.getUserId().equals(username))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No substitute user found"));

		createManagerWithSubstitute(manager, substitute, orgUnit);

		MvcResult result = this.mockMvc.perform(get("/api/v2/manager/{id}", manager.getUserId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andDo(document("manager-v2-get-substitutes",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("User ID of the manager")
				)
			))
			.andReturn();

		String responseBody = result.getResponse().getContentAsString();

		assertThat(responseBody)
			.as("Response should contain substitute userId")
			.contains("\"userId\":\"" + substitute.getUserId() + "\"");

		assertThat(responseBody)
			.as("Response should contain substitute name")
			.contains("\"name\":\"" + substitute.getName() + "\"");

		assertThat(responseBody)
			.as("Response should contain orgUnit UUID")
			.contains("\"orgUnitUuid\":\"" + orgUnit.getUuid() + "\"");

		assertThat(responseBody)
			.as("Response should contain manager UUID reference")
			.contains("\"managerUserId\":\"" + manager.getUserId() + "\"");
	}

	/**
	 * Tests error handling when requesting substitutes for a non-existent user.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when manager ID does not exist")
	void testGetManagerSubstitutes_ManagerNotFound() throws Exception {
		this.mockMvc.perform(get("/api/v2/manager/{id}", "nonexistent-user")
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that requesting substitutes for a non-manager user returns 400.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 400 when user is not a manager")
	void testGetManagerSubstitutes_NotAManager() throws Exception {
		User nonManager = userService.getAll().stream()
			.filter(u -> !userService.isManager(u))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No non-manager user found"));

		this.mockMvc.perform(get("/api/v2/manager/{id}", nonManager.getUserId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isBadRequest());
	}

	/**
	 * Tests adding a new substitute to a manager.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Substitute relationship is persisted in the database
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully add a substitute to a manager")
	void testAddSubstitute() throws Exception {
		User manager = userService.getByUserId(username);
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		// Make user a manager
		orgUnit.setManager(manager);
		orgUnitService.save(orgUnit);
		entityManager.flush();
		entityManager.clear();

		User substitute = userService.getAll().stream()
			.filter(u -> !u.getUserId().equals(username))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No substitute user found"));

		String requestBody = """
			{
				"uuid": "%s",
				"name": "%s",
				"userId": "%s",
				"orgUnitUuid": "%s",
				"orgUnitName": "%s",
				"managerUuid": "%s",
				"managerUserId": "%s"
			}
			""".formatted(
			substitute.getUuid(), substitute.getName(), substitute.getUserId(),
			orgUnit.getUuid(), orgUnit.getName(),
			manager.getUuid(), manager.getUserId());

		this.mockMvc.perform(post("/api/v2/manager")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("manager-v2-add-substitute",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				requestFields(
					fieldWithPath("uuid").type(JsonFieldType.STRING).description("UUID of the substitute"),
					fieldWithPath("name").type(JsonFieldType.STRING).description("Name of the substitute"),
					fieldWithPath("userId").type(JsonFieldType.STRING).description("User ID of the substitute"),
					fieldWithPath("orgUnitUuid").type(JsonFieldType.STRING).description("UUID of the org unit"),
					fieldWithPath("orgUnitName").type(JsonFieldType.STRING).description("Name of the org unit"),
					fieldWithPath("managerUuid").type(JsonFieldType.STRING).description("UUID of the manager"),
					fieldWithPath("managerUserId").type(JsonFieldType.STRING).description("User ID of the manager")
				)
			));

		entityManager.flush();
		entityManager.clear();

		User updatedManager = userService.getByUserId(manager.getUserId());
		boolean substituteExists = updatedManager.getManagerSubstitutes().stream()
			.anyMatch(ms -> ms.getSubstitute().getUserId().equals(substitute.getUserId())
				&& ms.getOrgUnit().getUuid().equals(orgUnit.getUuid()));

		assertTrue(substituteExists, "Substitute should be added to manager");
	}

	/**
	 * Tests that adding a substitute for a non-existent manager returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when adding substitute for non-existent manager")
	void testAddSubstitute_ManagerNotFound() throws Exception {
		String requestBody = """
			{
				"uuid": "sub-uuid",
				"name": "Test Sub",
				"userId": "testsub",
				"orgUnitUuid": "org-uuid",
				"orgUnitName": "Test Org",
				"managerUuid": "nonexistent-uuid",
				"managerUserId": "nonexistent-user"
			}
			""";

		this.mockMvc.perform(post("/api/v2/manager")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that adding a substitute for a non-manager user returns 400.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 400 when adding substitute for non-manager user")
	void testAddSubstitute_NotAManager() throws Exception {
		User nonManager = userService.getAll().stream()
			.filter(u -> !userService.isManager(u))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No non-manager user found"));

		User substitute = userService.getAll().stream()
			.filter(u -> !u.getUserId().equals(nonManager.getUserId()))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No substitute user found"));

		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		String requestBody = """
			{
				"uuid": "%s",
				"name": "%s",
				"userId": "%s",
				"orgUnitUuid": "%s",
				"orgUnitName": "%s",
				"managerUuid": "%s",
				"managerUserId": "%s"
			}
			""".formatted(
			substitute.getUuid(), substitute.getName(), substitute.getUserId(),
			orgUnit.getUuid(), orgUnit.getName(),
			nonManager.getUuid(), nonManager.getUserId());

		this.mockMvc.perform(post("/api/v2/manager")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest());
	}

	/**
	 * Tests updating an existing substitute assignment.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Substitute is updated in the database with new org unit
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully update a substitute assignment")
	void testUpdateSubstitute() throws Exception {
		User manager = userService.getByUserId(username);
		List<OrgUnit> orgUnits = orgUnitService.getAll();
		OrgUnit orgUnit = orgUnits.stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		OrgUnit newOrgUnit = orgUnits.stream()
			.filter(ou -> !ou.getUuid().equals(orgUnit.getUuid()))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No second org unit found"));

		User substitute = userService.getAll().stream()
			.filter(u -> !u.getUserId().equals(username))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No substitute user found"));

		createManagerWithSubstitute(manager, substitute, orgUnit);

		// Also make manager for the new org unit so the update has a valid target
		newOrgUnit.setManager(userService.getByUserId(username));
		orgUnitService.save(newOrgUnit);
		entityManager.flush();
		entityManager.clear();

		User newSubstitute = userService.getAll().stream()
			.filter(u -> !u.getUserId().equals(username) && !u.getUserId().equals(substitute.getUserId()))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No second substitute user found"));

		String requestBody = """
			{
				"uuid": "%s",
				"name": "%s",
				"userId": "%s",
				"orgUnitUuid": "%s",
				"orgUnitName": "%s",
				"managerUuid": "%s",
				"managerUserId": "%s"
			}
			""".formatted(
			newSubstitute.getUuid(), newSubstitute.getName(), newSubstitute.getUserId(),
			newOrgUnit.getUuid(), newOrgUnit.getName(),
			manager.getUuid(), manager.getUserId());

		this.mockMvc.perform(put("/api/v2/manager/{id}", substitute.getUserId())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("manager-v2-update-substitute",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("User ID of the substitute to update")
				)
			));

		entityManager.flush();
		entityManager.clear();

		User updatedManager = userService.getByUserId(manager.getUserId());
		boolean newSubstituteExists = updatedManager.getManagerSubstitutes().stream()
			.anyMatch(ms -> ms.getSubstitute().getUserId().equals(newSubstitute.getUserId())
				&& ms.getOrgUnit().getUuid().equals(newOrgUnit.getUuid()));

		assertTrue(newSubstituteExists, "Updated substitute should exist for manager");
	}

	/**
	 * Tests that updating a substitute for a non-existent manager returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when updating substitute for non-existent manager")
	void testUpdateSubstitute_ManagerNotFound() throws Exception {
		String requestBody = """
			{
				"uuid": "sub-uuid",
				"name": "Test Sub",
				"userId": "testsub",
				"orgUnitUuid": "org-uuid",
				"orgUnitName": "Test Org",
				"managerUuid": "nonexistent-uuid",
				"managerUserId": "nonexistent-user"
			}
			""";

		this.mockMvc.perform(put("/api/v2/manager/{id}", "old-sub")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests removing a substitute from a manager.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Substitute relationship is removed from the database
	 * - Response returns the updated substitute list
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully remove a substitute from a manager")
	void testDeleteSubstitute() throws Exception {
		User manager = userService.getByUserId(username);
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		User substitute = userService.getAll().stream()
			.filter(u -> !u.getUserId().equals(username))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No substitute user found"));

		createManagerWithSubstitute(manager, substitute, orgUnit);

		String requestBody = """
			{
				"uuid": "%s",
				"name": "%s",
				"userId": "%s",
				"orgUnitUuid": "%s",
				"orgUnitName": "%s",
				"managerUuid": "%s",
				"managerUserId": "%s"
			}
			""".formatted(
			substitute.getUuid(), substitute.getName(), substitute.getUserId(),
			orgUnit.getUuid(), orgUnit.getName(),
			manager.getUuid(), manager.getUserId());

		this.mockMvc.perform(delete("/api/v2/manager/{id}", manager.getUserId())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("manager-v2-delete-substitute",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("User ID of the manager")
				)
			));

		entityManager.flush();
		entityManager.clear();

		User updatedManager = userService.getByUserId(manager.getUserId());
		boolean substituteExists = updatedManager.getManagerSubstitutes().stream()
			.anyMatch(ms -> ms.getSubstitute().getUserId().equals(substitute.getUserId())
				&& ms.getOrgUnit().getUuid().equals(orgUnit.getUuid()));

		assertFalse(substituteExists, "Substitute should be removed from manager");
	}

	/**
	 * Tests that removing a substitute for a non-existent manager returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when removing substitute for non-existent manager")
	void testDeleteSubstitute_ManagerNotFound() throws Exception {
		String requestBody = """
			{
				"uuid": "sub-uuid",
				"name": "Test Sub",
				"userId": "testsub",
				"orgUnitUuid": "org-uuid",
				"orgUnitName": "Test Org",
				"managerUuid": "nonexistent-uuid",
				"managerUserId": "nonexistent-user"
			}
			""";

		this.mockMvc.perform(delete("/api/v2/manager/{id}", "nonexistent-user")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that removing a substitute for a non-manager user returns 400.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 400 when removing substitute for non-manager user")
	void testDeleteSubstitute_NotAManager() throws Exception {
		User nonManager = userService.getAll().stream()
			.filter(u -> !userService.isManager(u))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No non-manager user found"));

		String requestBody = """
			{
				"uuid": "sub-uuid",
				"name": "Test Sub",
				"userId": "testsub",
				"orgUnitUuid": "org-uuid",
				"orgUnitName": "Test Org",
				"managerUuid": "%s",
				"managerUserId": "%s"
			}
			""".formatted(nonManager.getUuid(), nonManager.getUserId());

		this.mockMvc.perform(delete("/api/v2/manager/{id}", nonManager.getUserId())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest());
	}
}
