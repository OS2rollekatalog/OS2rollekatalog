package dk.digitalidentity.rc.test.xdocumentation;

import dk.digitalidentity.rc.TestContainersConfiguration;
import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.security.RolePostProcessor;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith({SpringExtension.class, RestDocumentationExtension.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles({ "test" })
@Transactional(rollbackFor = Exception.class)
@Import(TestContainersConfiguration.class)
public class TitleApiDocumentation {
	private MockMvc mockMvc;

    @Autowired
    private BootstrapDevMode bootstrapper;

	@Autowired
	private WebApplicationContext context;

	@BeforeEach
	public void setUp(final RestDocumentationContextProvider restDocumentation) throws Exception {
		bootstrapper.init(false);

		// this is a bit of a hack, but we fake that the api logged in using a token,
		// so all of our existing security code just works without further modifications
		List<SamlGrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SamlGrantedAuthority(Constants.ROLE_SYSTEM));
		authorities.add(new SamlGrantedAuthority("ROLE_API_" + AccessRole.ORGANISATION.toString()));
		
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
	public void listTitles() throws Exception {
		this.mockMvc.perform(get("/api/title").header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb"))
					.andExpect(status().is(200))
					.andDo(document("titles-list", preprocessResponse(prettyPrint()),
							requestHeaders(
									headerWithName("ApiKey").description("Secret key required to call API")
							),
							responseFields(
									fieldWithPath("[].uuid").type("String").description("Unique ID for the title"),
									fieldWithPath("[].name").type("String").description("Name of title")
							)
					));
	}
	
	@Test
	public void updateTitles() throws Exception {
		this.mockMvc.perform(post("/api/title")
					.header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb")
					.content("[ {\"uuid\": \"221db307-6d5a-4d9b-9926-f4167196f6e9\", \"name\": \"Title One\"}, {\"uuid\": \"8189e13a-29a4-4a78-9988-d7de4671bf2f\", \"name\": \"Title Two\"}, {\"uuid\": \"91459137-0d33-430f-ae5d-b57874ae3f69\", \"name\": \"Title Three\"} ]")
					.contentType("application/json")
				)
				.andExpect(status().is(200))
				.andDo(document("titles-update", preprocessRequest(prettyPrint()),
						requestHeaders(
								headerWithName("ApiKey").description("Secret key required to call API")
						),
						requestFields(
								fieldWithPath("[].uuid").type("String").description("Unique ID for the title"),
								fieldWithPath("[].name").type("String").description("Name of title")
						)
				));
	}
}
