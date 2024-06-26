package dk.digitalidentity.rc.test.xdocumentation;

import dk.digitalidentity.rc.TestContainersConfiguration;
import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.security.RolePostProcessor;
import dk.digitalidentity.rc.service.ItSystemService;
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
import java.util.List;
import java.util.Map;

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
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith({SpringExtension.class, RestDocumentationExtension.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles({ "test" })
@Transactional(rollbackFor = Exception.class)
@Import(TestContainersConfiguration.class)
public class ItSystemApiDocumentation {
	private static String testUserId = "bbog";
	private MockMvc mockMvc;
	private long itSystemId = 0;

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

	@BeforeEach
	public void setUp(final RestDocumentationContextProvider restDocumentation) throws Exception {
		bootstrapper.init(false);

		// this is a bit of a hack, but we fake that the api logged in using a token,
		// so all of our existing security code just works without further modifications
		List<SamlGrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SamlGrantedAuthority(Constants.ROLE_SYSTEM));
		authorities.add(new SamlGrantedAuthority("ROLE_API_" + AccessRole.ITSYSTEM.toString()));

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
		userService.addRoleGroup(user, roleGroup, null, null, null);
		userService.addUserRole(user, userRole, null, null);

		itSystemId = itSystemService.getAll().stream().filter(its -> its.isCanEditThroughApi()).findFirst().get().getId();
		
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
							pathParameters(
									parameterWithName("id").description("The id of the it-system")
							),
							responseFields(
									fieldWithPath("id").type("String").description("Unique ID for the it-system"),
									fieldWithPath("name").type("String").description("Name of the it-system"),
									fieldWithPath("readonly").type("Boolean").description("Indicating if the system can only be read, and not written to"),
									fieldWithPath("identifier").type("String").description("Technical ID key for the it-system (not always unique)"),
									fieldWithPath("convertRolesEnabled").description("Can safely be ignored when READING the it-system data"),
									fieldWithPath("systemRoles[]").description("Array of systemroles currently on it-system"),
									fieldWithPath("systemRoles[].name").description("Name of systemrole"),
									fieldWithPath("systemRoles[].identifier").description("Unique identifier of systemrole"),
									fieldWithPath("systemRoles[].description").description("Description of systemrole"),
									subsectionWithPath("systemRoles[].users").description("Users with this assignment"),
									fieldWithPath("userRoles[]").description("Array of userroles currently on it-system"),
									fieldWithPath("userRoles[].id").description("ID of userRole"),
									fieldWithPath("userRoles[].name").description("Name of userole"),
									fieldWithPath("userRoles[].identifier").description("Unique identifier of userrole"),
									fieldWithPath("userRoles[].systemRoleAssignments").ignored()
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
						pathParameters(
								parameterWithName("id").description("The id of the it-system")
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
