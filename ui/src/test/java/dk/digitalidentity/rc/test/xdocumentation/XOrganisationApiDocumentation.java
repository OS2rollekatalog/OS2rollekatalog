package dk.digitalidentity.rc.test.xdocumentation;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.restdocs.JUnitRestDocumentation;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(locations = "classpath:test.properties")
@ActiveProfiles({ "test" })
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class XOrganisationApiDocumentation { // bad naming, but the X ensures that this test is run last
	private MockMvc mockMvc;

	@Rule
	public JUnitRestDocumentation restDocumentation = new JUnitRestDocumentation("target/generated-snippets");

	@Autowired
	private WebApplicationContext context;

	@Before
	public void setUp() {
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
	public void zLoadOrganisationV3() throws Exception {
		this.mockMvc.perform(
				 post("/api/organisation/v3")
				.header("ApiKey", "f7d8ea9e-53fe-4948-b600-fbc94d4eb0fb")
				.content("{\"orgUnits\": [ {\"uuid\": \"8c651eb0-9aef-42e2-ac41-8678f53ad00e\",\"manager\": {\"uuid\": \"0ce7368b-6712-4c00-a59b-74469f14b8ea\",\"userId\": \"jjup\"},\"name\": \"Hørning Kommune\",\"parentOrgUnitUuid\": null,\"klePerforming\": [\"27.18.00\",\"05.04\"],\"kleInterest\": [\"02.00\"] }, {\"uuid\": \"4db46fa6-ce98-497a-a04a-cfb6f3748a06\",\"manager\": {\"uuid\": \"0ce7368b-6712-4c00-a59b-74469f14b8ea\",\"userId\": \"jjup\"},\"name\": \"Børn og skole\",\"parentOrgUnitUuid\": \"8c651eb0-9aef-42e2-ac41-8678f53ad00e\",\"klePerforming\": [\"27.18.00\",\"05.04\"],\"kleInterest\": [\"02.00\"] }, {\"uuid\": \"99de9db7-5c00-4c70-bf85-85289e69ad47\",\"manager\": {\"uuid\": \"0ce7368b-6712-4c00-a59b-74469f14b8ea\",\"userId\": \"jjup\"},\"name\": \"Bakkeskolen\",\"parentOrgUnitUuid\": \"4db46fa6-ce98-497a-a04a-cfb6f3748a06\",\"klePerforming\": [\"27.18.00\",\"05.04\"],\"kleInterest\": [\"02.00\"] }, {\"uuid\": \"d906819c-d4ba-4cda-9f80-1322765ee891\",\"manager\": {\"uuid\": \"0ce7368b-6712-4c00-a59b-74469f14b8ea\",\"userId\": \"jjup\"},\"name\": \"Aaskolen\",\"parentOrgUnitUuid\": \"4db46fa6-ce98-497a-a04a-cfb6f3748a06\",\"klePerforming\": [\"27.18.00\",\"05.04\"],\"kleInterest\": [\"02.00\"] }],\"users\": [ {\"extUuid\": \"1d623c72-bfd5-4fd1-aee1-6c9a740b8f7f\",\"userId\": \"vmort\",\"name\": \"Viggo Mortensen\",\"cpr\": \"0102300405\",\"email\": \"viggo@kommune.dk\",\"positions\": [{ \"name\": \"Borgmester\", \"orgUnitUuid\": \"8c651eb0-9aef-42e2-ac41-8678f53ad00e\", \"titleUuid\": \"eb6fdd3d-d680-43ec-8c12-f2f9bc94df98\"}],\"klePerforming\": [\"02.00\"],\"kleInterest\": [\"27.18.00\",\"05.04\"] }, {\"extUuid\": \"453ed208-2ed1-4739-8d79-1199082193b5\",\"userId\": \"bbog\",\"email\": \"bente@kommune.dk\",\"name\": \"Bente Bogmærke\",\"positions\": [{ \"name\": \"Bogholder\", \"orgUnitUuid\": \"4db46fa6-ce98-497a-a04a-cfb6f3748a06\", \"titleUuid\": \"62120557-84a3-4c2a-8ba0-39703e8eefca\"}],\"klePerforming\": [\"02.00\"],\"kleInterest\": [\"27.18.00\",\"05.04\"] }, {\"extUuid\": \"0ce7368b-6712-4c00-a59b-74469f14b8ea\",\"userId\": \"jjup\",\"email\": \"jannie@kommune.dk\",\"name\": \"Jannie Jupiter\",\"doNotInherit\": true,\"positions\": [{ \"name\": \"HR Konsulent\", \"orgUnitUuid\": \"99de9db7-5c00-4c70-bf85-85289e69ad47\", \"titleUuid\": \"62120557-84a3-4c2a-8ba0-39703e8eefca\"},{ \"name\": \"Læreinde\", \"orgUnitUuid\": \"d906819c-d4ba-4cda-9f80-1322765ee891\", \"titleUuid\": \"eb6fdd3d-d680-43ec-8c12-f2f9bc94df98\"}],\"klePerforming\": [\"02.00\"],\"kleInterest\": [\"27.18.00\",\"05.04\"] }]}")
				.contentType("application/json")
		)
		.andExpect(status().is(200))
		.andDo(document("load-organisation-v3", preprocessRequest(prettyPrint()),
				requestFields(
					fieldWithPath("users").description("The list of employees to import"),
					fieldWithPath("users[].extUuid").type("String").description("The unique identifier of the employee"),
					fieldWithPath("users[].userId").type("String").description("The user-id of the employee (e.g. SAMAccountName from AD)"),
					fieldWithPath("users[].name").type("String").description("The full name of the employee"),
					fieldWithPath("users[].email").type("String").description("The email address of the employee").optional(),
					fieldWithPath("users[].cpr").type("String").description("The CPR of the employee").optional(),
					fieldWithPath("users[].doNotInherit").type("Boolean").description("Set to 'true' if this user cannot inherit roles and kle assignments from OrgUnits").optional(),
					fieldWithPath("users[].klePerforming").description("The list of 'performing' KLEs assigned directly to the user").optional(),
					fieldWithPath("users[].kleInterest").description("The list of 'interest' KLEs assigned directly to the user").optional(),
					fieldWithPath("users[].positions").description("The list of positions the employee holds in the organisation"),
					fieldWithPath("users[].positions[].orgUnitUuid").type("String").description("The unique identifier of the orgUnit that the employee holds a position in"),
					fieldWithPath("users[].positions[].name").type("String").description("The title of the position that the employee holds in this OrgUnit"),
					fieldWithPath("users[].positions[].titleUuid").type("String").description("Optional value - references the title of this position by its uuid (requires that the Title API is also used)"),
					fieldWithPath("orgUnits").description("The list of orgUnits to import"),
					fieldWithPath("orgUnits[].uuid").description("The unique identifier org the OrgUnit"),
					fieldWithPath("orgUnits[].name").description("The name of the OrgUnit"),
					fieldWithPath("orgUnits[].parentOrgUnitUuid").description("The unique identifier of the OrgUnit above this one in the hierarchy").optional(),
					fieldWithPath("orgUnits[].klePerforming").description("The list of 'performing' KLEs assigned to the OrgUnit").optional(),
					fieldWithPath("orgUnits[].kleInterest").description("The list of 'interest' KLEs assigned to the OrgUnit").optional(),
					fieldWithPath("orgUnits[].manager").description("The reference to the manager for this OrgUnit"),
					fieldWithPath("orgUnits[].manager.uuid").description("Reference extUuid on User"),
					fieldWithPath("orgUnits[].manager.userId").description("Reference to userId on User")
				),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				)
		));
	}
}
