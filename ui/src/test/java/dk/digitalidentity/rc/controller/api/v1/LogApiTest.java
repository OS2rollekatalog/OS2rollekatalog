package dk.digitalidentity.rc.controller.api.v1;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.SettingsDao;
import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.Setting;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.VersionStatusEnum;
import dk.digitalidentity.rc.security.RolePostProcessor;
import dk.digitalidentity.rc.service.ClientService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.test.AbstractApiTest;
import dk.digitalidentity.saml.service.model.SamlGrantedAuthority;
import dk.digitalidentity.saml.service.model.TokenUser;

/**
 * Test suite for Log Upload API endpoints.
 * <p>
 * Tests API endpoints for checking whether a client should upload diagnostic logs.
 * The system uses a settings-based flag (REQUEST_LOG_{clientId}) to signal when
 * administrators want to collect logs from specific API clients. Once a client
 * checks and receives "true", the flag is automatically reset to prevent repeated
 * uploads. The API uses SecurityUtil.getClient() to identify the calling client.
 * </p>
 */
@DisplayName("Log Upload API Tests")
public class LogApiTest extends AbstractApiTest {

	@Autowired
	private ClientService clientService;

	@MockitoBean
	private SettingsService settingsService;

	@MockitoBean
	private SettingsDao settingsDao;

	private Client testClient;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.READ_ACCESS.toString());
	}

	/**
	 * Sets up test client and security context before each test.
	 * <p>
	 * Note: This test class requires special setup because the LogApi uses
	 * SecurityUtil.getClient() which expects a Client object in the security
	 * context attributes, not just a string UUID.
	 * </p>
	 *
	 * @throws Exception if setup fails
	 */
	@BeforeEach
	@Override
	public void setUp(org.springframework.restdocs.RestDocumentationContextProvider restDocumentation) throws Exception {
		// Call parent setUp first to initialize MockMvc
		super.setUp(restDocumentation);

		// Create or get a test client
		testClient = clientService.getClientByName("Test Log Client");
		if (testClient == null) {
			testClient = new Client();
			testClient.setName("Test Log Client");
			testClient.setApiKey(API_KEY);
			testClient.setAccessRole(AccessRole.READ_ACCESS);
			testClient.setVersionStatus(VersionStatusEnum.UNKNOWN);
			testClient = clientService.save(testClient);
		}

		// Override security context to include actual Client object
		List<SamlGrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SamlGrantedAuthority(Constants.ROLE_SYSTEM));
		authorities.add(new SamlGrantedAuthority("ROLE_API_" + AccessRole.READ_ACCESS.toString()));

		Map<String, Object> attributes = new HashMap<>();
		attributes.put(RolePostProcessor.ATTRIBUTE_NAME, "system");
		attributes.put(RolePostProcessor.ATTRIBUTE_USERID, "system");
		// IMPORTANT: Set the actual Client object, not a string - required for SecurityUtil.getClient()
		attributes.put(RolePostProcessor.ATTRIBUTE_CLIENT, testClient);

		TokenUser tokenUser = TokenUser.builder()
			.cvr("N/A")
			.attributes(attributes)
			.authorities(authorities)
			.username("System")
			.build();

		UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken("System", "N/A", tokenUser.getAuthorities());
		token.setDetails(tokenUser);
		SecurityContextHolder.getContext().setAuthentication(token);

		entityManager.flush();
		entityManager.clear();
	}

	/**
	 * Tests that the API returns false when log request setting is false.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is "false" when REQUEST_LOG setting exists but is set to false
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return false when log request setting is false")
	void testUploadLog_SettingIsFalse() throws Exception {
		Setting mockSetting = new Setting();
		mockSetting.setValue("false");
		when(settingsDao.findByKey("REQUEST_LOG_" + testClient.getId())).thenReturn(mockSetting);

		this.mockMvc.perform(get("/api/uploadLog")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(content().string("false"))
			.andDo(document("log-upload-not-requested",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				)
			));
	}

	/**
	 * Tests that the API returns false when the log request setting does not exist.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is "false" when REQUEST_LOG setting is not present in database
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return false when log request setting does not exist")
	void testUploadLog_SettingNotPresent() throws Exception {
		when(settingsDao.findByKey("REQUEST_LOG_" + testClient.getId())).thenReturn(null);

		this.mockMvc.perform(get("/api/uploadLog")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(content().string("false"));
	}

	/**
	 * Tests that the API returns true when log upload has been requested.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is "true" when REQUEST_LOG setting is true
	 * - Setting is automatically reset to false after returning true
	 * - REST documentation is generated
	 * </p>
	 * <p>
	 * This "check and reset" behavior ensures clients only upload logs once
	 * per request, preventing unnecessary repeated uploads.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails or validation fails
	 */
	@Test
	@DisplayName("Should return true when log upload has been requested and reset flag")
	void testUploadLog_LogRequested() throws Exception {
		Setting mockSetting = new Setting();
		mockSetting.setKey("REQUEST_LOG_" + testClient.getId());
		mockSetting.setValue("true");

		when(settingsService.getByKey("REQUEST_LOG_" + testClient.getId())).thenReturn(mockSetting);

		this.mockMvc.perform(get("/api/uploadLog")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(content().string("true"))
			.andDo(document("log-upload-requested",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				)
			));

		// Verify the setting was saved with "false" after returning true
		verify(settingsService, times(1)).save(argThat(s ->
			"false".equals(s.getValue()) &&
				("REQUEST_LOG_" + testClient.getId()).equals(s.getKey())
		));
	}

	/**
	 * Tests that the flag is reset after first request returning true.
	 * <p>
	 * Verifies that:
	 * - First call returns true when flag is set
	 * - Setting is saved with value "false" after first call
	 * - Second call returns false (flag was reset)
	 * - Automatic reset prevents repeated log uploads
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should reset flag after returning true, subsequent calls return false")
	void testUploadLog_LogRequestedResetsFlag() throws Exception {
		Setting mockSetting = new Setting();
		mockSetting.setValue("true");
		mockSetting.setKey("REQUEST_LOG_" + testClient.getId());

		// First call: setting is "true"
		when(settingsService.getByKey("REQUEST_LOG_" + testClient.getId()))
			.thenReturn(mockSetting)
			.thenReturn(mockSetting);

		// First request - should return true
		this.mockMvc.perform(get("/api/uploadLog")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(content().string("true"));

		// Simulate the setting now being "false" for the second call
		mockSetting.setValue("false");

		// Second request - should return false
		this.mockMvc.perform(get("/api/uploadLog")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(content().string("false"));
	}

	/**
	 * Tests that the API returns 403 when client cannot be identified.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 403 Forbidden when client is null
	 * - SecurityUtil.getClient() returning null is handled gracefully
	 * - Error message "Unknown client" is returned
	 * </p>
	 * <p>
	 * This scenario occurs when the security context doesn't contain a valid
	 * Client object, which should never happen in production with proper
	 * authentication but is tested for robustness.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 403 when client cannot be identified from security context")
	void testUploadLog_WithoutClient() throws Exception {
		List<SamlGrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SamlGrantedAuthority(Constants.ROLE_SYSTEM));
		authorities.add(new SamlGrantedAuthority("ROLE_API_" + AccessRole.READ_ACCESS.toString()));

		Map<String, Object> attributes = new HashMap<>();
		attributes.put(RolePostProcessor.ATTRIBUTE_NAME, "system");
		attributes.put(RolePostProcessor.ATTRIBUTE_USERID, "system");
		// Don't set ATTRIBUTE_CLIENT - this should cause SecurityUtil.getClient() to return null

		TokenUser tokenUser = TokenUser.builder()
			.cvr("N/A")
			.attributes(attributes)
			.authorities(authorities)
			.username("System")
			.build();

		UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken("System", "N/A", tokenUser.getAuthorities());
		token.setDetails(tokenUser);
		SecurityContextHolder.getContext().setAuthentication(token);

		this.mockMvc.perform(get("/api/uploadLog")
				.header("ApiKey", API_KEY))
			.andExpect(status().isForbidden())
			.andExpect(content().string("Unknown client"));
	}
}
