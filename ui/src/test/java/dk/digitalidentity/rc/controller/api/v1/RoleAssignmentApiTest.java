package dk.digitalidentity.rc.controller.api.v1;

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
import dk.digitalidentity.rc.util.BootstrapDevMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.restdocs.RestDocumentationContextProvider;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for Role Assignment API endpoints.
 * <p>
 * Tests API endpoints for assigning and removing user roles and role groups to/from
 * both individual users and organizational units. Users can be identified by UUID or UserId, and assignments can
 * be made at both the user and organizational unit level.
 * </p>
 */
@DisplayName("Role Assignment API Tests")
public class RoleAssignmentApiTest extends AbstractApiTest {
	private static final String TEST_USER_UUID = BootstrapDevMode.userUUID;
	private static final String TEST_ORG_UNIT_UUID = BootstrapDevMode.orgUnitUUID;

	@Autowired
	private UserService userService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.ROLE_MANAGEMENT.toString());
	}

	/**
	 * Additional setup to create test data for role assignment operations.
	 * <p>
	 * Pre-assigns some roles and role groups to test user and org unit to ensure
	 * proper test environment. This allows testing both assignment and removal
	 * operations.
	 * </p>
	 *
	 * @param restDocumentation REST documentation context provider
	 * @throws Exception if setup fails
	 */
	@BeforeEach
	@Override
	public void setUp(RestDocumentationContextProvider restDocumentation) throws Exception {
		super.setUp(restDocumentation);

		UserRole userRole = userRoleService.getAll().get(2);
		RoleGroup roleGroup = roleGroupService.getAll().get(0);

		User user = userService.getByExtUuid(TEST_USER_UUID).get(0);
		userService.addRoleGroup(user, roleGroup, null, null, null, null);
		userService.addUserRole(user, userRole, null, null);

		OrgUnit ou = orgUnitService.getByUuid(TEST_ORG_UNIT_UUID);
		orgUnitService.addUserRole(ou, userRole, false, null, null, new HashSet<>(), new HashSet<>());
		orgUnitService.addRoleGroup(ou, roleGroup, false, null, null, new HashSet<>(), new HashSet<>());
	}

	/**
	 * Tests that a user role can be assigned to a user.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - User role is assigned successfully
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully assign user role to user")
	public void assignRole() throws Exception {
		long userRoleId = userRoleService.getAll().get(2).getId();
		String id = Long.toString(userRoleId);

		this.mockMvc.perform(put("/api/user/{userUuid}/assign/userrole/{userRoleId}", TEST_USER_UUID, id)
				.header("ApiKey", API_KEY))
			.andExpect(status().is(200))
			.andDo(document("assign-user-role", preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userUuid").description("The user UUID or UserId"),
					parameterWithName("userRoleId").description("The role id")
				)
			));

		entityManager.flush();
		entityManager.clear();
		User updatedUser = userService.getByExtUuid(TEST_USER_UUID).get(0);
		assertThat(updatedUser.getUserRoleAssignments())
			.anyMatch(a -> a.getUserRole().getId() == userRoleId);
	}

	/**
	 * Tests that a user role can be removed from a user.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - User role assignment is removed successfully
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully remove user role from user")
	public void deassignRole() throws Exception {
		long userRoleId = userRoleService.getAll().get(2).getId();
		String id = Long.toString(userRoleId);

		this.mockMvc.perform(delete("/api/user/{userUuid}/deassign/userrole/{userRoleId}", TEST_USER_UUID, id)
				.header("ApiKey", API_KEY))
			.andExpect(status().is(200))
			.andDo(document("deassign-user-role", preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userUuid").description("The user UUID or UserId"),
					parameterWithName("userRoleId").description("The role id")
				)
			));

		entityManager.flush();
		entityManager.clear();
		User updatedUser = userService.getByExtUuid(TEST_USER_UUID).get(0);
		assertThat(updatedUser.getUserRoleAssignments())
			.noneMatch(a -> a.getUserRole().getId() == userRoleId);
	}

	/**
	 * Tests that a role group can be assigned to a user.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Role group (containing multiple roles) is assigned successfully
	 * - REST documentation is generated
	 * </p>>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully assign role group to user")
	public void assignRoleGroupToUser() throws Exception {
		long roleGroupId = roleGroupService.getAll().get(0).getId();
		String id = Long.toString(roleGroupId);

		this.mockMvc.perform(put("/api/user/{userUuid}/assign/rolegroup/{roleGroupId}", TEST_USER_UUID, id)
				.header("ApiKey", API_KEY))
			.andExpect(status().is(200))
			.andDo(document("assign-user-rolegroup", preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userUuid").description("The user UUID or UserId"),
					parameterWithName("roleGroupId").description("The rolegroup id")
				)
			));

		entityManager.flush();
		entityManager.clear();
		User updatedUser = userService.getByExtUuid(TEST_USER_UUID).get(0);
		assertThat(updatedUser.getRoleGroupAssignments())
			.anyMatch(a -> a.getRoleGroup().getId() == roleGroupId);
	}

	/**
	 * Tests that a role group can be removed from a user.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Role group assignment is removed successfully
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully remove role group from user")
	public void deassignRoleGroupFromUser() throws Exception {
		long roleGroupId = roleGroupService.getAll().get(0).getId();
		String id = Long.toString(roleGroupId);

		this.mockMvc.perform(delete("/api/user/{userUuid}/deassign/rolegroup/{roleGroupId}", TEST_USER_UUID, id)
				.header("ApiKey", API_KEY))
			.andExpect(status().is(200))
			.andDo(document("deassign-user-rolegroup", preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("userUuid").description("The user UUID or UserId"),
					parameterWithName("roleGroupId").description("The rolegroup id")
				)
			));

		entityManager.flush();
		entityManager.clear();
		User updatedUser = userService.getByExtUuid(TEST_USER_UUID).get(0);
		assertThat(updatedUser.getRoleGroupAssignments())
			.noneMatch(a -> a.getRoleGroup().getId() == roleGroupId);
	}

	/**
	 * Tests that a user role can be assigned to an organizational unit.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - User role is assigned to org unit successfully
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully assign user role to organizational unit")
	public void assignRoleToOrgUnit() throws Exception {
		long userRoleId = userRoleService.getAll().get(0).getId();
		String id = Long.toString(userRoleId);

		this.mockMvc.perform(put("/api/ou/{ouUuid}/assign/userrole/{userRoleId}", TEST_ORG_UNIT_UUID, id)
				.header("ApiKey", API_KEY))
			.andExpect(status().is(200))
			.andDo(document("assign-ou-role", preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("ouUuid").description("The Organisational Unit UUID"),
					parameterWithName("userRoleId").description("The role id")
				)
			));

		entityManager.flush();
		entityManager.clear();
		OrgUnit updatedOu = orgUnitService.getByUuid(TEST_ORG_UNIT_UUID);
		assertThat(updatedOu.getUserRoleAssignments())
			.anyMatch(a -> a.getUserRole().getId() == userRoleId);
	}

	/**
	 * Tests that a user role can be removed from an organizational unit.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - User role assignment is removed from org unit successfully
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully remove user role from organizational unit")
	public void deassignRoleFromOrgUnit() throws Exception {
		long userRoleId = userRoleService.getAll().get(2).getId();
		String id = Long.toString(userRoleId);

		this.mockMvc.perform(delete("/api/ou/{ouUuid}/deassign/userrole/{userRoleId}", TEST_ORG_UNIT_UUID, id)
				.header("ApiKey", API_KEY))
			.andExpect(status().is(200))
			.andDo(document("deassign-ou-role", preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("ouUuid").description("The Organisational Unit UUID"),
					parameterWithName("userRoleId").description("The role id")
				)
			));

		entityManager.flush();
		entityManager.clear();
		OrgUnit updatedOu = orgUnitService.getByUuid(TEST_ORG_UNIT_UUID);
		assertThat(updatedOu.getUserRoleAssignments())
			.noneMatch(a -> a.getUserRole().getId() == userRoleId);
	}

	/**
	 * Tests that a role group can be assigned to an organizational unit.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Role group is assigned to org unit successfully
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully assign role group to organizational unit")
	public void assignRoleGroupToOrgUnit() throws Exception {
		long roleGroupId = roleGroupService.getAll().get(0).getId();
		String id = Long.toString(roleGroupId);

		this.mockMvc.perform(put("/api/ou/{ouUuid}/assign/rolegroup/{roleGroupId}", TEST_ORG_UNIT_UUID, id)
				.header("ApiKey", API_KEY))
			.andExpect(status().is(200))
			.andDo(document("assign-ou-rolegroup", preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("ouUuid").description("The Organisational Unit UUID"),
					parameterWithName("roleGroupId").description("The rolegroup id")
				)
			));

		entityManager.flush();
		entityManager.clear();
		OrgUnit updatedOu = orgUnitService.getByUuid(TEST_ORG_UNIT_UUID);
		assertThat(updatedOu.getRoleGroupAssignments())
			.anyMatch(a -> a.getRoleGroup().getId() == roleGroupId);
	}

	/**
	 * Tests that a role group can be removed from an organizational unit.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Role group assignment is removed from org unit successfully
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should successfully remove role group from organizational unit")
	public void deassignRoleGroupFromOrgUnit() throws Exception {
		long roleGroupId = roleGroupService.getAll().get(0).getId();
		String id = Long.toString(roleGroupId);

		this.mockMvc.perform(delete("/api/ou/{ouUuid}/deassign/rolegroup/{roleGroupId}", TEST_ORG_UNIT_UUID, id)
				.header("ApiKey", API_KEY))
			.andExpect(status().is(200))
			.andDo(document("deassign-ou-rolegroup", preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("ouUuid").description("The Organisational Unit UUID"),
					parameterWithName("roleGroupId").description("The rolegroup id")
				)
			));

		entityManager.flush();
		entityManager.clear();
		OrgUnit updatedOu = orgUnitService.getByUuid(TEST_ORG_UNIT_UUID);
		assertThat(updatedOu.getRoleGroupAssignments())
			.noneMatch(a -> a.getRoleGroup().getId() == roleGroupId);
	}
}
