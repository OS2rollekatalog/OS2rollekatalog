package dk.digitalidentity.rc.test.xdocumentation;

import dk.digitalidentity.rc.TestContainersConfiguration;
import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.DirtyADGroupDao;
import dk.digitalidentity.rc.dao.model.DirtyADGroup;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.security.RolePostProcessor;
import dk.digitalidentity.rc.service.ItSystemService;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith({SpringExtension.class, RestDocumentationExtension.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles({ "test" })
@Transactional(rollbackFor = Exception.class)
@Import(TestContainersConfiguration.class)
public class ADSyncDocumentation {
	private static String testUserId = "bbog";
	private MockMvc mockMvc;

    @Autowired
    private BootstrapDevMode bootstrapper;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRoleService userRoleService;
	
	@Autowired
	private DirtyADGroupDao dirtyADGroupDao;

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

		// ensure test-user has an AD role assigned
		ItSystem itSystem = itSystemService.getBySystemType(ItSystemType.AD).get(0);
		UserRole userRole = userRoleService.getByItSystem(itSystem).get(0);		
		User user = userService.getByUserId(testUserId);
		userService.addUserRole(user, userRole, null, null);

		DirtyADGroup pending = new DirtyADGroup();
		pending.setIdentifier(userRole.getSystemRoleAssignments().get(0).getSystemRole().getIdentifier());
		pending.setItSystemId(itSystem.getId());
		pending.setTimestamp(new Date());
		pending.setDomain(itSystem.getDomain());
		dirtyADGroupDao.save(pending);

		/*
		* 		this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(documentationConfiguration(restDocumentation)).build();
		* */
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
	public void listAssignments() throws Exception {
		this.mockMvc.perform(get("/api/ad/v2/sync").header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
			.andExpect(status().is(200))
			.andDo(document("ad-sync-assignments", preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("head").type("Integer").description("sync-control value (used for cleanup)"),
					fieldWithPath("maxHead").type("Integer").description("sync-control value (used to indicate max ID of all pending operations, including those not available in output)"),
					fieldWithPath("assignments[]").description("An array of AD groups that have changes in assignments"),
					fieldWithPath("assignments[].groupName").description("the name of the AD group"),
					fieldWithPath("assignments[].samaccountNames").type("Array of String").description("sAMAccountNames of the users within this AD group")
				)
			));
	}
	
	@Test
	public void cleanup() throws Exception {
		this.mockMvc.perform(delete("/api/ad/v2/sync/{head}", "2").header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
			.andExpect(status().is(200))
			.andDo(document("ad-sync-cleanup", preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("head").description("The value of \"head\" given by the output from /api/ad/sync")
				)
			));
	}
}
