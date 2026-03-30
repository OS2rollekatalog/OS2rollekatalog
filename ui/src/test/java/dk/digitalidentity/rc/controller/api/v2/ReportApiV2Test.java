package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.test.AbstractApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for Report API v2 endpoints.
 * <p>
 * Tests API endpoints for generating customizable reports about role assignments,
 * organizational structure, and user access. Reports can include various combinations
 * of users, organizational units, IT systems, KLE classifications, titles, and role information.
 * </p>
 * <p>
 * Note: All tests verify both HTTP status and REST documentation generation.
 * </p>
 */
@DisplayName("Report API v2 Tests")
public class ReportApiV2Test extends AbstractApiTest {

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.READ_ACCESS.toString());
	}

	/**
	 * Tests generation of a customized report with various options enabled.
	 * <p>
	 * Reports are generated for a specific date and can include different types of
	 * information based on the boolean flags in the request. This test demonstrates
	 * a typical report configuration including users, organizational units, IT systems,
	 * and user roles.
	 * </p>
	 * <p>
	 * Verifies that:
	 * - Endpoint returns HTTP 200 OK
	 * - Request accepts multiple configuration options
	 * - REST documentation is generated
	 * </p>
	 * <p>
	 * Configuration options:
	 * - date: Report generation date (format: yyyy-MM-dd)
	 * - showUsers: Include user information
	 * - showOUs: Include organizational unit information
	 * - showKLE: Include KLE classification codes
	 * - showItSystems: Include IT system information
	 * - showTitles: Include job titles
	 * - showInactiveUsers: Include inactive/disabled users
	 * - showUserRoles: Include user role assignments
	 * - showOUsThroughUserRoles: Show OUs through inherited user roles
	 * </p>
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should generate report with custom configuration options")
	void testGenerateReport() throws Exception {
		String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

		String requestBody = String.format("""
			{
				"date": "%s",
				"showUsers": true,
				"showOUs": true,
				"showKLE": false,
				"showItSystems": true,
				"showTitles": false,
				"showInactiveUsers": false,
				"showUserRoles": true,
				"showOUsThroughUserRoles": false
			}
			""", today);

		this.mockMvc.perform(post("/api/v2/report")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andDo(document("report-v2-generate",
				preprocessRequest(prettyPrint()),
				requestHeaders(
					headerWithName("ApiKey").description("Secret key required to call API")
				)
			));
	}

	/**
	 * Tests that report generation fails with missing or empty date field.
	 *
	 * @throws Exception if HTTP request fails
	 */
	@Test
	@DisplayName("Should return 400 when date field is missing or empty")
	void testGenerateReport_MissingDate() throws Exception {
		String requestBody = """
			{
				"date": "",
				"showUsers": true,
				"showOUs": true
			}
			""";

		this.mockMvc.perform(post("/api/v2/report")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest());
	}
}
