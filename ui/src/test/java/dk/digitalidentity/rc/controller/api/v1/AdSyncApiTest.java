package dk.digitalidentity.rc.controller.api.v1;

import dk.digitalidentity.rc.dao.model.DirtyADGroup;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.PendingADGroupOperation;
import dk.digitalidentity.rc.dao.model.enums.ADGroupType;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.PendingADUpdateService;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for AD Sync Service API v1 endpoints.
 * <p>
 * Tests API endpoints for managing Active Directory operations and synchronization,
 * including retrieving pending operations, flagging completed operations, and
 * managing AD group assignments with domain support.
 * </p>
 */
@DisplayName("AD Sync Service API v1 Tests")
public class AdSyncApiTest extends AbstractApiTest {

	@Autowired
	private DomainService domainService;

	@Autowired
	private ItSystemService itSystemService;

	@MockitoBean
	private PendingADUpdateService pendingADUpdateService;

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.READ_ACCESS.toString());
	}

	@BeforeEach
	@Override
	public void setUp(RestDocumentationContextProvider restDocumentation) throws Exception {
		super.setUp(restDocumentation);

		Domain domain = domainService.getPrimaryDomain();
		ItSystem adSystem = itSystemService.getBySystemType(ItSystemType.AD).stream()
			.filter(s -> s.getDomain() != null && s.getDomain().getId() == domain.getId())
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No AD IT system found in test environment"));

		DirtyADGroup dirtyGroup = new DirtyADGroup();
		dirtyGroup.setIdentifier("testgroup-001");
		dirtyGroup.setItSystemId(adSystem.getId());
		dirtyGroup.setDomain(domain);
		dirtyGroup.setTimestamp(new Date());
		PendingADGroupOperation operation = new PendingADGroupOperation();
		operation.setId(1L);
		operation.setTimestamp(Date.from(Instant.now()));
		operation.setSystemRoleId(1L);
		operation.setSystemRoleIdentifier("TestIdentifier");
		operation.setItSystemIdentifier("TestIdentifierAgain");
		operation.setActive(true);
		operation.setAdGroupType(ADGroupType.NONE);
		operation.setDomain(domain);


		when(pendingADUpdateService.find100Operations(domain)).thenReturn(List.of(operation));
		when(pendingADUpdateService.find100(any(Domain.class))).thenReturn(List.of(dirtyGroup));
		when(pendingADUpdateService.findMaxHead()).thenReturn(0L);
		doNothing().when(pendingADUpdateService).deleteByIdLessThan(anyLong(), anyLong(), any(Domain.class));
		doNothing().when(pendingADUpdateService).deleteOperationsByIdLessThan(anyLong(), any(Domain.class));
	}

	/**
	 * Tests that the operations endpoint returns pending AD operations successfully.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response contains operations array and head value
	 * - REST documentation is generated with proper field descriptions
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve pending operations")
	void testGetPendingOperations() throws Exception {
		this.mockMvc.perform(get("/api/ad/v2/operations")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.operations").isArray())
			.andExpect(jsonPath("$.head").value(1))
			.andDo(document("adsync-operations",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("operations").type(JsonFieldType.ARRAY).description("List of pending AD operations"),
					fieldWithPath("operations[].id").type(JsonFieldType.NUMBER).description("Unique identifier of the operation").optional(),
					fieldWithPath("operations[].userId").type(JsonFieldType.STRING).description("User ID associated with the operation").optional(),
					fieldWithPath("operations[].operation").type(JsonFieldType.STRING).description("Type of operation to perform").optional(),
					fieldWithPath("operations[].timestamp").type(JsonFieldType.STRING).description("Timestamp of the operation").optional(),
					fieldWithPath("operations[].active").type(JsonFieldType.BOOLEAN).description("Whether the operation is active").optional(),
					fieldWithPath("operations[].adGroupType").type(JsonFieldType.STRING).description("Type of AD group (e.g., NONE)").optional(),
					fieldWithPath("operations[].domain").type(JsonFieldType.OBJECT).description("Domain information").optional(),
					fieldWithPath("operations[].domain.id").type(JsonFieldType.NUMBER).description("Domain ID").optional(),
					fieldWithPath("operations[].domain.name").type(JsonFieldType.STRING).description("Domain name").optional(),
					fieldWithPath("operations[].itSystemIdentifier").type(JsonFieldType.STRING).description("IT system identifier").optional(),
					fieldWithPath("operations[].systemRoleIdentifier").type(JsonFieldType.STRING).description("System role identifier").optional(),
					fieldWithPath("operations[].universal").type(JsonFieldType.BOOLEAN).description("Whether the operation is universal").optional(),
					fieldWithPath("head").type(JsonFieldType.NUMBER).description("Maximum ID of the operations returned")
				)
			));
	}

	/**
	 * Tests that the operations endpoint can filter by domain.
	 * <p>
	 * Verifies that:
	 * - Endpoint accepts domain query parameter
	 * - Response contains operations filtered by domain
	 * - Valid domain returns HTTP 200 OK
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve pending operations filtered by domain")
	void testGetPendingOperations_WithDomain() throws Exception {
		Domain domain = domainService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No domain found in test environment"));

		this.mockMvc.perform(get("/api/ad/v2/operations")
				.header("ApiKey", API_KEY)
				.param("domain", domain.getName()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.operations").isArray())
			.andExpect(jsonPath("$.head").value(1))
			.andDo(document("adsync-operations",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("operations").type(JsonFieldType.ARRAY).description("List of pending AD operations"),
					fieldWithPath("operations[].id").type(JsonFieldType.NUMBER).description("Unique identifier of the operation").optional(),
					fieldWithPath("operations[].userId").type(JsonFieldType.STRING).description("User ID associated with the operation").optional(),
					fieldWithPath("operations[].operation").type(JsonFieldType.STRING).description("Type of operation to perform").optional(),
					fieldWithPath("operations[].timestamp").type(JsonFieldType.STRING).description("Timestamp of the operation").optional(),
					fieldWithPath("operations[].active").type(JsonFieldType.BOOLEAN).description("Whether the operation is active").optional(),
					fieldWithPath("operations[].adGroupType").type(JsonFieldType.STRING).description("Type of AD group (e.g., NONE)").optional(),
					fieldWithPath("operations[].domain").type(JsonFieldType.OBJECT).description("Domain information").optional(),
					fieldWithPath("operations[].domain.id").type(JsonFieldType.NUMBER).description("Domain ID").optional(),
					fieldWithPath("operations[].domain.name").type(JsonFieldType.STRING).description("Domain name").optional(),
					fieldWithPath("operations[].itSystemIdentifier").type(JsonFieldType.STRING).description("IT system identifier").optional(),
					fieldWithPath("operations[].systemRoleIdentifier").type(JsonFieldType.STRING).description("System role identifier").optional(),
					fieldWithPath("operations[].universal").type(JsonFieldType.BOOLEAN).description("Whether the operation is universal").optional(),
					fieldWithPath("head").type(JsonFieldType.NUMBER).description("Maximum ID of the operations returned")
				)
			));
	}

	/**
	 * Tests that the operations endpoint returns 404 for non-existent domains.
	 * <p>
	 * Verifies that:
	 * - Invalid domain parameter returns HTTP 404 Not Found
	 * - API handles invalid domain scenario correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when querying operations for non-existent domain")
	void testGetPendingOperations_InvalidDomain() throws Exception {
		this.mockMvc.perform(get("/api/ad/v2/operations")
				.header("ApiKey", API_KEY)
				.param("domain", "nonexistent-domain"))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that completed operations can be flagged successfully.
	 * <p>
	 * Verifies that:
	 * - DELETE endpoint accepts head parameter
	 * - Endpoint returns HTTP 200 OK
	 * - Delete operation is called with correct parameters
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully flag operations as performed")
	void testFlagOperationsPerformed() throws Exception {
		Domain domain = domainService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No domain found in test environment"));

		this.mockMvc.perform(delete("/api/ad/v2/operations/{head}", 10)
				.header("ApiKey", API_KEY)
				.param("domain", domain.getName()))
			.andExpect(status().isOk())
			.andDo(document("adsync-operations-delete",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("head").description("The maximum ID of operations that have been performed")
				)
			));

		// Verify the delete operation was called with the correct parameters
		verify(pendingADUpdateService, times(1)).deleteOperationsByIdLessThan(eq(11L), any(Domain.class));
	}

	/**
	 * Tests that the sync endpoint returns pending AD group assignments.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response contains assignments array, head, and maxHead values
	 * - REST documentation is generated with proper field descriptions
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve pending sync updates")
	void testGetPendingUpdates() throws Exception {
		this.mockMvc.perform(get("/api/ad/v2/sync")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assignments").isArray())
			.andExpect(jsonPath("$.assignments").isNotEmpty())
			.andExpect(jsonPath("$.assignments[0].groupName").value("testgroup-001"))
			.andExpect(jsonPath("$.assignments[0].sAMAccountNames").isArray())
			.andExpect(jsonPath("$.head").value(0))
			.andExpect(jsonPath("$.maxHead").value(0))
			.andDo(document("adsync-sync",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("assignments").type(JsonFieldType.ARRAY).description("List of AD group assignments"),
					fieldWithPath("assignments[].groupName").type(JsonFieldType.STRING).description("Name of the AD group").optional(),
					fieldWithPath("assignments[].sAMAccountNames").type(JsonFieldType.ARRAY).description("List of SAM account names to assign").optional(),
					fieldWithPath("head").type(JsonFieldType.NUMBER).description("Maximum ID of the updates processed"),
					fieldWithPath("maxHead").type(JsonFieldType.NUMBER).description("Maximum ID available in the system")
				)
			));
	}

	/**
	 * Tests that the sync endpoint supports full synchronization mode.
	 * <p>
	 * Verifies that:
	 * - Endpoint accepts fullsync query parameter
	 * - Full sync returns all AD systems
	 * - Response structure matches regular sync
	 * - REST documentation includes fullsync parameter
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully perform full sync of all AD systems")
	void testGetPendingUpdates_FullSync() throws Exception {
		this.mockMvc.perform(get("/api/ad/v2/sync")
				.header("ApiKey", API_KEY)
				.param("fullsync", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assignments").isArray())
			.andExpect(jsonPath("$.assignments").isNotEmpty())
			.andExpect(jsonPath("$.assignments[?(@.groupName == 'testgroup-001')]").exists())
			.andExpect(jsonPath("$.head").value(0))
			.andExpect(jsonPath("$.maxHead").value(0))
			.andDo(document("adsync-sync-fullsync",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				queryParameters(
					parameterWithName("fullsync").description("If true, performs a full sync of all AD systems (default: false)")
				),
				responseFields(
					fieldWithPath("assignments").type(JsonFieldType.ARRAY).description("List of AD group assignments"),
					fieldWithPath("assignments[].itSystemId").type(JsonFieldType.NUMBER).description("ID of the IT system").optional(),
					fieldWithPath("assignments[].weight").type(JsonFieldType.NUMBER).description("Weight of the system role").optional(),
					fieldWithPath("assignments[].groupName").type(JsonFieldType.STRING).description("Name of the AD group").optional(),
					fieldWithPath("assignments[].sAMAccountNames").type(JsonFieldType.ARRAY).description("List of SAM account names to assign").optional(),
					fieldWithPath("head").type(JsonFieldType.NUMBER).description("Maximum ID of the updates processed"),
					fieldWithPath("maxHead").type(JsonFieldType.NUMBER).description("Maximum ID available in the system")
				)
			));
	}

	/**
	 * Tests that the sync endpoint can filter by domain.
	 * <p>
	 * Verifies that:
	 * - Endpoint accepts domain query parameter
	 * - Response contains assignments filtered by domain
	 * - Valid domain returns HTTP 200 OK
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve pending sync updates filtered by domain")
	void testGetPendingUpdates_WithDomain() throws Exception {
		Domain domain = domainService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No domain found in test environment"));

		this.mockMvc.perform(get("/api/ad/v2/sync")
				.header("ApiKey", API_KEY)
				.param("domain", domain.getName()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assignments").isArray())
			.andExpect(jsonPath("$.assignments").isNotEmpty())
			.andExpect(jsonPath("$.assignments[0].groupName").value("testgroup-001"))
			.andExpect(jsonPath("$.assignments[0].sAMAccountNames").isArray())
			.andExpect(jsonPath("$.head").value(0))
			.andExpect(jsonPath("$.maxHead").value(0));
	}

	/**
	 * Tests that the sync endpoint returns 404 for non-existent domains.
	 * <p>
	 * Verifies that:
	 * - Invalid domain parameter returns HTTP 404 Not Found
	 * - API handles invalid domain scenario correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when querying sync updates for non-existent domain")
	void testGetPendingUpdates_InvalidDomain() throws Exception {
		this.mockMvc.perform(get("/api/ad/v2/sync")
				.header("ApiKey", API_KEY)
				.param("domain", "nonexistent-domain"))
			.andExpect(status().isNotFound());
	}

	/**
	 * Tests that completed sync operations can be flagged successfully.
	 * <p>
	 * Verifies that:
	 * - DELETE endpoint accepts head path parameter and maxHead query parameter
	 * - Endpoint returns HTTP 200 OK
	 * - Delete operation is called with correct parameters
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully flag sync as performed")
	void testFlagSyncPerformed() throws Exception {
		this.mockMvc.perform(delete("/api/ad/v2/sync/{head}", 10)
				.header("ApiKey", API_KEY)
				.param("maxHead", "100"))
			.andExpect(status().isOk())
			.andDo(document("adsync-sync-delete",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				pathParameters(
					parameterWithName("head").description("The maximum ID of updates that have been synced")
				),
				queryParameters(
					parameterWithName("maxHead").description("Maximum head value (default: 0)").optional()
				)
			));

		// Verify the delete operation was called with the correct parameters
		verify(pendingADUpdateService, times(1)).deleteByIdLessThan(eq(11L), eq(100L), any(Domain.class));
	}

	/**
	 * Tests that completed sync operations can be flagged with domain filtering.
	 * <p>
	 * Verifies that:
	 * - DELETE endpoint accepts domain parameter
	 * - Valid domain returns HTTP 200 OK
	 * - Delete operation is called with correct parameters including domain
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully flag sync as performed for specific domain")
	void testFlagSyncPerformed_WithDomain() throws Exception {
		Domain domain = domainService.getAll().stream()
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No domain found in test environment"));

		this.mockMvc.perform(delete("/api/ad/v2/sync/{head}", 10)
				.header("ApiKey", API_KEY)
				.param("maxHead", "100")
				.param("domain", domain.getName()))
			.andExpect(status().isOk());

		// Verify the delete operation was called with the correct parameters including the specific domain
		verify(pendingADUpdateService, times(1)).deleteByIdLessThan(eq(11L), eq(100L), eq(domain));
	}

	/**
	 * Tests that the sync deletion endpoint returns 404 for non-existent domains.
	 * <p>
	 * Verifies that:
	 * - Invalid domain parameter returns HTTP 404 Not Found
	 * - API handles invalid domain scenario correctly when flagging sync
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 404 when flagging sync for non-existent domain")
	void testFlagSyncPerformed_InvalidDomain() throws Exception {
		this.mockMvc.perform(delete("/api/ad/v2/sync/{head}", 10)
				.header("ApiKey", API_KEY)
				.param("domain", "nonexistent-domain"))
			.andExpect(status().isNotFound());
	}
}
