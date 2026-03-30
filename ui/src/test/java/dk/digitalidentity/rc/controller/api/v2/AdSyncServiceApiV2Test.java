package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.dao.model.ADConfiguration;
import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.ClientIntegrationType;
import dk.digitalidentity.rc.dao.model.enums.VersionStatusEnum;
import dk.digitalidentity.rc.dao.model.json.ADConfigurationJSON;
import dk.digitalidentity.rc.service.ADConfigurationService;
import dk.digitalidentity.rc.service.ClientService;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.test.AbstractApiTest;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for AD Sync Service API v2 endpoints.
 * <p>
 * Tests API endpoints for synchronizing Active Directory data,
 * including retrieving and updating configuration as well as error handling.
 * </p>
 */
@DisplayName("AD Sync Service API v2 Tests")
public class AdSyncServiceApiV2Test extends AbstractApiTest {

	@Autowired
	private DomainService domainService;

	@Autowired
	private ClientService clientService;

	@Autowired
	private ADConfigurationService adConfigurationService;

	private Client testClient;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of("AD_SYNC_SERVICE");
	}

	@BeforeEach
	@Override
	public void setUp(RestDocumentationContextProvider restDocumentation) throws Exception {
		super.setUp(restDocumentation);

		Domain primaryDomain = domainService.getPrimaryDomain();

		testClient = clientService.getClientByDomain(primaryDomain);
		if (testClient == null) {
			testClient = new Client();
			testClient.setName("Test AD Sync Client");
			testClient.setApiKey(API_KEY);
			testClient.setAccessRole(AccessRole.ADMINISTRATOR);
			testClient.setVersionStatus(VersionStatusEnum.UNKNOWN);
			testClient.setClientIntegrationType(ClientIntegrationType.AD_SYNC_SERVICE);
			testClient.setDomain(primaryDomain);
			testClient = clientService.save(testClient);
		}

		entityManager.flush();
		entityManager.clear();
	}

	/**
	 * Tests that the syncAll endpoint can be called successfully with a valid domain.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Domain path parameter is handled correctly
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully trigger sync for existing domain")
	void testSyncAll() throws Exception {
		Domain domain = domainService.getPrimaryDomain();

		this.mockMvc.perform(post("/api/v2/ad/syncAll/{domain}", domain.getName())
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andDo(document("adsync-v2-syncall",
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("domain").description("Domain name to synchronize")
				)
			));
	}

	/**
	 * Tests that the syncAll endpoint fails correctly when an invalid domain is specified.
	 * <p>
	 * Verifies that:
	 * - ServletException is thrown when the domain does not exist
	 * - API handles the error scenario correctly
	 * </p>
	 *
	 * @throws Exception if test setup fails
	 */
	@Test
	@DisplayName("Should throw ServletException when syncing non-existent domain")
	void testSyncAll_DomainNotFound() throws Exception {
		assertThrows(ServletException.class, () ->
			this.mockMvc.perform(post("/api/v2/ad/syncAll/{domain}", "nonexistent-domain")
				.header("ApiKey", API_KEY))
		);
	}

	/**
	 * Tests that the getConfiguration endpoint returns 204 when client exists but no config is set.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 204 No Content when client exists but has no ADConfiguration
	 * - Domain path parameter is handled correctly
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 204 when client exists but no configuration is set")
	void testGetConfiguration() throws Exception {
		Domain domain = domainService.getPrimaryDomain();

		this.mockMvc.perform(get("/api/v2/ad/getConfiguration/{domain}", domain.getName())
				.header("ApiKey", API_KEY))
			.andExpect(status().isNoContent())
			.andDo(document("adsync-v2-get-config",
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("domain").description("Domain name")
				)
			));
	}

	/**
	 * Tests that the writeConfiguration endpoint saves configuration when client exists.
	 * <p>
	 * Verifies that:
	 * - Endpoint accepts JSON request body with ADConfigurationJSON fields
	 * - Endpoint returns HTTP 200 OK when client exists and no config exists yet
	 * - Configuration is persisted in the database
	 * - REST documentation is generated with pretty-print
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should save configuration when client exists and no prior config")
	void testWriteConfiguration() throws Exception {
		Domain domain = domainService.getPrimaryDomain();

		String requestBody = """
			{
				"createDeleteFeatureEnabled": false,
				"membershipSyncFeatureEnabled": false,
				"backSyncFeatureEnabled": false,
				"itSystemGroupFeatureEnabled": false,
				"readonlyItSystemFeatureEnabled": false,
				"logUploaderEnabled": false,
				"sendErrorEmailFeatureEnabled": false,
				"includeNotesInDescription": false
			}
			""";

		this.mockMvc.perform(post("/api/v2/ad/writeConfiguration/{domain}", domain.getName())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("adsync-v2-write-config",
				preprocessRequest(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("domain").description("Domain name")
				)
			));

		entityManager.flush();
		entityManager.clear();

		Client refreshedClient = clientService.getClientByDomain(domain);
		ADConfiguration savedConfig = adConfigurationService.getByClient(refreshedClient);
		assertNotNull(savedConfig, "ADConfiguration should be saved");
		assertEquals(1, savedConfig.getVersion(), "Version should be 1");
		assertNotNull(savedConfig.getJson(), "JSON configuration should be saved");
	}

	/**
	 * Tests that the error endpoint saves error messages on existing configuration.
	 * <p>
	 * Verifies that:
	 * - Endpoint accepts text/plain error message in request body
	 * - Endpoint returns HTTP 200 OK when client and configuration exist
	 * - Error message is persisted on the ADConfiguration
	 * - REST documentation is generated with pretty-print
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should save error message when client and configuration exist")
	void testError() throws Exception {
		Domain domain = domainService.getPrimaryDomain();

		// Create an ADConfiguration for the client so the error endpoint has something to save to
		Client client = clientService.getClientByDomain(domain);
		ADConfiguration config = new ADConfiguration();
		config.setClient(client);
		config.setJson(new ADConfigurationJSON());
		config.setVersion(1);
		config.setUpdatedBy("test");
		adConfigurationService.save(config);

		entityManager.flush();
		entityManager.clear();

		this.mockMvc.perform(post("/api/v2/ad/error/{domain}", domain.getName())
				.header("ApiKey", API_KEY)
				.contentType(MediaType.TEXT_PLAIN)
				.content("Test error message"))
			.andExpect(status().isOk())
			.andDo(document("adsync-v2-error",
				preprocessRequest(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("domain").description("Domain name")
				)
			));

		entityManager.flush();
		entityManager.clear();

		Client refreshedClient = clientService.getClientByDomain(domain);
		ADConfiguration updatedConfig = adConfigurationService.getByClient(refreshedClient);
		assertNotNull(updatedConfig, "ADConfiguration should exist");
		assertEquals("Test error message", updatedConfig.getErrorMessage(), "Error message should be saved");
	}
}
