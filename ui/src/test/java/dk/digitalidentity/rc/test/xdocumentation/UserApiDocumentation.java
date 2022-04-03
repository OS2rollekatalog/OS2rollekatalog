package dk.digitalidentity.rc.test.xdocumentation;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.security.RolePostProcessor;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.util.BootstrapDevMode;
import dk.digitalidentity.samlmodule.model.SamlGrantedAuthority;
import dk.digitalidentity.samlmodule.model.TokenUser;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles({ "test" })
@Transactional(rollbackFor = Exception.class)
public class UserApiDocumentation {
	private static String testUserId = "bbog";

	@Rule
	public JUnitRestDocumentation restDocumentation = new JUnitRestDocumentation("target/generated-snippets");

    @Autowired
    private BootstrapDevMode bootstrapper;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private RoleGroupService roleGroupService;

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext context;

	@Before
	public void setUp() throws Exception {
		bootstrapper.init(false);

		// this is a bit of a hack, but we fake that the api logged in using a token,
		// so all of our existing security code just works without further modifications
		List<SamlGrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SamlGrantedAuthority(Constants.ROLE_SYSTEM));
		authorities.add(new SamlGrantedAuthority("ROLE_API_" + AccessRole.READ_ACCESS.toString()));

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

		User user = userService.getByUserId(testUserId);
		UserRole userRole = userRoleService.getAll().get(2);
		RoleGroup roleGroup = roleGroupService.getAll().get(0);
		userService.addRoleGroup(user, roleGroup, null, null);
		userService.addUserRole(user, userRole, null, null);

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
	public void listUserRoles() throws Exception {
		this.mockMvc.perform(get("/api/user/{userid}/roles?system={system}", testUserId, "KOMBIT").header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
					.andExpect(status().is(200))
					.andDo(document("list-user-roles", preprocessResponse(prettyPrint()),
							requestHeaders(
									headerWithName("ApiKey").description("Secret key required to call API")
							),
							requestParameters(
									parameterWithName("system").description("The identifier of the it-system (fx: SAPA) - if not supplied, all roles for all it-systems are returned").optional()
							),
							pathParameters(
									parameterWithName("userid").description("The users userid (fx: " + testUserId + ")")
							),
							responseFields(
									fieldWithPath("oioBPP").type("String").description("Base64 encoded string, containing a OIO-BPP representation of the users roles"),
									fieldWithPath("nameID").type("String").description("Subject NameID in X.509 format"),
									subsectionWithPath("roleMap").description("Map with id/name of roles in oioBPP structure")
							)
					));
	}
	
	@Test
	public void listUserRolesAsList() throws Exception {
		this.mockMvc.perform(get("/api/user/{userid}/rolesAsList?system={system}", testUserId, "KOMBIT").header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
					.andExpect(status().is(200))
					.andDo(document("list-user-roles-as-list", preprocessResponse(prettyPrint()),
							requestHeaders(
									headerWithName("ApiKey").description("Secret key required to call API")
							),
							requestParameters(
									parameterWithName("system").description("The identifier of the it-system (fx: SAPA) - if not supplied, all roles for all it-systems are returned").optional()
							),
							pathParameters(
									parameterWithName("userid").description("The users userid (fx: " + testUserId + ")")
							),
							responseFields(
									fieldWithPath("userRoles").description("List of userroles assigned to the user"),
									fieldWithPath("dataRoles").description("List of dataroles assigned to the user"),
									fieldWithPath("functionRoles").description("List of functionroles assigned to the user"),
									fieldWithPath("systemRoles").description("List of systemroles derived from the list of other roles"),
									fieldWithPath("nameID").type("String").description("Subject NameID in X.509 format"),
									subsectionWithPath("roleMap").description("Map with id/name of roles in oioBPP structure")
							)
					));
	}

	@Test
	public void getNameIdentifier() throws Exception {
		this.mockMvc.perform(get("/api/user/{userid}/nameid", testUserId).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
					.andExpect(status().is(200))
					.andDo(document("read-name-identifier", preprocessResponse(prettyPrint()),
							requestHeaders(
									headerWithName("ApiKey").description("Secret key required to call API")
							),
							pathParameters(
									parameterWithName("userid").description("The users userid (fx: " + testUserId + ")")
							),
							responseFields(
									fieldWithPath("nameID").type("String").description("Subject NameID in X.509 format")
							)
					));
	}
}
