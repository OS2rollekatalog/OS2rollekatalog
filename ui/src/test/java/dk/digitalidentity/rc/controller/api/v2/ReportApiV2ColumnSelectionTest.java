package dk.digitalidentity.rc.controller.api.v2;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.test.AbstractApiTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Backward-compatibility tests for the per-column selection feature on POST /api/v2/report:
 * old API clients that never send "selectedColumns" must keep getting today's full column set,
 * while a client that does send a selection gets exactly (and only) those columns.
 */
@DisplayName("Report API v2 column selection tests")
class ReportApiV2ColumnSelectionTest extends AbstractApiTest {

	@Override
	protected List<String> getRequiredApiRoles() {
		return List.of(AccessRole.READ_ACCESS.toString());
	}

	@Test
	@DisplayName("Omitting selectedColumns keeps the full historical column set (backward compatibility)")
	void testGenerateReport_NoSelectedColumns_ReturnsAllColumns() throws Exception {
		String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

		String requestBody = String.format("""
			{
				"date": "%s",
				"showUsers": true,
				"showOUs": false,
				"showItSystems": false,
				"showInactiveUsers": false,
				"showUserRoles": false
			}
			""", today);

		MvcResult result = this.mockMvc.perform(post("/api/v2/report")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andReturn();

		Sheet usersSheet = getUsersSheet(result);
		Row headerRow = usersSheet.getRow(0);

		assertThat((int) headerRow.getLastCellNum()).isEqualTo(4);
		assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("UUID");
		assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("Navn");
		assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("Bruger-id");
		assertThat(headerRow.getCell(3).getStringCellValue()).isEqualTo("Stillinger");
	}

	@Test
	@DisplayName("A selectedColumns entry limits the sheet to exactly those columns, in definition order")
	void testGenerateReport_WithSelectedColumns_ReturnsOnlySelectedColumns() throws Exception {
		String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

		String requestBody = String.format("""
			{
				"date": "%s",
				"showUsers": true,
				"showOUs": false,
				"showItSystems": false,
				"showInactiveUsers": false,
				"showUserRoles": false,
				"selectedColumns": {
					"ShowUsers": ["userId", "uuid"]
				}
			}
			""", today);

		MvcResult result = this.mockMvc.perform(post("/api/v2/report")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andReturn();

		Sheet usersSheet = getUsersSheet(result);
		Row headerRow = usersSheet.getRow(0);

		// Definition order (uuid, name, userId, positions) wins over submission order, so only
		// "uuid" and "userId" appear, in that order - name/positions are excluded entirely.
		assertThat((int) headerRow.getLastCellNum()).isEqualTo(2);
		assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("UUID");
		assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("Bruger-id");
	}

	@Test
	@DisplayName("An empty selectedColumns entry excludes the sheet entirely, like unchecking its checkbox")
	void testGenerateReport_WithEmptySelectedColumns_ExcludesSheet() throws Exception {
		String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

		String requestBody = String.format("""
			{
				"date": "%s",
				"showUsers": true,
				"showOUs": false,
				"showItSystems": false,
				"showInactiveUsers": false,
				"showUserRoles": false,
				"selectedColumns": {
					"ShowUsers": []
				}
			}
			""", today);

		MvcResult result = this.mockMvc.perform(post("/api/v2/report")
				.header("ApiKey", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isOk())
			.andReturn();

		byte[] content = result.getResponse().getContentAsByteArray();
		Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content));

		assertThat(workbook.getSheet("Brugere")).isNull();
	}

	private Sheet getUsersSheet(MvcResult result) throws Exception {
		byte[] content = result.getResponse().getContentAsByteArray();
		Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content));
		Sheet sheet = workbook.getSheet("Brugere");
		assertThat(sheet).isNotNull();
		return sheet;
	}
}
