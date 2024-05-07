package dk.digitalidentity.rc.test.xdocumentation;

import dk.digitalidentity.rc.TestContainersConfiguration;
import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.security.RolePostProcessor;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.util.BootstrapDevMode;
import dk.digitalidentity.samlmodule.model.SamlGrantedAuthority;
import dk.digitalidentity.samlmodule.model.TokenUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith({SpringExtension.class, RestDocumentationExtension.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles({ "test" })
@Transactional(rollbackFor = Exception.class)
@Import(TestContainersConfiguration.class)
public class RoleAssignmentApiDocumentation {
	private static String testUserUUID = BootstrapDevMode.userUUID;
	private static String testOrgUnitUUID = BootstrapDevMode.orgUnitUUID;
	private MockMvc mockMvc;

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

	@BeforeEach
	public void setUp(final RestDocumentationContextProvider restDocumentation) throws Exception {
		bootstrapper.init(false);

		// this is a bit of a hack, but we fake that the api logged in using a token,
		// so all of our existing security code just works without further modifications
		List<SamlGrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SamlGrantedAuthority(Constants.ROLE_SYSTEM));
		authorities.add(new SamlGrantedAuthority("ROLE_API_" + AccessRole.ROLE_MANAGEMENT.toString()));

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

		UserRole userRole = userRoleService.getAll().get(2);
		RoleGroup roleGroup = roleGroupService.getAll().get(0);

		User user = userService.getByExtUuid(testUserUUID).get(0);
		userService.addRoleGroup(user, roleGroup, null, null, null);
		userService.addUserRole(user, userRole, null, null);
		
		OrgUnit ou = orgUnitService.getByUuid(testOrgUnitUUID);
		orgUnitService.addUserRole(ou, userRole, false, null, null, new HashSet<>(), new HashSet<>());
		orgUnitService.addRoleGroup(ou, roleGroup, false, null, null, new HashSet<>(), new HashSet<>());

		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
									  .apply(documentationConfiguration(restDocumentation)
											  .uris()
											  .withHost("www.rollekatalog.dk")
											  .withPort(443)
											  .withScheme("https")
											)
									  .build();
	}

	@Test
	public void assignRole() throws Exception {
		String id = Long.toString(userRoleService.getAll().get(2).getId());

		this.mockMvc.perform(put("/api/user/{userUuid}/assign/userrole/{userRoleId}", testUserUUID, id).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
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
	}

	@Test
	public void deassignRole() throws Exception {
		String id = Long.toString(userRoleService.getAll().get(2).getId());

		this.mockMvc.perform(delete("/api/user/{userUuid}/deassign/userrole/{userRoleId}", testUserUUID, id).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
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
	}

	@Test
	public void assignRoleGroupToUser() throws Exception {
		String id = Long.toString(roleGroupService.getAll().get(0).getId());

		this.mockMvc.perform(put("/api/user/{userUuid}/assign/rolegroup/{roleGroupId}", testUserUUID, id).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
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
	}

	@Test
	public void deassignRoleGroupFromUser() throws Exception {
		String id = Long.toString(roleGroupService.getAll().get(0).getId());

		this.mockMvc.perform(delete("/api/user/{userUuid}/deassign/rolegroup/{roleGroupId}", testUserUUID, id).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
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
	}

	@Test
	public void assignRoleToOrgUnit() throws Exception {
		String id = Long.toString(userRoleService.getAll().get(0).getId());

		this.mockMvc.perform(put("/api/ou/{ouUuid}/assign/userrole/{userRoleId}", testOrgUnitUUID, id).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
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
	}

	@Test
	public void deassignRoleFromOrgUnit() throws Exception {
		String id = Long.toString(userRoleService.getAll().get(0).getId());

		this.mockMvc.perform(delete("/api/ou/{ouUuid}/deassign/userrole/{userRoleId}", testOrgUnitUUID, id).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
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
	}

	@Test
	public void assignRoleGroupToOrgUnit() throws Exception {
		String id = Long.toString(roleGroupService.getAll().get(0).getId());

		this.mockMvc.perform(put("/api/ou/{ouUuid}/assign/rolegroup/{roleGroupId}", testOrgUnitUUID, id).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
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
	}

	@Test
	public void deassignRoleGroupFromOrgUnit() throws Exception {
		String id = Long.toString(roleGroupService.getAll().get(0).getId());

		this.mockMvc.perform(delete("/api/ou/{ouUuid}/deassign/rolegroup/{roleGroupId}", testOrgUnitUUID, id).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
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
	}
}
