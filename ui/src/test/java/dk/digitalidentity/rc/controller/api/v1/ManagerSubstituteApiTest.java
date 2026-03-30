package dk.digitalidentity.rc.controller.api.v1;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MvcResult;

import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.test.AbstractApiTest;

/**
 * Test suite for Manager Substitute API endpoints.
 * <p>
 * Tests API endpoints for managing manager substitute relationships, which allow
 * designated users to act on behalf of managers for specific organizational units.
 * The API supports listing all manager-substitute relationships, adding new substitutes
 * (one at a time), and removing existing substitutes. Each substitute is scoped to a
 * specific organizational unit.
 * </p>
 */
@DisplayName("Manager Substitute API Tests")
public class ManagerSubstituteApiTest extends AbstractApiTest {

	@Value("${tests.username}")
	private String username;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.ORGANISATION.toString());
	}

	/**
	 * Additional setup to create test data for manager substitute tests.
	 * <p>
	 * Assigns a user role and role group to the test user to ensure proper
	 * test environment for manager substitute operations.
	 * </p>
	 *
	 * @param restDocumentation REST documentation context provider
	 * @throws Exception if setup fails
	 */
	@BeforeEach
	@Override
	public void setUp(RestDocumentationContextProvider restDocumentation) throws Exception {
		super.setUp(restDocumentation);

		User user = userService.getByUserId(username);
		UserRole userRole = userRoleService.getAll().get(2);
		RoleGroup roleGroup = roleGroupService.getAll().get(0);
		userService.addRoleGroup(user, roleGroup, null, null, null, null);
		userService.addUserRole(user, userRole, null, null);
		entityManager.flush();
		entityManager.clear();
	}

	/**
	 * Tests that all manager-substitute relationships can be retrieved.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array of managers with their substitutes
	 * - Each manager includes name, userId, and list of substitutes
	 * - Each substitute includes name, userId, and orgUnitUuid
	 * - Created manager-substitute relationship is present in response
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve all manager-substitute relationships")
	void testManagerSubstituteAPI() throws Exception {
		User manager = userService.getByUserId(username);
		if (manager.getManagerSubstitutes() == null) {
			manager.setManagerSubstitutes(new ArrayList<>());
		}

		// Ensure the user is actually a manager by giving them a position with manager role
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		// Set the user as manager for the org unit
		orgUnit.setManager(manager);
		orgUnitService.save(orgUnit);

		User substitute = userService.getAll().stream()
			.filter(u -> !u.getUserId().equals(username))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No substitute user found"));

		ManagerSubstitute newSubstitute = new ManagerSubstitute();
		newSubstitute.setManager(manager);
		newSubstitute.setSubstitute(substitute);
		newSubstitute.setOrgUnit(orgUnit);
		newSubstitute.setAssignedTts(new Date());

		manager.getManagerSubstitutes().add(newSubstitute);
		userService.save(manager);

		entityManager.flush();
		entityManager.clear();

		MvcResult result = this.mockMvc.perform(get("/api/manager")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("manager-substitute-list",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the manager"),
					fieldWithPath("[].userId").type(JsonFieldType.STRING).description("User ID of the manager"),
					fieldWithPath("[].substitutes").type(JsonFieldType.ARRAY).description("List of substitutes for this manager"),
					fieldWithPath("[].substitutes[].name").type(JsonFieldType.STRING).description("Name of the substitute").optional(),
					fieldWithPath("[].substitutes[].userId").type(JsonFieldType.STRING).description("User ID of the substitute").optional(),
					fieldWithPath("[].substitutes[].orgUnitUuid").type(JsonFieldType.STRING).description("UUID of the org unit the substitute is assigned to").optional()
				)
			))
			.andReturn();

		String responseBody = result.getResponse().getContentAsString();

		// Verify manager is present in response array
		assertThat(responseBody)
			.as("Response should contain manager with userId and name")
			.contains("\"userId\":\"" + manager.getUserId() + "\"")
			.contains("\"name\":\"" + manager.getName() + "\"");

		// Verify the substitute is present in the manager's substitutes list
		assertThat(responseBody)
			.as("Response should contain substitute information")
			.contains("\"userId\":\"" + substitute.getUserId() + "\"")
			.contains("\"name\":\"" + substitute.getName() + "\"")
			.contains("\"orgUnitUuid\":\"" + orgUnit.getUuid() + "\"");

		// Verify the manager has at least one substitute
		assertThat(responseBody)
			.as("Response should contain substitutes array for manager")
			.contains("\"substitutes\":[");
	}

	/**
	 * Tests that a new manager substitute can be added successfully.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Substitute is assigned to the specified manager and org unit
	 * - Substitute relationship is persisted correctly
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully add new manager substitute assignment")
	void testAddManagerSubstituteAssignment() throws Exception {
		User manager = userService.findManagers().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No manager found in database"));

		User substitute = userService.getByUserId(username);

		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		String requestBody = """
       {
           "name": "%s",
           "userId": "%s",
           "substitutes": [
               {
                   "name": "%s",
                   "userId": "%s",
                   "orgUnitUuid": "%s"
               }
           ]
       }
       """.formatted(manager.getName(), manager.getUserId(),
			substitute.getName(), substitute.getUserId(), orgUnit.getUuid());

		this.mockMvc.perform(post("/api/manager")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("manager-substitute-add",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				requestFields(
					fieldWithPath("name").type(JsonFieldType.STRING).description("Name of the manager"),
					fieldWithPath("userId").type(JsonFieldType.STRING).description("User ID of the manager"),
					fieldWithPath("substitutes").type(JsonFieldType.ARRAY).description("List with exactly one substitute to add"),
					fieldWithPath("substitutes[].name").type(JsonFieldType.STRING).description("Name of the substitute"),
					fieldWithPath("substitutes[].userId").type(JsonFieldType.STRING).description("User ID of the substitute"),
					fieldWithPath("substitutes[].orgUnitUuid").type(JsonFieldType.STRING).description("UUID of the organization unit")
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
	 * Tests that attempting to add zero substitutes is rejected.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 400 Bad Request when substitutes array is empty
	 * - Validation enforces exactly one substitute in the array
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should reject request with empty substitutes array")
	void testAddManagerSubstituteAssignment_EmptyArray() throws Exception {
		User manager = userService.findManagers().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No manager found in database"));

		// Request with ZERO substitutes (should be rejected)
		String requestBody = """
       {
           "name": "%s",
           "userId": "%s",
           "substitutes": []
       }
       """.formatted(manager.getName(), manager.getUserId());

		this.mockMvc.perform(post("/api/manager")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest());
	}

	/**
	 * Tests that adding an already existing substitute returns 304 Not Modified.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 304 Not Modified when substitute already exists
	 * - Error message indicates the substitute relationship already exists
	 * - Duplicate prevention works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should return 304 when attempting to add existing substitute")
	void testAddManagerSubstituteAssignment_AlreadyExists() throws Exception {
		User manager = userService.findManagers().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No manager found in database"));

		if (manager.getManagerSubstitutes() == null) {
			manager.setManagerSubstitutes(new ArrayList<>());
		}

		User substitute = userService.getByUserId(username);
		assertNotNull(substitute, "Substitute user should exist");

		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		ManagerSubstitute existingSubstitute = new ManagerSubstitute();
		existingSubstitute.setManager(manager);
		existingSubstitute.setSubstitute(substitute);
		existingSubstitute.setOrgUnit(orgUnit);
		existingSubstitute.setAssignedTts(new Date());

		manager.getManagerSubstitutes().add(existingSubstitute);
		userService.save(manager);
		entityManager.flush();
		entityManager.clear();

		String requestBody = """
        {
            "name": "%s",
            "userId": "%s",
            "substitutes": [
                {
                    "name": "%s",
                    "userId": "%s",
                    "orgUnitUuid": "%s"
                }
            ]
        }
        """.formatted(manager.getName(), manager.getUserId(),
			substitute.getName(), substitute.getUserId(), orgUnit.getUuid());

		this.mockMvc.perform(post("/api/manager")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotModified())
			.andExpect(content().string("Allerede stedfortræder"));
	}

	/**
	 * Tests that adding a substitute for non-existent manager returns 404.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 404 Not Found when manager doesn't exist
	 * - Error message indicates invalid userId
	 * - Validation works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when manager is not found")
	void testAddManagerSubstituteAssignment_ManagerNotFound() throws Exception {
		String requestBody = """
        {
            "name": "Non Existent",
            "userId": "nonexistent123",
            "substitutes": [
                {
                    "name": "Test User",
                    "userId": "testuser",
                    "orgUnitUuid": "test-uuid"
                }
            ]
        }
        """;

		this.mockMvc.perform(post("/api/manager")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound())
			.andExpect(content().string("Det angivne userId peger ikke på en bruger"));
	}

	/**
	 * Tests that adding multiple substitutes at once is rejected.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 400 Bad Request with multiple substitutes
	 * - Only one substitute can be added per request
	 * - Error message indicates single substitute requirement
	 * </p>
	 * <p>
	 * Note: The API requires exactly one substitute in the array when adding.
	 * This is a business rule to ensure atomic operations.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 400 when attempting to add multiple substitutes at once")
	void testAddManagerSubstituteAssignment_InvalidSubstituteCount() throws Exception {
		User manager = userService.findManagers().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No manager found in database"));

		String requestBody = """
        {
            "name": "%s",
            "userId": "%s",
            "substitutes": [
                {
                    "name": "Sub1",
                    "userId": "sub1",
                    "orgUnitUuid": "uuid1"
                },
                {
                    "name": "Sub2",
                    "userId": "sub2",
                    "orgUnitUuid": "uuid2"
                }
            ]
        }
        """.formatted(manager.getName(), manager.getUserId());

		this.mockMvc.perform(post("/api/manager")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(content().string("Angiv én og kun én stedfortræder når der tilføjes"));
	}

	/**
	 * Tests that a manager substitute can be removed successfully.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Substitute relationship is removed from database
	 * - Request accepts exactly one substitute in the array
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully remove manager substitute assignment")
	void testRemoveManagerSubstituteAssignment() throws Exception {
		User manager = userService.findManagers().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No manager found in database"));

		if (manager.getManagerSubstitutes() == null) {
			manager.setManagerSubstitutes(new ArrayList<>());
		}

		User substitute = userService.getByUserId(username);
		assertNotNull(substitute, "Substitute user should exist");

		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		ManagerSubstitute existingSubstitute = new ManagerSubstitute();
		existingSubstitute.setManager(manager);
		existingSubstitute.setSubstitute(substitute);
		existingSubstitute.setOrgUnit(orgUnit);
		existingSubstitute.setAssignedTts(new Date());

		manager.getManagerSubstitutes().add(existingSubstitute);
		userService.save(manager);
		entityManager.flush();
		entityManager.clear();

		String requestBody = """
        {
            "name": "%s",
            "userId": "%s",
            "substitutes": [
                {
                    "name": "%s",
                    "userId": "%s",
                    "orgUnitUuid": "%s"
                }
            ]
        }
        """.formatted(manager.getName(), manager.getUserId(),
			substitute.getName(), substitute.getUserId(), orgUnit.getUuid());

		this.mockMvc.perform(delete("/api/manager")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("manager-substitute-remove",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				requestFields(
					fieldWithPath("name").type(JsonFieldType.STRING).description("Name of the manager"),
					fieldWithPath("userId").type(JsonFieldType.STRING).description("User ID of the manager"),
					fieldWithPath("substitutes").type(JsonFieldType.ARRAY).description("List with exactly one substitute to remove"),
					fieldWithPath("substitutes[].name").type(JsonFieldType.STRING).description("Name of the substitute"),
					fieldWithPath("substitutes[].userId").type(JsonFieldType.STRING).description("User ID of the substitute"),
					fieldWithPath("substitutes[].orgUnitUuid").type(JsonFieldType.STRING).description("UUID of the organization unit")
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
	 * Tests that removing a substitute for non-existent manager returns 404.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 404 Not Found when manager doesn't exist
	 * - Error message indicates invalid userId
	 * - Validation works correctly for remove operations
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when removing substitute for non-existent manager")
	void testRemoveManagerSubstituteAssignment_ManagerNotFound() throws Exception {
		String requestBody = """
        {
            "name": "Non Existent",
            "userId": "nonexistent123",
            "substitutes": [
                {
                    "name": "Test User",
                    "userId": "testuser",
                    "orgUnitUuid": "test-uuid"
                }
            ]
        }
        """;

		this.mockMvc.perform(delete("/api/manager")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound())
			.andExpect(content().string("Det angivne userId peger ikke på en bruger"));
	}

	/**
	 * Tests that removing multiple substitutes at once is rejected.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 400 Bad Request with multiple substitutes
	 * - Only one substitute can be removed per request
	 * - Error message indicates single substitute requirement
	 * </p>
	 * <p>
	 * Note: The API requires exactly one substitute in the array when removing.
	 * This is a business rule to ensure atomic operations.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 400 when attempting to remove multiple substitutes at once")
	void testRemoveManagerSubstituteAssignment_InvalidSubstituteCount() throws Exception {
		User manager = userService.findManagers().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No manager found in database"));

		String requestBody = """
        {
            "name": "%s",
            "userId": "%s",
            "substitutes": [
                {
                    "name": "Sub1",
                    "userId": "sub1",
                    "orgUnitUuid": "uuid1"
                },
                {
                    "name": "Sub2",
                    "userId": "sub2",
                    "orgUnitUuid": "uuid2"
                }
            ]
        }
        """.formatted(manager.getName(), manager.getUserId());

		this.mockMvc.perform(delete("/api/manager")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(content().string("Angiv én og kun én stedfortræder når der fjernes"));
	}

	/**
	 * Tests that attempting to remove with empty substitutes array is rejected.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 400 Bad Request with empty substitutes array
	 * - At least one substitute must be specified for removal
	 * - Error message indicates single substitute requirement
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 400 when attempting to remove with empty substitutes array")
	void testRemoveManagerSubstituteAssignment_EmptyArray() throws Exception {
		User manager = userService.findManagers().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No manager found in database"));

		String requestBody = """
       {
           "name": "%s",
           "userId": "%s",
           "substitutes": []
       }
       """.formatted(manager.getName(), manager.getUserId());

		this.mockMvc.perform(delete("/api/manager")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest())
			.andExpect(content().string("Angiv én og kun én stedfortræder når der fjernes"));
	}
}
