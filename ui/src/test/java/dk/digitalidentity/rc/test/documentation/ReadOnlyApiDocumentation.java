package dk.digitalidentity.rc.test.documentation;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.requestParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.restdocs.JUnitRestDocumentation;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.RolePostProcessor;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.util.BootstrapDevMode;
import dk.digitalidentity.saml.model.TokenUser;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles({ "test" })
@Transactional(rollbackFor = Exception.class)
public class ReadOnlyApiDocumentation {
	private static String testUserUUID = BootstrapDevMode.userUUID;
	private static String testOrgUnitUUID = BootstrapDevMode.orgUnitUUID;
	private MockMvc mockMvc;

	@Rule
	public JUnitRestDocumentation restDocumentation = new JUnitRestDocumentation("target/generated-snippets");

    @Autowired
    private BootstrapDevMode bootstrapper;

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserService userService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;

	@Before
	public void setUp() throws Exception {
		bootstrapper.init(true);
		
		// this is a bit of a hack, but we fake that the api logged in using a token,
		// so all of our existing security code just works without further modifications
		List<SimpleGrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SimpleGrantedAuthority(Constants.ROLE_SYSTEM));
		authorities.add(new SimpleGrantedAuthority(Constants.ROLE_ADMINISTRATOR));

		Map<String, Object> attributes = new HashMap<>();
		attributes.put(RolePostProcessor.ATTRIBUTE_NAME, "system");
		attributes.put(RolePostProcessor.ATTRIBUTE_USERID, "system");

		TokenUser tokenUser = TokenUser.builder()
				.cvr("N/A")
				.attributes(attributes)
				.authorities(authorities)
				.username("System")
				.build();

		UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken("System", "N/A", tokenUser.getAuthorities());
		token.setDetails(tokenUser);
		SecurityContextHolder.getContext().setAuthentication(token);
		
		User user = userService.getByUuid(testUserUUID);
		OrgUnit ou = orgUnitService.getByUuid(testOrgUnitUUID);
		UserRole userRole = userRoleService.getAll().get(2);
		RoleGroup roleGroup = roleGroupService.getAll().get(0);
		userService.addRoleGroup(user, roleGroup, null, null);
		userService.addUserRole(user, userRole, null,  null);
		orgUnitService.addUserRole(ou, userRole, false, null, null);
		orgUnitService.addRoleGroup(ou, roleGroup, false, null, null);
		
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
									  .apply(documentationConfiguration(this.restDocumentation)
											  .uris()
											  .withHost("www.rollekatalog.dk")
											  .withPort(443)
											  .withScheme("https")
											 )
									  .build();
	}

	@Test
	public void readOrgUnitRoles() throws Exception {
		this.mockMvc.perform(get("/api/read/ous/{uuid}/roles", testOrgUnitUUID).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
					.andExpect(status().is(200))
					.andDo(document("read-ou-roles", preprocessResponse(prettyPrint()),
							requestHeaders(
									headerWithName("ApiKey").description("Secret key required to call API")
							),
							pathParameters(
									parameterWithName("uuid").description("The UUID of the OrgUnit")
							),
							responseFields(
									fieldWithPath("[].id").type("String").description("The id of the role"),
									fieldWithPath("[].name").type("String").description("The name of the role"),
									fieldWithPath("[].itSystemName").type("String").description("The name of the itsystem the role belongs to")
							)
					));
	}
	
	@Test
	public void readOrgUnitRoleGroups() throws Exception {
		this.mockMvc.perform(get("/api/read/ous/{uuid}/rolegroups", testOrgUnitUUID).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
					.andExpect(status().is(200))
					.andDo(document("read-ou-rolegroups", preprocessResponse(prettyPrint()),
							requestHeaders(
									headerWithName("ApiKey").description("Secret key required to call API")
							),
							pathParameters(
									parameterWithName("uuid").description("The UUID of the OrgUnit")
							),
							responseFields(
									fieldWithPath("[].id").type("String").description("The id of the rolegroup"),
									fieldWithPath("[].name").type("String").description("The name of the rolegroup")
							)
					));
	}

	@Test
	public void readUserRoles() throws Exception {
		this.mockMvc.perform(get("/api/read/user/{uuid}/roles", testUserUUID).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
					.andExpect(status().is(200))
					.andDo(document("read-user-roles", preprocessResponse(prettyPrint()),
							requestHeaders(
									headerWithName("ApiKey").description("Secret key required to call API")
							),
							pathParameters(
									parameterWithName("uuid").description("The UUID of the user")
							),
							responseFields(
									fieldWithPath("[].id").type("String").description("The id of the role"),
									fieldWithPath("[].name").type("String").description("The name of the role"),
									fieldWithPath("[].itSystemName").description("The IT System that the role belongs to")
							)
					));
	}

	@Test
	public void readUserRoleGroups() throws Exception {
		this.mockMvc.perform(get("/api/read/user/{uuid}/rolegroups", testUserUUID).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
					.andExpect(status().is(200))
					.andDo(document("read-user-rolegroups", preprocessResponse(prettyPrint()),
							requestHeaders(
									headerWithName("ApiKey").description("Secret key required to call API")
							),
							pathParameters(
									parameterWithName("uuid").description("The UUID of the user")
							),
							responseFields(
									fieldWithPath("[].id").type("String").description("The id of the rolegroup"),
									fieldWithPath("[].name").type("String").description("The name of the rolegroup")
							)
					));
	}

	@Test
	public void listUserRoles() throws Exception {
		this.mockMvc.perform(get("/api/read/userroles").header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
					.andExpect(status().is(200))
					.andDo(document("list-userroles", preprocessResponse(prettyPrint()),
							requestHeaders(
									headerWithName("ApiKey").description("Secret key required to call API")
							),
							responseFields(
									fieldWithPath("[].id").type("String").description("The id of the role"),
									fieldWithPath("[].name").type("String").description("The name of the role"),
									fieldWithPath("[].itSystemName").type("String").description("The IT System that the role belongs to")
							)
					));
	}

	@Test
	public void readUserRole() throws Exception {
		this.mockMvc.perform(get("/api/read/userroles/{id}", 1).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
					.andExpect(status().is(200))
					.andDo(document("read-userrole", preprocessResponse(prettyPrint()),
							requestHeaders(
									headerWithName("ApiKey").description("Secret key required to call API")
							),
							pathParameters(
									parameterWithName("id").description("The role id")
							),
							responseFields(
									fieldWithPath("identifier").type("String").description("The id of the role"),
									fieldWithPath("name").type("String").description("The name of the role"),
									fieldWithPath("systemRoleAssignments").description("The systemroles assigned to this role"),
									fieldWithPath("systemRoleAssignments[].systemRole").description("The actual system role"),
									fieldWithPath("systemRoleAssignments[].systemRole.name").description("The name of the system role"),
									fieldWithPath("systemRoleAssignments[].systemRole.identifier").description("The identifier of the system role"),
									fieldWithPath("systemRoleAssignments[].systemRole.description").description("The description of the system role"),
									fieldWithPath("systemRoleAssignments[].constraintValues").description("Constraint values applied to this assignment")
							)
					));
	}

	@Test
	public void listRoleGroups() throws Exception {
		this.mockMvc.perform(get("/api/read/rolegroups").header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
					.andExpect(status().is(200))
					.andDo(document("list-rolegroups", preprocessResponse(prettyPrint()),
							requestHeaders(
									headerWithName("ApiKey").description("Secret key required to call API")
							),
							responseFields(
									fieldWithPath("[].id").type("String").description("The id of the rolegroup"),
									fieldWithPath("[].name").type("String").description("The name of the rolegroup")
							)
					));
	}

	@Test
	public void readRoleGroup() throws Exception {
		String id = Long.toString(roleGroupService.getAll().get(0).getId());
		
		this.mockMvc.perform(get("/api/read/rolegroups/{id}", id).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
					.andExpect(status().is(200))
					.andDo(document("read-rolegroup", preprocessResponse(prettyPrint()),
							requestHeaders(
									headerWithName("ApiKey").description("Secret key required to call API")
							),
							pathParameters(
									parameterWithName("id").description("The id of the rolegrou")
							),
							responseFields(
									fieldWithPath("id").type("String").description("The id of the rolegroup"),
									fieldWithPath("name").type("String").description("The name of the rolegroup"),
									fieldWithPath("roles").description("The roles assigned to this rolegroup")
							)
					));
	}
	
	@Test
	public void listUsersWithGivenRole() throws Exception {
		String id = Long.toString(userRoleService.getByIdentifier("KOMBIT_2").getId());
				
		this.mockMvc.perform(get("/api/read/assigned/{id}?indirectRoles=true", id).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
			.andExpect(status().is(200))
			.andDo(document("list-users-with-given-role", preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				requestParameters(
					parameterWithName("indirectRoles").description("set this to true if the result should contain indirect role assignments (through rolegroups, positions and orgunits). Default is false.").optional()
				),
				pathParameters(
					parameterWithName("id").description("The ID of the role to search for")
				),
				responseFields(
					fieldWithPath("roleId").description("The id of the user role"),
					fieldWithPath("roleIdentifier").description("The identifier of the user role"),
					fieldWithPath("roleName").description("The name of the user role"),
					fieldWithPath("systemRoles[]").description("An array of system roles mapped to this user role"),
					fieldWithPath("systemRoles[].roleName").description("The name of the system role"),
					fieldWithPath("systemRoles[].roleIdentifier").description("The unique identifier of the system role"),
					fieldWithPath("systemRoles[].roleConstraintValues[]").description("An array of constraints applied to this system role mapping"),
					fieldWithPath("systemRoles[].roleConstraintValues[].constraintType").description("The unique identifier for the constraint type"),
					fieldWithPath("systemRoles[].roleConstraintValues[].constraintValue").description("The actual constraint value (contrains '*** DYNAMIC ***' for dynamically computed values)"),
					fieldWithPath("assignments[]").description("An array of role assignments for this user role"),
					fieldWithPath("assignments[].uuid").description("The internal UUID of the user assigned the role"),
					fieldWithPath("assignments[].extUuid").description("The external (KOMBIT) UUID of the user assigned the role"),
					fieldWithPath("assignments[].userId").description("The userId of the user assigned the role"),
					fieldWithPath("assignments[].name").description("The name of the user assigned the role"),
					fieldWithPath("assignments[].assignedThrough[]").description("An array of enums indicating how the user is assigned this role. Legal values are: DIRECTLY, ROLEGROUP, POSITION, POSITION_ROLEGROUP, ORGUNIT, ORGUNIT_ROLEGROUP")
				)
			)
		);
	}
	
	@Test
	public void listUsersWithRolesFromItSystem() throws Exception {
		String identifier = "KOMBIT";
		
		this.mockMvc.perform(get("/api/read/itsystem/{identifier}?indirectRoles=true", identifier).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
			.andExpect(status().is(200))
			.andDo(document("list-users-with-roles-from-itsystem", preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				requestParameters(
					parameterWithName("indirectRoles").description("set this to true if the result should contain indirect role assignments (through rolegroups, positions and orgunits). Default is false.").optional()
				),
				pathParameters(
					parameterWithName("identifier").description("The identifier of the it-system to lookup role assignments for")
				),
				responseFields(
					fieldWithPath("[]roleId").description("The id of the user role"),
					fieldWithPath("[]roleIdentifier").description("The identifier of the user role"),
					fieldWithPath("[]roleName").description("The name of the user role"),
					fieldWithPath("[]systemRoles[]").description("An array of system roles mapped to this user role"),
					fieldWithPath("[]systemRoles[].roleName").description("The name of the system role"),
					fieldWithPath("[]systemRoles[].roleIdentifier").description("The unique identifier of the system role"),
					fieldWithPath("[]systemRoles[].roleConstraintValues[]").description("An array of constraints applied to this system role mapping"),
					fieldWithPath("[]systemRoles[].roleConstraintValues[].constraintType").description("The unique identifier for the constraint type"),
					fieldWithPath("[]systemRoles[].roleConstraintValues[].constraintValue").description("The actual constraint value (contrains '*** DYNAMIC ***' for dynamically computed values)"),
					fieldWithPath("[]assignments[]").description("An array of role assignments for this user role"),
					fieldWithPath("[]assignments[].uuid").description("The internal UUID of the user assigned the role"),
					fieldWithPath("[]assignments[].extUuid").description("The external (KOMBIT) UUID of the user assigned the role"),
					fieldWithPath("[]assignments[].userId").description("The userId of the user assigned the role"),
					fieldWithPath("[]assignments[].name").description("The name of the user assigned the role"),
					fieldWithPath("[]assignments[].assignedThrough[]").description("An array of enums indicating how the user is assigned this role. Legal values are: DIRECTLY, ROLEGROUP, POSITION, POSITION_ROLEGROUP, ORGUNIT, ORGUNIT_ROLEGROUP")
				)
			)
		);
	}
}
