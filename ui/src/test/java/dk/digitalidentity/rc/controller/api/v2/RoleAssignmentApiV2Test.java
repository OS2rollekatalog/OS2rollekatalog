package dk.digitalidentity.rc.controller.api.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.service.OrgUnitAssignmentService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for Role Assignment API v2 endpoints.
 * <p>
 * Tests API endpoints for managing role assignments to both individual users and
 * organizational units. Covers direct user role assignments as well as organizational
 * assignments that can be inherited by users within the org unit hierarchy.
 * </p>
 * <p>
 * Note: All tests verify both HTTP status and REST documentation generation.
 * </p>
 */
@DisplayName("Role Assignment API v2 Tests")
public class RoleAssignmentApiV2Test extends AbstractApiTest {

	@Value("${tests.username}")
	private String username;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private RoleGroupService roleGroupService;

	@Autowired
	private OrgUnitAssignmentService ouAssignmentService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.ROLE_MANAGEMENT.toString());
	}

	/**
	 * Tests assignment of a user role directly to a user.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - User role is persisted in the database
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should assign user role directly to user")
	void testAssignUserRoleToUser() throws Exception {
		User user = userService.getByUserId(username);
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));
		long userRoleId = userRole.getId();

		String requestBody = """
			{
				"startDate": null,
				"stopDate": null,
				"onlyIfNotAssigned": true,
				"domain": null,
				"postponedConstraints": []
			}
			""";

		this.mockMvc.perform(put("/api/v2/user/{userUuid}/assign/userrole/{userRoleId}", user.getExtUuid(), userRoleId)
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("roleassignment-v2-assign-user",
				preprocessRequest(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userUuid").description("UUID or user ID of the user"),
					parameterWithName("userRoleId").description("ID of the user role to assign")
				)
			));

		entityManager.flush();
		entityManager.clear();

		User updatedUser = userService.getByUserId(username);
		assertThat(updatedUser.getUserRoleAssignments())
			.anyMatch(a -> a.getUserRole().getId() == userRoleId);
	}

	/**
	 * Tests that assigning a role to a non-existent user returns 404.
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should return 404 when user does not exist")
	void testAssignUserRoleToUser_UserNotFound() throws Exception {
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		String requestBody = """
			{
				"startDate": null,
				"stopDate": null,
				"onlyIfNotAssigned": false,
				"domain": null,
				"postponedConstraints": []
			}
			""";

		this.mockMvc.perform(put("/api/v2/user/{userUuid}/assign/userrole/{userRoleId}", "nonexistent-user", userRole.getId())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that assigning a non-existent role to a user returns 404.
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should return 404 when user role does not exist")
	void testAssignUserRoleToUser_UserRoleNotFound() throws Exception {
		User user = userService.getByUserId(username);

		String requestBody = """
			{
				"startDate": null,
				"stopDate": null,
				"onlyIfNotAssigned": false,
				"domain": null,
				"postponedConstraints": []
			}
			""";

		this.mockMvc.perform(put("/api/v2/user/{userUuid}/assign/userrole/{userRoleId}", user.getExtUuid(), 999999)
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests creation of a user role assignment to an organizational unit.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 201 Created
	 * - Response contains the created assignment with correct fields
	 * - Assignment is persisted in the database
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should create user role assignment to organizational unit")
	void testCreateUserRoleAssignmentToOrgUnit() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));
		long userRoleId = userRole.getId();
		String orgUnitUuid = orgUnit.getUuid();

		String requestBody = String.format("""
			{
				"assignmentType": "USER_ROLE",
				"orgUnit": {
					"uuid": "%s"
				},
				"userRole": {
					"id": %d
				},
				"inherit": true,
				"startDate": null,
				"stopDate": null,
				"scopes": []
			}
			""", orgUnitUuid, userRoleId);

		this.mockMvc.perform(post("/api/v2/organisation/assignment/userrole")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.assignmentType").value("USER_ROLE"))
			.andExpect(jsonPath("$.inherit").value(true))
			.andExpect(jsonPath("$.orgUnit.uuid").value(orgUnitUuid))
			.andExpect(jsonPath("$.userRole.id").value(userRoleId))
			.andDo(document("roleassignment-v2-create-ou-userrole",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				)
			));

		entityManager.flush();
		entityManager.clear();

		OrgUnit updatedOu = orgUnitService.getByUuid(orgUnitUuid);
		assertThat(updatedOu.getUserRoleAssignments())
			.anyMatch(a -> a.getUserRole().getId() == userRoleId);
	}

	/**
	 * Tests that creating a user role assignment for a non-existent org unit returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when creating user role assignment for non-existent org unit")
	void testCreateUserRoleAssignmentToOrgUnit_OrgUnitNotFound() throws Exception {
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		String requestBody = String.format("""
			{
				"assignmentType": "USER_ROLE",
				"orgUnit": {
					"uuid": "nonexistent-uuid"
				},
				"userRole": {
					"id": %d
				},
				"inherit": false,
				"startDate": null,
				"stopDate": null,
				"scopes": []
			}
			""", userRole.getId());

		this.mockMvc.perform(post("/api/v2/organisation/assignment/userrole")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests creation of a role group assignment to an organizational unit.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 201 Created
	 * - Response contains the created assignment with correct fields
	 * - Assignment is persisted in the database
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should create role group assignment to organizational unit")
	void testCreateRoleGroupAssignmentToOrgUnit() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		RoleGroup roleGroup = roleGroupService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No role group found"));
		long roleGroupId = roleGroup.getId();
		String orgUnitUuid = orgUnit.getUuid();

		String requestBody = String.format("""
			{
				"assignmentType": "ROLE_GROUP",
				"orgUnit": {
					"uuid": "%s"
				},
				"roleGroup": {
					"id": %d
				},
				"inherit": false,
				"startDate": null,
				"stopDate": null,
				"scopes": []
			}
			""", orgUnitUuid, roleGroupId);

		this.mockMvc.perform(post("/api/v2/organisation/assignment/rolegroup")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.assignmentType").value("ROLE_GROUP"))
			.andExpect(jsonPath("$.inherit").value(false))
			.andExpect(jsonPath("$.orgUnit.uuid").value(orgUnitUuid))
			.andExpect(jsonPath("$.roleGroup.id").value(roleGroupId))
			.andDo(document("roleassignment-v2-create-ou-rolegroup",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				)
			));

		entityManager.flush();
		entityManager.clear();

		OrgUnit updatedOu = orgUnitService.getByUuid(orgUnitUuid);
		assertThat(updatedOu.getRoleGroupAssignments())
			.anyMatch(a -> a.getRoleGroup().getId() == roleGroupId);
	}

	/**
	 * Tests that creating a role group assignment for a non-existent org unit returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when creating role group assignment for non-existent org unit")
	void testCreateRoleGroupAssignmentToOrgUnit_OrgUnitNotFound() throws Exception {
		RoleGroup roleGroup = roleGroupService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No role group found"));

		String requestBody = String.format("""
			{
				"assignmentType": "ROLE_GROUP",
				"orgUnit": {
					"uuid": "nonexistent-uuid"
				},
				"roleGroup": {
					"id": %d
				},
				"inherit": false,
				"startDate": null,
				"stopDate": null,
				"scopes": []
			}
			""", roleGroup.getId());

		this.mockMvc.perform(post("/api/v2/organisation/assignment/rolegroup")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests retrieval of a single user role assignment by its ID.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response contains the correct assignment details
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should return single user role assignment by ID")
	void testGetOrgUnitUserRoleAssignment() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		orgUnitService.addUserRole(orgUnit, userRole, true, null, null, new HashSet<>(), new HashSet<>());
		entityManager.flush();
		entityManager.clear();

		OrgUnit refreshedOu = orgUnitService.getByUuid(orgUnit.getUuid());
		OrgUnitUserRoleAssignment assignment = refreshedOu.getUserRoleAssignments().stream()
			.filter(a -> a.getUserRole().getId() == userRole.getId())
			.findFirst()
			.orElseThrow(() -> new RuntimeException("Assignment not found after creation"));
		long assignmentId = assignment.getId();

		this.mockMvc.perform(get("/api/v2/organisation/assignment/userrole/{assignmentId}", assignmentId)
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assignmentType").value("USER_ROLE"))
			.andExpect(jsonPath("$.inherit").value(true))
			.andExpect(jsonPath("$.orgUnit.uuid").value(orgUnit.getUuid()))
			.andExpect(jsonPath("$.userRole.id").value(userRole.getId()))
			.andDo(document("roleassignment-v2-get-ou-userrole",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("assignmentId").description("ID of the user role assignment")
				)
			));
	}

	/**
	 * Tests that requesting a non-existent user role assignment returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when user role assignment does not exist")
	void testGetOrgUnitUserRoleAssignment_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/v2/organisation/assignment/userrole/{assignmentId}", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests retrieval of a single role group assignment by its ID.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response contains the correct assignment details
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should return single role group assignment by ID")
	void testGetOrgUnitRoleGroupAssignment() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));
		RoleGroup roleGroup = roleGroupService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No role group found"));

		orgUnitService.addRoleGroup(orgUnit, roleGroup, false, null, null, new HashSet<>(), new HashSet<>());
		entityManager.flush();
		entityManager.clear();

		OrgUnit refreshedOu = orgUnitService.getByUuid(orgUnit.getUuid());
		OrgUnitRoleGroupAssignment assignment = refreshedOu.getRoleGroupAssignments().stream()
			.filter(a -> a.getRoleGroup().getId() == roleGroup.getId())
			.findFirst()
			.orElseThrow(() -> new RuntimeException("Assignment not found after creation"));
		long assignmentId = assignment.getId();

		this.mockMvc.perform(get("/api/v2/organisation/assignment/rolegroup/{assignmentId}", assignmentId)
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assignmentType").value("ROLE_GROUP"))
			.andExpect(jsonPath("$.inherit").value(false))
			.andExpect(jsonPath("$.orgUnit.uuid").value(orgUnit.getUuid()))
			.andExpect(jsonPath("$.roleGroup.id").value(roleGroup.getId()))
			.andDo(document("roleassignment-v2-get-ou-rolegroup",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("assignmentId").description("ID of the role group assignment")
				)
			));
	}

	/**
	 * Tests that requesting a non-existent role group assignment returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when role group assignment does not exist")
	void testGetOrgUnitRoleGroupAssignment_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/v2/organisation/assignment/rolegroup/{assignmentId}", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests updating a user role assignment on an organizational unit.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 204 No Content
	 * - The inherit flag is updated in the database
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should update user role assignment on organizational unit")
	void testUpdateUserRoleAssignment() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		orgUnitService.addUserRole(orgUnit, userRole, false, null, null, new HashSet<>(), new HashSet<>());
		entityManager.flush();
		entityManager.clear();

		OrgUnit refreshedOu = orgUnitService.getByUuid(orgUnit.getUuid());
		OrgUnitUserRoleAssignment assignment = refreshedOu.getUserRoleAssignments().stream()
			.filter(a -> a.getUserRole().getId() == userRole.getId())
			.findFirst()
			.orElseThrow(() -> new RuntimeException("Assignment not found after creation"));
		long assignmentId = assignment.getId();

		assertThat(assignment.isInherit()).isFalse();

		String requestBody = String.format("""
			{
				"assignmentType": "USER_ROLE",
				"orgUnit": {
					"uuid": "%s"
				},
				"userRole": {
					"id": %d
				},
				"inherit": true,
				"startDate": null,
				"stopDate": null,
				"scopes": []
			}
			""", orgUnit.getUuid(), userRole.getId());

		this.mockMvc.perform(put("/api/v2/organisation/assignment/userrole/{assignmentId}", assignmentId)
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNoContent())
			.andDo(document("roleassignment-v2-update-ou-userrole",
				preprocessRequest(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("assignmentId").description("ID of the user role assignment to update")
				)
			));

		entityManager.flush();
		entityManager.clear();

		OrgUnitUserRoleAssignment updatedAssignment = ouAssignmentService.getOrgUnitUserRoleAssignment(assignmentId)
			.orElseThrow(() -> new RuntimeException("Assignment not found after update"));
		assertThat(updatedAssignment.isInherit()).isTrue();
	}

	/**
	 * Tests that updating a non-existent user role assignment returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when updating non-existent user role assignment")
	void testUpdateUserRoleAssignment_NotFound() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		String requestBody = String.format("""
			{
				"assignmentType": "USER_ROLE",
				"orgUnit": {
					"uuid": "%s"
				},
				"userRole": {
					"id": %d
				},
				"inherit": true,
				"startDate": null,
				"stopDate": null,
				"scopes": []
			}
			""", orgUnit.getUuid(), userRole.getId());

		this.mockMvc.perform(put("/api/v2/organisation/assignment/userrole/{assignmentId}", 999999)
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests updating a role group assignment on an organizational unit.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 204 No Content
	 * - The inherit flag is updated in the database
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should update role group assignment on organizational unit")
	void testUpdateRoleGroupAssignment() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));
		RoleGroup roleGroup = roleGroupService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No role group found"));

		orgUnitService.addRoleGroup(orgUnit, roleGroup, false, null, null, new HashSet<>(), new HashSet<>());
		entityManager.flush();
		entityManager.clear();

		OrgUnit refreshedOu = orgUnitService.getByUuid(orgUnit.getUuid());
		OrgUnitRoleGroupAssignment assignment = refreshedOu.getRoleGroupAssignments().stream()
			.filter(a -> a.getRoleGroup().getId() == roleGroup.getId())
			.findFirst()
			.orElseThrow(() -> new RuntimeException("Assignment not found after creation"));
		long assignmentId = assignment.getId();

		assertThat(assignment.isInherit()).isFalse();

		String requestBody = String.format("""
			{
				"assignmentType": "ROLE_GROUP",
				"orgUnit": {
					"uuid": "%s"
				},
				"roleGroup": {
					"id": %d
				},
				"inherit": true,
				"startDate": null,
				"stopDate": null,
				"scopes": []
			}
			""", orgUnit.getUuid(), roleGroup.getId());

		this.mockMvc.perform(put("/api/v2/organisation/assignment/rolegroup/{assignmentId}", assignmentId)
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNoContent())
			.andDo(document("roleassignment-v2-update-ou-rolegroup",
				preprocessRequest(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("assignmentId").description("ID of the role group assignment to update")
				)
			));

		entityManager.flush();
		entityManager.clear();

		OrgUnitRoleGroupAssignment updatedAssignment = ouAssignmentService.getOrgUnitRoleGroupAssignment(assignmentId)
			.orElseThrow(() -> new RuntimeException("Assignment not found after update"));
		assertThat(updatedAssignment.isInherit()).isTrue();
	}

	/**
	 * Tests that updating a non-existent role group assignment returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when updating non-existent role group assignment")
	void testUpdateRoleGroupAssignment_NotFound() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));
		RoleGroup roleGroup = roleGroupService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No role group found"));

		String requestBody = String.format("""
			{
				"assignmentType": "ROLE_GROUP",
				"orgUnit": {
					"uuid": "%s"
				},
				"roleGroup": {
					"id": %d
				},
				"inherit": true,
				"startDate": null,
				"stopDate": null,
				"scopes": []
			}
			""", orgUnit.getUuid(), roleGroup.getId());

		this.mockMvc.perform(put("/api/v2/organisation/assignment/rolegroup/{assignmentId}", 999999)
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests deletion of a user role assignment from an organizational unit.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 204 No Content
	 * - The assignment is removed from the org unit
	 * - The user role itself still exists
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should delete user role assignment from organizational unit")
	void testDeleteUserRoleAssignment() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		orgUnitService.addUserRole(orgUnit, userRole, false, null, null, new HashSet<>(), new HashSet<>());
		entityManager.flush();
		entityManager.clear();

		OrgUnit refreshedOu = orgUnitService.getByUuid(orgUnit.getUuid());
		OrgUnitUserRoleAssignment assignment = refreshedOu.getUserRoleAssignments().stream()
			.filter(a -> a.getUserRole().getId() == userRole.getId())
			.findFirst()
			.orElseThrow(() -> new RuntimeException("Assignment not found after creation"));
		long assignmentId = assignment.getId();
		long userRoleId = userRole.getId();

		this.mockMvc.perform(delete("/api/v2/organisation/assignment/userrole/{assignmentId}", assignmentId)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNoContent())
			.andDo(document("roleassignment-v2-delete-ou-userrole",
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("assignmentId").description("ID of the assignment to delete")
				)
			));

		entityManager.flush();
		entityManager.clear();

		assertThat(ouAssignmentService.getOrgUnitUserRoleAssignment(assignmentId)).isEmpty();
		assertThat(userRoleService.getById(userRoleId)).isNotNull();
	}

	/**
	 * Tests that deleting a non-existent user role assignment returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when deleting non-existent user role assignment")
	void testDeleteUserRoleAssignment_NotFound() throws Exception {
		this.mockMvc.perform(delete("/api/v2/organisation/assignment/userrole/{assignmentId}", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests deletion of a role group assignment from an organizational unit.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 204 No Content
	 * - The assignment is removed from the org unit
	 * - The role group itself still exists
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should delete role group assignment from organizational unit")
	void testDeleteRoleGroupAssignment() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));
		RoleGroup roleGroup = roleGroupService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No role group found"));

		orgUnitService.addRoleGroup(orgUnit, roleGroup, false, null, null, new HashSet<>(), new HashSet<>());
		entityManager.flush();
		entityManager.clear();

		OrgUnit refreshedOu = orgUnitService.getByUuid(orgUnit.getUuid());
		OrgUnitRoleGroupAssignment assignment = refreshedOu.getRoleGroupAssignments().stream()
			.filter(a -> a.getRoleGroup().getId() == roleGroup.getId())
			.findFirst()
			.orElseThrow(() -> new RuntimeException("Assignment not found after creation"));
		long assignmentId = assignment.getId();
		long roleGroupId = roleGroup.getId();

		this.mockMvc.perform(delete("/api/v2/organisation/assignment/rolegroup/{assignmentId}", assignmentId)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNoContent())
			.andDo(document("roleassignment-v2-delete-ou-rolegroup",
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("assignmentId").description("ID of the assignment to delete")
				)
			));

		entityManager.flush();
		entityManager.clear();

		assertThat(ouAssignmentService.getOrgUnitRoleGroupAssignment(assignmentId)).isEmpty();
		assertThat(roleGroupService.getById(roleGroupId)).isNotNull();
	}

	/**
	 * Tests that deleting a non-existent role group assignment returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when deleting non-existent role group assignment")
	void testDeleteRoleGroupAssignment_NotFound() throws Exception {
		this.mockMvc.perform(delete("/api/v2/organisation/assignment/rolegroup/{assignmentId}", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests retrieval of all user role assignments for an organizational unit.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array matching the expected number of assignments
	 * - Assignments contain the expected user role data
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should return user role assignments for organizational unit")
	void testListUserRoleAssignmentsForOrgUnit() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		orgUnitService.addUserRole(orgUnit, userRole, true, null, null, new HashSet<>(), new HashSet<>());
		entityManager.flush();
		entityManager.clear();

		OrgUnit refreshedOu = orgUnitService.getByUuid(orgUnit.getUuid());
		int expectedCount = refreshedOu.getUserRoleAssignments().size();

		ObjectMapper mapper = new ObjectMapper();
		MvcResult result = this.mockMvc.perform(get("/api/v2/organisation/{orgUnitUuid}/assignment/userrole", orgUnit.getUuid())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$.length()").value(expectedCount))
			.andDo(document("roleassignment-v2-list-ou-userrole",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("orgUnitUuid").description("UUID of the org unit")
				)
			))
			.andReturn();

		JsonNode responseArray = mapper.readTree(result.getResponse().getContentAsString());
		boolean foundUserRole = false;
		for (JsonNode entry : responseArray) {
			assertThat(entry.has("assignmentType")).isTrue();
			assertThat(entry.get("assignmentType").asText()).isEqualTo("USER_ROLE");
			if (entry.get("userRole").get("id").asLong() == userRole.getId()) {
				foundUserRole = true;
			}
		}
		assertThat(foundUserRole).as("Expected user role %d in response", userRole.getId()).isTrue();
	}

	/**
	 * Tests retrieval of all role group assignments for an organizational unit.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array matching the expected number of assignments
	 * - Assignments contain the expected role group data
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or test data is missing
	 */
	@Test
	@DisplayName("Should return role group assignments for organizational unit")
	void testListRoleGroupAssignmentsForOrgUnit() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));
		RoleGroup roleGroup = roleGroupService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No role group found"));

		orgUnitService.addRoleGroup(orgUnit, roleGroup, false, null, null, new HashSet<>(), new HashSet<>());
		entityManager.flush();
		entityManager.clear();

		OrgUnit refreshedOu = orgUnitService.getByUuid(orgUnit.getUuid());
		int expectedCount = refreshedOu.getRoleGroupAssignments().size();

		ObjectMapper mapper = new ObjectMapper();
		MvcResult result = this.mockMvc.perform(get("/api/v2/organisation/{orgUnitUuid}/assignment/rolegroup", orgUnit.getUuid())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$.length()").value(expectedCount))
			.andDo(document("roleassignment-v2-list-ou-rolegroup",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("orgUnitUuid").description("UUID of the org unit")
				)
			))
			.andReturn();

		JsonNode responseArray = mapper.readTree(result.getResponse().getContentAsString());
		boolean foundRoleGroup = false;
		for (JsonNode entry : responseArray) {
			assertThat(entry.has("assignmentType")).isTrue();
			assertThat(entry.get("assignmentType").asText()).isEqualTo("ROLE_GROUP");
			if (entry.get("roleGroup").get("id").asLong() == roleGroup.getId()) {
				foundRoleGroup = true;
			}
		}
		assertThat(foundRoleGroup).as("Expected role group %d in response", roleGroup.getId()).isTrue();
	}

	/**
	 * Tests that requesting user role assignments for a non-existent org unit returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when listing user role assignments for non-existent org unit")
	void testListUserRoleAssignmentsForOrgUnit_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/v2/organisation/{orgUnitUuid}/assignment/userrole", "nonexistent-uuid")
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that requesting role group assignments for a non-existent org unit returns 404.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when listing role group assignments for non-existent org unit")
	void testListRoleGroupAssignmentsForOrgUnit_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/v2/organisation/{orgUnitUuid}/assignment/rolegroup", "nonexistent-uuid")
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}
}
