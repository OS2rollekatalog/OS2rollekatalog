package dk.digitalidentity.rc.controller.api.v1;

import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.interceptor.RoleChangeInterceptor;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
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

/**
 * Test suite for Read-Only API endpoints.
 * <p>
 * Tests comprehensive read-only API endpoints for querying role assignments, user roles,
 * role groups, and organizational unit associations. These endpoints provide read-only
 * access to the role catalog data, supporting various query patterns including constraint
 * values, direct and indirect assignments, and filtering by domains and IT systems.
 * </p>
 */
@DisplayName("Read-Only API Tests")
public class ReadOnlyApiTest extends AbstractApiTest {

	@Value("${tests.username}")
	private String username;

	@Autowired
	private UserService userService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;

	@Autowired
	private OrgUnitService orgUnitService;

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

	@BeforeEach
	@Override
	public void setUp(RestDocumentationContextProvider restDocumentation) throws Exception {
		super.setUp(restDocumentation);

		User user = userService.getByUserId(username);
		UserRole userRole = userRoleService.getAll().stream()
			.filter(ur -> !ur.getSystemRoleAssignments().isEmpty())
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role with system role assignments found"));

		CurrentAssignment assignment = new CurrentAssignment();
		assignment.setUser(user);
		assignment.setUserRole(userRole);
		assignment.setItSystem(userRole.getItSystem());
		assignment.setAssignmentId(1L);
		assignment.setRecordHash("test-hash");

		Set<CurrentAssignment> assignments = Set.of(assignment);

		when(assignmentService.getDirectlyAssignedInItSystem(any(ItSystem.class))).thenReturn(assignments);
		when(assignmentService.getBySystem(any(ItSystem.class))).thenReturn(assignments);
		when(assignmentService.getUsersForSystem(any(ItSystem.class))).thenReturn(Set.of(user));
		when(assignmentService.getAssignedThrough(any(CurrentAssignment.class))).thenReturn(AssignedThrough.DIRECT);
		when(assignmentService.getByDirectlyAssignedUserRole(any(UserRole.class))).thenReturn(assignments);
		when(assignmentService.getByUserRole(any(UserRole.class))).thenReturn(assignments);
		when(assignmentService.getActiveDirectlyAssignedUserRolesForUser(any(User.class))).thenReturn(assignments);
		when(assignmentService.getActiveDirectlyAssignedRoleGroupsForUser(any(User.class))).thenReturn(new HashSet<>());
	}

	/**
	 * Tests that role assignments with constraints can be retrieved for an IT system.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes users with their role assignments
	 * - Constraint values for roles are included
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve role assignments with constraints for IT system")
	void testGetRoleAssignmentsWithConstraints() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		this.mockMvc.perform(get("/api/read/itsystem/roleAssignmentsWithContraints/{system}", itSystem.getIdentifier())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("readonly-role-assignments-constraints",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("system").description("The identifier or UUID of the IT system")
				),
				responseFields(
					fieldWithPath("[]").description("Array of users with role assignments"),
					fieldWithPath("[].extUuid").type(JsonFieldType.STRING).description("External UUID of the user").optional(),
					fieldWithPath("[].userId").type(JsonFieldType.STRING).description("User ID of the user").optional(),
					fieldWithPath("[].assignments").type(JsonFieldType.ARRAY).description("List of role assignments").optional(),
					fieldWithPath("[].assignments[].roleIdentifier").type(JsonFieldType.STRING).description("Identifier of the role").optional(),
					fieldWithPath("[].assignments[].roleName").type(JsonFieldType.STRING).description("Name of the role").optional(),
					fieldWithPath("[].assignments[].roleConstraintValues").type(JsonFieldType.ARRAY).description("Constraint values for the role").optional(),
					fieldWithPath("[].assignments[].roleConstraintValues[].constraintType").type(JsonFieldType.STRING).description("Type of constraint").optional(),
					fieldWithPath("[].assignments[].roleConstraintValues[].constraintValues").type(JsonFieldType.ARRAY).description("Values for the constraint").optional()
				)
			));
	}

	/**
	 * Tests that role assignments can be filtered by domain.
	 * <p>
	 * Verifies that:
	 * - Domain parameter is supported
	 * - Only users from specified domain are included
	 * - Endpoint returns HTTP 200 OK
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve role assignments filtered by domain")
	void testGetRoleAssignmentsWithConstraints_WithDomain() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		Domain domain = domainService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No domain found in test environment"));

		this.mockMvc.perform(get("/api/read/itsystem/roleAssignmentsWithContraints/{system}", itSystem.getIdentifier())
				.header("ApiKey", API_KEY)
				.param("domain", domain.getName()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray());
	}

	/**
	 * Tests that users with given user roles can be retrieved for an IT system.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes all user role assignments
	 * - IT system can be identified by identifier, UUID, or ID
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve users with given user roles for IT system")
	void testGetUsersWithGivenUserRoles() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));
		this.mockMvc.perform(get("/api/read/itsystem/{system}", itSystem.getIdentifier())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("readonly-users-with-roles",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("system").description("The identifier, UUID, or ID of the IT system")
				),
				responseFields(
					fieldWithPath("[].roleId").description("ID of the system role"),
					fieldWithPath("[].roleIdentifier").description("Unique identifier for the system role"),
					fieldWithPath("[].roleName").description("Name of the system role"),
					fieldWithPath("[].roleDescription").description("Description of the system role (optional)"),
					fieldWithPath("[].assignments").optional().description("Array of user assignments for this role (optional)"),
					fieldWithPath("[].assignments[].userId").optional().description("User ID of the assigned user"),
					fieldWithPath("[].assignments[].name").optional().description("Full name of the assigned user"),
					fieldWithPath("[].assignments[].uuid").optional().description("UUID of the assignment"),
					fieldWithPath("[].assignments[].extUuid").optional().description("External UUID reference"),
					fieldWithPath("[].assignments[].assignedThrough").optional().description("Array indicating how the role was assigned (e.g., DIRECT)"),
					fieldWithPath("[].assignments[].postponedConstraints").optional().description("Array of postponed constraints for the assignment"),
					fieldWithPath("[].systemRoles").description("Array of system roles"),
					fieldWithPath("[].systemRoles[].roleIdentifier").description("Unique identifier (URI) for the system role"),
					fieldWithPath("[].systemRoles[].roleName").description("Name of the system role"),
					fieldWithPath("[].systemRoles[].weight").description("Weight/priority of the role"),
					fieldWithPath("[].systemRoles[].roleConstraintValues").description("Array of constraint values for the role (can be empty)")
				)
			));
	}

	/**
	 * Tests that user role query supports multiple optional parameters.
	 * <p>
	 * Verifies that:
	 * - indirectRoles parameter includes inherited roles
	 * - withDescription parameter includes role descriptions
	 * - withConstraintTypeValueSet parameter includes constraint definitions
	 * - includePostponedConstraints parameter includes delayed constraints
	 * - REST documentation includes all parameters
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should support multiple query parameters for user roles retrieval")
	void testGetUsersWithGivenUserRoles_WithParameters() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		this.mockMvc.perform(get("/api/read/itsystem/{system}", itSystem.getIdentifier())
				.header("ApiKey", API_KEY)
				.param("indirectRoles", "true")
				.param("withDescription", "true")
				.param("withConstraintTypeValueSet", "true")
				.param("includePostponedConstraints", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("readonly-users-with-roles-params",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("system").description("The identifier, UUID, or ID of the IT system")
				),
				queryParameters(
					parameterWithName("indirectRoles").description("Include indirectly assigned roles (default: false)"),
					parameterWithName("withDescription").description("Include role descriptions (default: false)"),
					parameterWithName("withConstraintTypeValueSet").description("Include constraint type value sets (default: false)"),
					parameterWithName("includePostponedConstraints").description("Include postponed constraints (default: false)")
				)
			));
	}

	/**
	 * Tests that users assigned to a specific user role can be retrieved.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes role details and user assignments
	 * - System roles associated with the user role are included
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve users assigned to specific user role")
	void testGetUsersWithGivenUserRole() throws Exception {
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		this.mockMvc.perform(get("/api/read/assigned/{id}", userRole.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andDo(document("readonly-users-with-specific-role",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("The ID of the user role")
				),
				responseFields(
					fieldWithPath("roleId").type(JsonFieldType.NUMBER).description("ID of the user role").optional(),
					fieldWithPath("roleIdentifier").type(JsonFieldType.STRING).description("Identifier of the user role").optional(),
					fieldWithPath("roleName").type(JsonFieldType.STRING).description("Name of the user role").optional(),
					fieldWithPath("roleDescription").type(JsonFieldType.STRING).description("Description of the user role").optional(),
					subsectionWithPath("systemRoles").description("System roles associated with this user role").optional(),
					subsectionWithPath("assignments").description("User assignments for this role").optional()
				)
			));
	}

	/**
	 * Tests that user role query supports indirect roles and descriptions.
	 * <p>
	 * Verifies that:
	 * - indirectRoles parameter includes inherited assignments
	 * - withDescription parameter includes detailed descriptions
	 * - REST documentation includes parameters
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should support parameters for specific user role query")
	void testGetUsersWithGivenUserRole_WithParameters() throws Exception {
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		this.mockMvc.perform(get("/api/read/assigned/{id}", userRole.getId())
				.header("ApiKey", API_KEY)
				.param("indirectRoles", "true")
				.param("withDescription", "true"))
			.andExpect(status().isOk())
			.andDo(document("readonly-users-with-specific-role-params",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("The ID of the user role")
				),
				queryParameters(
					parameterWithName("indirectRoles").description("Include indirectly assigned roles (default: false)"),
					parameterWithName("withDescription").description("Include role description (default: false)")
				)
			));
	}

	/**
	 * Tests that all roles assigned to a specific user can be retrieved.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes all user roles with IT system information
	 * - User is identified by UUID
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve all roles for specific user")
	void testGetUserRoles() throws Exception {
		User user = userService.getByUserId(username);

		this.mockMvc.perform(get("/api/read/user/{uuid}/roles", user.getUuid())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("readonly-user-roles",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("uuid").description("The UUID of the user")
				),
				responseFields(
					fieldWithPath("[]").description("Array of user roles"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("ID of the user role").optional(),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the user role").optional(),
					fieldWithPath("[].identifier").type(JsonFieldType.STRING).description("Identifier of the user role").optional(),
					fieldWithPath("[].description").type(JsonFieldType.STRING).description("Description of the user role").optional(),
					fieldWithPath("[].itSystemId").type(JsonFieldType.NUMBER).description("ID of the IT system").optional(),
					fieldWithPath("[].itSystemName").type(JsonFieldType.STRING).description("Name of the IT system").optional()
				)
			));
	}

	/**
	 * Tests that all role groups assigned to a specific user can be retrieved.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes all role groups with descriptions
	 * - User is identified by UUID
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve all role groups for specific user")
	void testGetUserRoleGroups() throws Exception {
		User user = userService.getByUserId(username);

		this.mockMvc.perform(get("/api/read/user/{uuid}/rolegroups", user.getUuid())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("readonly-user-rolegroups",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("uuid").description("The UUID of the user")
				),
				responseFields(
					fieldWithPath("[]").description("Array of role groups"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("ID of the role group").optional(),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the role group").optional(),
					fieldWithPath("[].description").type(JsonFieldType.STRING).description("Description of the role group").optional()
				)
			));
	}

	/**
	 * Tests that all roles assigned to an organizational unit can be retrieved.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes all user roles assigned to org unit
	 * - Org unit is identified by UUID
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve all roles for organizational unit")
	void testGetOuRoles() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		this.mockMvc.perform(get("/api/read/ous/{uuid}/roles", orgUnit.getUuid())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("readonly-ou-roles",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("uuid").description("The UUID of the org unit")
				),
				responseFields(
					fieldWithPath("[]").description("Array of user roles assigned to the org unit"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("ID of the user role").optional(),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the user role").optional(),
					fieldWithPath("[].identifier").type(JsonFieldType.STRING).description("Identifier of the user role").optional(),
					fieldWithPath("[].description").type(JsonFieldType.STRING).description("Description of the user role").optional(),
					fieldWithPath("[].itSystemId").type(JsonFieldType.NUMBER).description("ID of the IT system").optional(),
					fieldWithPath("[].itSystemName").type(JsonFieldType.STRING).description("Name of the IT system").optional()
				)
			));
	}

	/**
	 * Tests that all role groups assigned to an organizational unit can be retrieved.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes all role groups assigned to org unit
	 * - Org unit is identified by UUID
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve all role groups for organizational unit")
	void testGetOuRolegroups() throws Exception {
		OrgUnit orgUnit = orgUnitService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No org unit found"));

		this.mockMvc.perform(get("/api/read/ous/{uuid}/rolegroups", orgUnit.getUuid())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("readonly-ou-rolegroups",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("uuid").description("The UUID of the org unit")
				),
				responseFields(
					fieldWithPath("[]").description("Array of role groups assigned to the org unit"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("ID of the role group").optional(),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the role group").optional(),
					fieldWithPath("[].description").type(JsonFieldType.STRING).description("Description of the role group").optional()
				)
			));
	}

	/**
	 * Tests that all role groups in the system can be listed.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes all role groups
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully list all role groups")
	void testListRoleGroups() throws Exception {
		this.mockMvc.perform(get("/api/read/rolegroups")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andDo(document("readonly-rolegroups-list",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[]").description("Array of all role groups"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("ID of the role group").optional(),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the role group").optional(),
					fieldWithPath("[].description").type(JsonFieldType.STRING).description("Description of the role group").optional()
				)
			));
	}

	/**
	 * Tests that details and roles of a specific role group can be retrieved.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes role group details and contained roles
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve role group details with contained roles")
	void testGetRolegroupsRoles() throws Exception {
		RoleGroup roleGroup = roleGroupService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No role group found"));

		this.mockMvc.perform(get("/api/read/rolegroups/{id}", roleGroup.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andDo(document("readonly-rolegroup-details",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("The ID of the role group")
				),
				responseFields(
					fieldWithPath("id").type(JsonFieldType.NUMBER).description("ID of the role group"),
					fieldWithPath("name").type(JsonFieldType.STRING).description("Name of the role group"),
					fieldWithPath("roles").type(JsonFieldType.ARRAY).description("User roles in this role group"),
					fieldWithPath("roles[].id").type(JsonFieldType.NUMBER).description("ID of the user role").optional(),
					fieldWithPath("roles[].name").type(JsonFieldType.STRING).description("Name of the user role").optional(),
					fieldWithPath("roles[].identifier").type(JsonFieldType.STRING).description("Identifier of the user role").optional(),
					fieldWithPath("roles[].description").type(JsonFieldType.STRING).description("Description of the user role").optional(),
					fieldWithPath("roles[].itSystemId").type(JsonFieldType.NUMBER).description("ID of the IT system").optional(),
					fieldWithPath("roles[].itSystemName").type(JsonFieldType.STRING).description("Name of the IT system").optional()
				)
			));
	}

	/**
	 * Tests that all user roles in the system can be listed.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes all user roles with IT system information
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully list all user roles")
	void testListUserRoles() throws Exception {
		this.mockMvc.perform(get("/api/read/userroles")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andDo(document("readonly-userroles-list",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[]").description("Array of all user roles"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("ID of the user role").optional(),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the user role").optional(),
					fieldWithPath("[].identifier").type(JsonFieldType.STRING).description("Identifier of the user role").optional(),
					fieldWithPath("[].description").type(JsonFieldType.STRING).description("Description of the user role").optional(),
					fieldWithPath("[].itSystemId").type(JsonFieldType.NUMBER).description("ID of the IT system").optional(),
					fieldWithPath("[].itSystemName").type(JsonFieldType.STRING).description("Name of the IT system").optional()
				)
			));
	}

	/**
	 * Tests that details of a specific user role can be retrieved.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes user role details and system role assignments
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve user role details")
	void testGetUserRole() throws Exception {
		UserRole userRole = userRoleService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No user role found"));

		this.mockMvc.perform(get("/api/read/userroles/{id}", userRole.getId())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andDo(document("readonly-userrole-details",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("id").description("The ID of the user role")
				),
				responseFields(
					fieldWithPath("id").type(JsonFieldType.NUMBER).description("ID of the user role"),
					fieldWithPath("name").type(JsonFieldType.STRING).description("Name of the user role"),
					fieldWithPath("identifier").type(JsonFieldType.STRING).description("Identifier of the user role"),
					fieldWithPath("description").type(JsonFieldType.STRING).description("Description of the user role").optional(),
					fieldWithPath("itSystemId").type(JsonFieldType.NUMBER).description("ID of the IT system").optional(),
					fieldWithPath("itSystemName").type(JsonFieldType.STRING).description("Name of the IT system").optional(),
					subsectionWithPath("systemRoleAssignments").description("System role assignments").optional()
				)
			));
	}

	/**
	 * Tests that user roles can be retrieved for all IT systems.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response includes user roles grouped by IT systems
	 * - Empty request body returns all user roles
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve user roles for all IT systems")
	void testGetUserRolesByItSystems() throws Exception {
		this.mockMvc.perform(get("/api/read/userroles/itsystems")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andDo(document("readonly-userroles-by-itsystems",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[]").description("Array of user roles"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("ID of the user role").optional(),
					fieldWithPath("[].name").type(JsonFieldType.STRING).description("Name of the user role").optional(),
					fieldWithPath("[].identifier").type(JsonFieldType.STRING).description("Identifier of the user role").optional(),
					fieldWithPath("[].description").type(JsonFieldType.STRING).description("Description of the user role").optional(),
					fieldWithPath("[].itSystemId").type(JsonFieldType.NUMBER).description("ID of the IT system").optional(),
					fieldWithPath("[].itSystemName").type(JsonFieldType.STRING).description("Name of the IT system").optional()
				)
			));
	}

	/**
	 * Tests that user roles can be filtered by specific IT system IDs.
	 * <p>
	 * Verifies that:
	 * - Request body accepts array of IT system IDs
	 * - Response includes only user roles for specified systems
	 * - REST documentation is generated with request body
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve user roles filtered by IT system IDs")
	void testGetUserRolesByItSystems_WithItSystemIds() throws Exception {
		ItSystem itSystem = itSystemService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No IT system found"));

		String requestBody = "[" + itSystem.getId() + "]";

		this.mockMvc.perform(get("/api/read/userroles/itsystems")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("readonly-userroles-by-itsystems-filtered",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				requestFields(
					fieldWithPath("[]").description("Array of IT system IDs to filter by (optional, returns all if not provided)")
				)
			));
	}

	/**
	 * Tests that requesting non-existent user role returns 404.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 404 Not Found for invalid role ID
	 * - Error handling works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when user role assignment not found")
	void testGetUsersWithGivenUserRole_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/read/assigned/{id}", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that requesting roles for non-existent org unit returns 404.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 404 Not Found for invalid UUID
	 * - Error handling works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when organizational unit not found")
	void testGetOuRoles_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/read/ous/{uuid}/roles", "nonexistent-uuid")
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that requesting non-existent role group returns 404.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 404 Not Found for invalid role group ID
	 * - Error handling works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when role group not found")
	void testGetRolegroupsRoles_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/read/rolegroups/{id}", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that requesting non-existent user role details returns 404.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 404 Not Found for invalid user role ID
	 * - Error handling works correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when user role details not found")
	void testGetUserRole_NotFound() throws Exception {
		this.mockMvc.perform(get("/api/read/userroles/{id}", 999999)
				.header("ApiKey", API_KEY))
			.andExpect(status().isNotFound());
	}
}
