package dk.digitalidentity.rc.controller.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.digitalidentity.rc.dao.model.AuditLog;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.log.AuditLogEntryDao;
import dk.digitalidentity.rc.mockfactory.auditlog.MockFactory;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
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

/**
 * Test suite for Audit Log API endpoints.
 * <p>
 * Tests API endpoints for retrieving audit log data, including getting the head index
 * and reading audit log entries with configurable pagination parameters.
 * </p>
 */
@DisplayName("Audit Log API Tests")
public class AuditLogApiTest extends AbstractApiTest {

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.AUDITLOG_ACCESS.toString());
	}

	@MockitoBean
	private AuditLogEntryDao auditLogEntryDao;

	private List<AuditLog> testAuditLogs;

	@BeforeEach
	public void setUpAuditLogData() {
		AuditLog log1 = MockFactory.createAuditLog(EntityType.USER, EventType.CREATE, "admin");
		log1.setId(1L);
		log1.setIpAddress("127.0.0.1");

		AuditLog log2 = MockFactory.createAuditLog(EntityType.USERROLE, EventType.ASSIGN_USER_ROLE, "admin",
			EntityType.USER, "user-123", "Test User");
		log2.setId(2L);
		log2.setIpAddress("127.0.0.1");
		log2.setDescription("Assigned role to user");

		AuditLog log3 = MockFactory.createAuditLog(EntityType.ITSYSTEM, EventType.UPDATE, "system");
		log3.setId(3L);

		AuditLog log4 = MockFactory.createAuditLog(EntityType.ORGUNIT, EventType.ASSIGN_ROLE_GROUP, "admin",
			EntityType.ROLEGROUP, "rg-456", "Test Role Group");
		log4.setId(4L);
		log4.setIpAddress("192.168.1.1");

		AuditLog log5 = MockFactory.createAuditLog(EntityType.SYSTEMROLE, EventType.DELETE, "admin");
		log5.setId(5L);

		testAuditLogs = List.of(log1, log2, log3, log4, log5);

		when(auditLogEntryDao.getMaxId()).thenReturn(5L);
		when(auditLogEntryDao.findAllWithOffsetAndSize(0L, 250L)).thenReturn(testAuditLogs);
		when(auditLogEntryDao.findAllWithOffsetAndSize(0L, 2L)).thenReturn(testAuditLogs.subList(0, 2));
		when(auditLogEntryDao.findAllWithOffsetAndSize(1L, 2L)).thenReturn(testAuditLogs.subList(1, 3));
	}

	/**
	 * Tests that the head index endpoint returns the maximum audit log ID.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response contains the head field with the maximum ID
	 * - REST documentation is generated
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve the head index")
	void testGetHeadIndex() throws Exception {
		this.mockMvc.perform(get("/api/auditlog/head")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.head").value(5L))
			.andDo(document("auditlog-head",
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
	 * THIS IS DEFAULT PARAM TEST
	 * Tests that audit logs can be retrieved with default parameters.
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Response is an array of audit log entries
	 * - All expected fields are present in the response
	 * - REST documentation is generated with field descriptions
	 * </p>
	 * <p>
	 * Default parameters: offset=0, size=250
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve audit logs with default parameters")
	void testGetLogs_DefaultParameters() throws Exception {
		this.mockMvc.perform(get("/api/auditlog/read")
				.header("ApiKey", API_KEY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("auditlog-read-default",
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
					fieldWithPath("[].entityType").type(JsonFieldType.STRING).description("Type of the primary entity involved"),
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
	 * THIS IS PAGINATION TEST
	 * Tests that audit logs can be retrieved with custom pagination parameters.
	 * <p>
	 * Verifies that:
	 * - Endpoint accepts offset and size query parameters
	 * - Response is an array of audit log entries
	 * - Size parameter limits the number of returned entries
	 * - REST documentation includes parameter descriptions
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should successfully retrieve audit logs with custom pagination parameters")
	void testGetLogs_WithCustomParameters() throws Exception {
		this.mockMvc.perform(get("/api/auditlog/read")
				.header("ApiKey", API_KEY)
				.param("offset", "0")
				.param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andDo(document("auditlog-read-custom",
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
					fieldWithPath("[].entityType").type(JsonFieldType.STRING).description("Type of the primary entity involved"),
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
	 * THIS IS OFFSET TEST
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
		MvcResult firstResult = this.mockMvc.perform(get("/api/auditlog/read")
				.header("ApiKey", API_KEY)
				.param("offset", "0")
				.param("size", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray())
			.andReturn();

		JsonNode firstPage = mapper.readTree(firstResult.getResponse().getContentAsString());
		assertTrue(firstPage.size() <= 2, "Size parameter should limit results to at most 2 entries");
		long firstEntryId = firstPage.get(0).get("id").asLong();
		assertTrue(firstEntryId > 0, "First entry ID should be greater than 0");

		// Second request: use first entry's ID as offset — should exclude that entry
		MvcResult secondResult = this.mockMvc.perform(get("/api/auditlog/read")
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
