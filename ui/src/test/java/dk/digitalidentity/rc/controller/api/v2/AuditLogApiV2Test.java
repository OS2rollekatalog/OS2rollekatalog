package dk.digitalidentity.rc.controller.api.v2;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dk.digitalidentity.rc.dao.model.AuditLog;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.log.AuditLogEntryDao;
import dk.digitalidentity.rc.mockfactory.auditlog.MockFactory;
import dk.digitalidentity.rc.test.AbstractApiTest;

/**
 * Test suite for Audit Log API v2 endpoints.
 * <p>
 * Tests API endpoints for retrieving audit log entries, including:
 * - Getting the current head index (maximum ID)
 * - Reading audit logs with various pagination parameters
 * - Verifying response structure and field types
 * </p>
 * <p>
 * Note: All tests verify both the HTTP status and the REST documentation generation
 * to ensure API documentation stays in sync with implementation.
 * </p>
 */
@DisplayName("Audit Log API v2 Tests")
public class AuditLogApiV2Test extends AbstractApiTest {

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.AUDITLOG_ACCESS.toString());
	}

	@Autowired
	private AuditLogEntryDao auditLogEntryDao;

	@BeforeEach
	public void setUpAuditLogData() {
		AuditLog log1 = MockFactory.createAuditLog(EntityType.USER, EventType.CREATE, "admin");
		log1.setIpAddress("127.0.0.1");

		AuditLog log2 = MockFactory.createAuditLog(EntityType.USERROLE, EventType.ASSIGN_USER_ROLE, "admin",
			EntityType.USER, "user-123", "Test User");
		log2.setIpAddress("127.0.0.1");
		log2.setDescription("Assigned role to user");

		AuditLog log3 = MockFactory.createAuditLog(EntityType.ITSYSTEM, EventType.UPDATE, "system");

		AuditLog log4 = MockFactory.createAuditLog(EntityType.ORGUNIT, EventType.ASSIGN_ROLE_GROUP, "admin",
			EntityType.ROLEGROUP, "rg-456", "Test Role Group");
		log4.setIpAddress("192.168.1.1");

		AuditLog log5 = MockFactory.createAuditLog(EntityType.SYSTEMROLE, EventType.DELETE, "admin");

		auditLogEntryDao.saveAll(List.of(log1, log2, log3, log4, log5));
		entityManager.flush();
		entityManager.clear();
	}

	/**
	 * Tests retrieval of the current head index from the audit log.
	 * <p>
	 * The head index represents the maximum ID in the audit log, useful for
	 * determining if new entries have been added since last check.
	 * </p>
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response contains a 'head' field with numeric value
	 * - REST documentation is generated correctly
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return current head index of audit log")
	void testGetHeadIndex() throws Exception {
		long expectedHead = auditLogEntryDao.getMaxId();

		this.mockMvc.perform(get("/api/v2/auditlog/head")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.head").value(expectedHead))
			.andDo(document("auditlog-v2-head",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("head").type(JsonFieldType.NUMBER).description("The maximum ID in the audit log")
				)
			));
	}

	/**
	 * Tests reading audit logs with default parameters (no offset or size specified).
	 * <p>
	 * When no parameters are provided, the API uses default values for pagination.
	 * This test also serves as the primary documentation for the audit log entry structure.
	 * </p>
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array of audit log entries
	 * - All expected fields are present and correctly typed
	 * - REST documentation includes complete field descriptions
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return audit logs with default pagination parameters")
	void testGetLogs_DefaultParameters() throws Exception {
		this.mockMvc.perform(get("/api/v2/auditlog/read")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andDo(document("auditlog-v2-read-default",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				responseFields(
					fieldWithPath("[]").description("Array of audit log entries"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("Unique identifier of the audit log entry"),
					fieldWithPath("[].timestamp").type(JsonFieldType.STRING).description("Timestamp when the event occurred"),
					fieldWithPath("[].ipAddress").type(JsonFieldType.STRING).description("IP address of the user who performed the action").optional(),
					fieldWithPath("[].username").type(JsonFieldType.STRING).description("Username of the user who performed the action"),
					fieldWithPath("[].entityType").type(JsonFieldType.STRING).description("Type of the primary entity involved (e.g., USER_ROLE, IT_SYSTEM)"),
					fieldWithPath("[].entityId").type(JsonFieldType.STRING).description("ID of the primary entity"),
					fieldWithPath("[].entityName").type(JsonFieldType.STRING).description("Name of the primary entity").optional(),
					fieldWithPath("[].eventType").type(JsonFieldType.STRING).description("Type of event that occurred"),
					fieldWithPath("[].secondaryEntityType").type(JsonFieldType.STRING).description("Type of the secondary entity involved").optional(),
					fieldWithPath("[].secondaryEntityId").type(JsonFieldType.STRING).description("ID of the secondary entity").optional(),
					fieldWithPath("[].secondaryEntityName").type(JsonFieldType.STRING).description("Name of the secondary entity").optional(),
					fieldWithPath("[].description").type(JsonFieldType.STRING).description("Additional description of the event").optional()
				)
			));
	}

	/**
	 * Tests reading audit logs with custom pagination parameters.
	 * <p>
	 * Demonstrates how to use offset and size parameters for paginated retrieval.
	 * See {@link #testGetLogs_DefaultParameters()} for full response field documentation.
	 * </p>
	 * <p>
	 * Verifies that:
	 * - Endpoint accepts custom offset and size query parameters
	 * - Endpoint returns HTTP 200 OK
	 * - Response structure matches documented format
	 * - REST documentation includes parameter descriptions
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return audit logs with custom offset and size parameters")
	void testGetLogs_WithCustomParameters() throws Exception {
		this.mockMvc.perform(get("/api/v2/auditlog/read")
				.header("ApiKey", API_KEY)
				.param("offset", "0")
				.param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$.length()").value(2))
			.andDo(document("auditlog-v2-read-custom",
				preprocessResponse(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				),
				queryParameters(
					parameterWithName("offset").description("Starting position for reading audit logs (default: 0)"),
					parameterWithName("size").description("Number of audit log entries to return (default: 250)")
				),
				responseFields(
					fieldWithPath("[]").description("Array of audit log entries"),
					fieldWithPath("[].id").type(JsonFieldType.NUMBER).description("Unique identifier of the audit log entry"),
					fieldWithPath("[].timestamp").type(JsonFieldType.STRING).description("Timestamp when the event occurred"),
					fieldWithPath("[].ipAddress").type(JsonFieldType.STRING).description("IP address of the user who performed the action").optional(),
					fieldWithPath("[].username").type(JsonFieldType.STRING).description("Username of the user who performed the action"),
					fieldWithPath("[].entityType").type(JsonFieldType.STRING).description("Type of the primary entity involved (e.g., USER_ROLE, IT_SYSTEM)"),
					fieldWithPath("[].entityId").type(JsonFieldType.STRING).description("ID of the primary entity"),
					fieldWithPath("[].entityName").type(JsonFieldType.STRING).description("Name of the primary entity").optional(),
					fieldWithPath("[].eventType").type(JsonFieldType.STRING).description("Type of event that occurred"),
					fieldWithPath("[].secondaryEntityType").type(JsonFieldType.STRING).description("Type of the secondary entity involved").optional(),
					fieldWithPath("[].secondaryEntityId").type(JsonFieldType.STRING).description("ID of the secondary entity").optional(),
					fieldWithPath("[].secondaryEntityName").type(JsonFieldType.STRING).description("Name of the secondary entity").optional(),
					fieldWithPath("[].description").type(JsonFieldType.STRING).description("Additional description of the event").optional()
				)
			));
	}

	/**
	 * Tests that requesting audit logs with an offset beyond available data returns empty array.
	 * <p>
	 * Verifies graceful handling of pagination beyond data boundaries - the API should
	 * return an empty array rather than an error when offset exceeds available entries.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return empty array when offset exceeds available data")
	void testGetLogs_WithLargeOffset() throws Exception {
		this.mockMvc.perform(get("/api/v2/auditlog/read")
				.header("ApiKey", API_KEY)
				.param("offset", "10000")
				.param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isEmpty());
	}

	/**
	 * Tests that requesting zero audit log entries returns an empty array.
	 * <p>
	 * Verifies edge case handling when size parameter is set to zero.
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return empty array when size is zero")
	void testGetLogs_WithZeroSize() throws Exception {
		this.mockMvc.perform(get("/api/v2/auditlog/read")
				.header("ApiKey", API_KEY)
				.param("offset", "0")
				.param("size", "0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isEmpty());
	}

	/**
	 * Tests that the offset parameter correctly filters audit log entries by ID.
	 * <p>
	 * The offset parameter acts as an ID-based cursor (WHERE id > offset), so
	 * entries with IDs less than or equal to the offset are excluded.
	 * </p>
	 * <p>
	 * Verifies that:
	 * - First request with offset=0 returns entries starting from ID 1
	 * - Second request using the first entry's ID as offset excludes that entry
	 * - All entries in the second response have IDs greater than the offset
	 * - Size parameter limits the number of returned entries to at most 2
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should correctly apply offset as ID-based cursor to filter audit log entries")
	void testGetLogs_OffsetFiltersById() throws Exception {
		ObjectMapper mapper = new ObjectMapper();

		// First request: get first 2 entries starting from id > 0
		MvcResult firstResult = this.mockMvc.perform(get("/api/v2/auditlog/read")
				.header("ApiKey", API_KEY)
				.param("offset", "0")
				.param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andExpect(jsonPath("$").isNotEmpty())
			.andReturn();

		JsonNode firstPage = mapper.readTree(firstResult.getResponse().getContentAsString());
		assertTrue(firstPage.size() <= 2, "Size parameter should limit results to at most 2 entries");
		long firstEntryId = firstPage.get(0).get("id").asLong();
		assertTrue(firstEntryId > 0, "First entry ID should be greater than 0");

		// Second request: use first entry's ID as offset — should exclude that entry
		MvcResult secondResult = this.mockMvc.perform(get("/api/v2/auditlog/read")
				.header("ApiKey", API_KEY)
				.param("offset", String.valueOf(firstEntryId))
				.param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andReturn();

		JsonNode secondPage = mapper.readTree(secondResult.getResponse().getContentAsString());
		for (JsonNode entry : secondPage) {
			long entryId = entry.get("id").asLong();
			assertTrue(entryId > firstEntryId, "All entries should have ID > " + firstEntryId + ", but found ID " + entryId);
		}

		// Verify the first entry from page 1 is not in page 2
		if (secondPage.size() > 0) {
			long secondPageFirstId = secondPage.get(0).get("id").asLong();
			assertTrue(secondPageFirstId > firstEntryId,
				"Second page should start after the offset ID " + firstEntryId + ", but started at " + secondPageFirstId);
		}
	}
}
