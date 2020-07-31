package dk.digitalidentity.rc.test.documentation;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
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
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.RolePostProcessor;
import dk.digitalidentity.rc.service.ItSystemService;
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
public class ItSystemApiDocumentation {
	private static String testUserId = "bbog";
	private MockMvc mockMvc;
	private long itSystemId = 0;

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
	
	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private WebApplicationContext context;

	@Before
	public void setUp() throws Exception {
		bootstrapper.init();

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

		User user = userService.getByUserId(testUserId);
		UserRole userRole = userRoleService.getAll().get(2);
		RoleGroup roleGroup = roleGroupService.getAll().get(0);
		userService.addRoleGroup(user, roleGroup);
		userService.addUserRole(user, userRole);

		itSystemId = itSystemService.getAll().stream().filter(its -> its.isCanEditThroughApi()).findFirst().get().getId();
		
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
	public void listItSystems() throws Exception {
		this.mockMvc.perform(get("/api/itsystem/manage").header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
					.andExpect(status().is(200))
					.andDo(document("itsystem-manage-list", preprocessResponse(prettyPrint()),
							requestHeaders(
									headerWithName("ApiKey").description("Secret key required to call API")
							),
							responseFields(
									fieldWithPath("[].id").type("String").description("Unique ID for the it-system"),
									fieldWithPath("[].name").type("String").description("Name of the it-system"),
									fieldWithPath("[].identifier").type("String").description("Technical ID key for the it-system (not always unique)")
							)
					));
	}
	
	@Test
	public void getItSystem() throws Exception {
		this.mockMvc.perform(get("/api/itsystem/manage/{id}", itSystemId).header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
					.andExpect(status().is(200))
					.andDo(document("itsystem-manage-get", preprocessResponse(prettyPrint()),
							requestHeaders(
									headerWithName("ApiKey").description("Secret key required to call API")
							),
							responseFields(
									fieldWithPath("id").type("String").description("Unique ID for the it-system"),
									fieldWithPath("name").type("String").description("Name of the it-system"),
									fieldWithPath("identifier").type("String").description("Technical ID key for the it-system (not always unique)"),
									fieldWithPath("systemRoles[]").description("Array of systemroles currently on it-system"),
									fieldWithPath("systemRoles[].name").description("Name of systemrole"),
									fieldWithPath("systemRoles[].identifier").description("Unique identifier of systemrole"),
									fieldWithPath("systemRoles[].description").description("Description of systemrole")
							)
					));
	}
	
	@Test
	public void updateItSystem() throws Exception {
		this.mockMvc.perform(post("/api/itsystem/manage/{id}", itSystemId)
					.header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb")
					.content("{ \"name\": \"MyItSystem\", \"identifier\": \"MY-IDENTIFIER\", \"systemRoles\": [ { \"name\": \"role1\", \"identifier\": \"ROLE1\", \"description\": \"description....\" }, { \"name\": \"role2\", \"identifier\": \"ROLE2\", \"description\": \"description....\" } ]}")
					.contentType("application/json")
				)
				.andExpect(status().is(200))
				.andDo(document("itsystem-manage-update", preprocessRequest(prettyPrint()),
						requestHeaders(
								headerWithName("ApiKey").description("Secret key required to call API")
						),
						requestFields(
								fieldWithPath("name").description("Name of the it-system"),
								fieldWithPath("identifier").description("Technical ID key for the it-system (not always unique)"),
								fieldWithPath("systemRoles").description("rray of systemroles currently on it-system"),
								fieldWithPath("systemRoles[].name").type("String").description("Name of systemrole"),
								fieldWithPath("systemRoles[].identifier").type("String").description("Unique identifier of systemrole"),
								fieldWithPath("systemRoles[].description").type("String").description("Description of systemrole")
						)
				));
	}
}
