package dk.digitalidentity.rc.controller.api.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentCalculator;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentService;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for User API v2 endpoints.
 * <p>
 * Tests API endpoints for retrieving user role assignments, including detailed information
 * about how roles were assigned (direct, through position, role group, or title) and the
 * associated system roles and constraints.
 * </p>
 * <p>
 * Note: All tests verify both HTTP status and REST documentation generation.
 * </p>
 */
@DisplayName("User API v2 Tests")
public class UserApiV2Test extends AbstractApiTest {

	@Value("${tests.username}")
	private String username;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private CurrentAssignmentCalculator calculator;

	@Autowired
	private CurrentAssignmentService currentAssignmentService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.ROLE_MANAGEMENT.toString());
	}

	/**
	 * Sets up current assignments for the test user by assigning a role and calculating
	 * the resulting assignments. This is needed because the V2 endpoint reads from the
	 * CurrentAssignment table rather than computing assignments on the fly.
	 */
	@BeforeEach
	void setUpAssignments() {
		User user = userService.getByUserId(username);
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		userService.addUserRole(user, userRole, null, null);
		entityManager.flush();
		entityManager.clear();

		user = userService.getByUserId(username);
		var assignments = calculator.calculateAllAssignmentsForUser(user);
		currentAssignmentService.saveAll(user, assignments.getLeft());
		entityManager.flush();
		entityManager.clear();
	}

	/**
	 * Tests retrieval of all role assignments for a user.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is a non-empty array of assignments
	 * - Each assignment contains user, userRole, and assignedThrough fields
	 * - The assigned user role is present in the response
	 * - REST documentation is generated with full field descriptions
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should return all role assignments for user")
	void testGetUserAssignments() throws Exception {
		User user = userService.getByUserId(username);
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		ObjectMapper mapper = new ObjectMapper();
		MvcResult result = this.mockMvc.perform(get("/api/v2/user/{userid}/assignments", user.getUserId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andDo(document("user-v2-assignments",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userid").description("User ID or external UUID of the user")
				),
				responseFields(
					fieldWithPath("[]").description("Array of user role assignments"),
					fieldWithPath("[].user").type(JsonFieldType.OBJECT).description("User information"),
					fieldWithPath("[].user.uuid").type(JsonFieldType.STRING).description("UUID of the user"),
					fieldWithPath("[].user.userId").type(JsonFieldType.STRING).description("User ID"),
					fieldWithPath("[].user.name").type(JsonFieldType.STRING).description("Name of the user"),
					fieldWithPath("[].postponedConstraints").type(JsonFieldType.ARRAY).description("Postponed constraints"),
					fieldWithPath("[].userRole").type(JsonFieldType.OBJECT).description("User role information"),
					fieldWithPath("[].userRole.id").type(JsonFieldType.NUMBER).description("ID of the user role"),
					fieldWithPath("[].userRole.name").type(JsonFieldType.STRING).description("Name of the user role"),
					fieldWithPath("[].userRole.identifier").type(JsonFieldType.STRING).description("Identifier of the user role"),
					fieldWithPath("[].userRole.description").type(JsonFieldType.STRING).description("Description of the user role").optional(),
					fieldWithPath("[].userRole.delegatedFromCvr").type(JsonFieldType.STRING).description("Delegated from CVR").optional(),
					fieldWithPath("[].userRole.userOnly").type(JsonFieldType.BOOLEAN).description("Whether role is user only"),
					fieldWithPath("[].userRole.canRequest").type(JsonFieldType.BOOLEAN).description("Whether role can be requested"),
					fieldWithPath("[].userRole.sensitiveRole").type(JsonFieldType.BOOLEAN).description("Whether role is sensitive"),
					fieldWithPath("[].userRole.itSystemId").type(JsonFieldType.NUMBER).description("IT system ID"),
					fieldWithPath("[].userRole.systemRoleAssignments").type(JsonFieldType.ARRAY).description("System role assignments"),
					fieldWithPath("[].userRole.systemRoleAssignments[].systemRoleId").type(JsonFieldType.NUMBER).description("System role ID").optional(),
					fieldWithPath("[].userRole.systemRoleAssignments[].systemRoleIdentifier").type(JsonFieldType.STRING).description("System role identifier").optional(),
					fieldWithPath("[].userRole.systemRoleAssignments[].constraintValues").type(JsonFieldType.ARRAY).description("Constraint values").optional(),
					fieldWithPath("[].userRole.requesterPermission").type(JsonFieldType.ARRAY).description("Requester permission").optional(),
					fieldWithPath("[].userRole.approverPermission").type(JsonFieldType.ARRAY).description("Approver permission").optional(),
					fieldWithPath("[].userRole.contactEmail").type(JsonFieldType.STRING).description("Contact email for the user role").optional(),
					fieldWithPath("[].userRole.ouFilterEnabled").type(JsonFieldType.BOOLEAN).description("Whether OU filter is enabled"),
					fieldWithPath("[].userRole.orgUnitFilterOrgUnits").type(JsonFieldType.ARRAY).description("Org units for OU filter"),
					fieldWithPath("[].userRole.roleAssignmentAttestationByAttestationResponsible").type(JsonFieldType.BOOLEAN).description("Whether role assignment attestation is by attestation responsible"),
					fieldWithPath("[].userRole.extraSensitiveRole").type(JsonFieldType.BOOLEAN).description("Whether role is extra sensitive"),
					fieldWithPath("[].userRole.allowPostponing").type(JsonFieldType.BOOLEAN).description("Whether postponing is allowed"),
					fieldWithPath("[].responsibleOrgUnit").type(JsonFieldType.OBJECT).description("Responsible org unit").optional(),
					fieldWithPath("[].responsibleOrgUnit.uuid").type(JsonFieldType.STRING).description("UUID of the responsible org unit").optional(),
					fieldWithPath("[].responsibleOrgUnit.name").type(JsonFieldType.STRING).description("Name of the responsible org unit").optional(),
					fieldWithPath("[].assignedThroughTitle").type(JsonFieldType.OBJECT).description("Title through which role was assigned").optional(),
					fieldWithPath("[].assignedThroughTitle.uuid").type(JsonFieldType.STRING).description("UUID of the title").optional(),
					fieldWithPath("[].assignedThroughTitle.name").type(JsonFieldType.STRING).description("Name of the title").optional(),
					fieldWithPath("[].assignedThrough").type(JsonFieldType.STRING).description("How the role was assigned (e.g., DIRECT, POSITION, ROLEGROUP, TITLE)")
				)
			))
			.andReturn();

		JsonNode responseArray = mapper.readTree(result.getResponse().getContentAsString());
		boolean foundAssignedRole = false;
		for (JsonNode entry : responseArray) {
			assertThat(entry.has("user")).isTrue();
			assertThat(entry.get("user").get("userId").asText()).isEqualTo(user.getUserId());
			assertThat(entry.has("userRole")).isTrue();
			assertThat(entry.has("assignedThrough")).isTrue();

			if (entry.get("userRole").get("id").asLong() == userRole.getId()) {
				foundAssignedRole = true;
				assertThat(entry.get("userRole").get("name").asText()).isEqualTo(userRole.getName());
				assertThat(entry.get("assignedThrough").asText()).isEqualTo("DIRECT");
			}
		}
		assertThat(foundAssignedRole)
			.as("Expected user role %d (%s) in response", userRole.getId(), userRole.getName())
			.isTrue();
	}

	/**
	 * Tests that user can be looked up by external UUID instead of user ID.
	 * <p>
	 * The endpoint supports both userId and extUuid for user identification.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should return assignments when looking up user by external UUID")
	void testGetUserAssignments_ByExtUuid() throws Exception {
		User user = userService.getByUserId(username);

		this.mockMvc.perform(get("/api/v2/user/{userid}/assignments", user.getExtUuid())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andExpect(jsonPath("$[0].user.userId").value(user.getUserId()));
	}

	/**
	 * Tests filtering user assignments by IT system.
	 * <p>
	 * Verifies that:
	 * - Endpoint accepts system query parameter
	 * - Response only contains assignments from the specified IT system
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should return user assignments filtered by IT system")
	void testGetUserAssignments_WithSystemFilter() throws Exception {
		User user = userService.getByUserId(username);
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		ObjectMapper mapper = new ObjectMapper();
		MvcResult result = this.mockMvc.perform(get("/api/v2/user/{userid}/assignments", user.getUserId())
				.header("ApiKey", API_KEY)
				.param("system", itSystem.getIdentifier()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("user-v2-assignments-system",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userid").description("User ID or external UUID of the user")
				),
				queryParameters(
					parameterWithName("system").description("IT system identifier to filter by (optional)")
				)
			))
			.andReturn();

		JsonNode responseArray = mapper.readTree(result.getResponse().getContentAsString());
		for (JsonNode entry : responseArray) {
			long itSystemId = entry.get("userRole").get("itSystemId").asLong();
			assertThat(itSystemId)
				.as("All assignments should belong to IT system %s (id=%d)", itSystem.getIdentifier(), itSystem.getId())
				.isEqualTo(itSystem.getId());
		}
	}

	/**
	 * Tests that filtering by a non-existent system returns an empty array rather than an error.
	 * <p>
	 * When the system identifier doesn't match any IT system, the API returns all IT systems
	 * (as findByAnyIdentifier falls back to getAll when no match is found).
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should return assignments when system filter has no match")
	void testGetUserAssignments_WithNonexistentSystemFilter() throws Exception {
		User user = userService.getByUserId(username);

		this.mockMvc.perform(get("/api/v2/user/{userid}/assignments", user.getUserId())
				.header("ApiKey", API_KEY)
				.param("system", "NONEXISTENT_SYSTEM"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray());
	}

	/**
	 * Tests that requesting assignments for a non-existent user returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when user does not exist")
	void testGetUserAssignments_UserNotFound() throws Exception {
		this.mockMvc.perform(get("/api/v2/user/{userid}/assignments", "nonexistent-user")
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that requesting assignments with a non-existent domain filter returns 404.
	 * <p>
	 * When filtering by domain (for AD systems), the domain must exist in the system.
	 * This test verifies proper error handling for invalid domain filters.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should return 404 when domain filter does not exist")
	void testGetUserAssignments_DomainNotFound() throws Exception {
		User user = userService.getByUserId(username);

		this.mockMvc.perform(get("/api/v2/user/{userid}/assignments", user.getUserId())
				.header("ApiKey", API_KEY)
				.param("domain", "nonexistent-domain"))
			.andExpect(status().isNotFound());
	}
}
