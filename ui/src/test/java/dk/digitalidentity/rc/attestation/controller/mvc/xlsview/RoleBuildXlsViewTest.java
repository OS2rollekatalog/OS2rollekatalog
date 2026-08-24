package dk.digitalidentity.rc.attestation.controller.mvc.xlsview;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

import dk.digitalidentity.rc.attestation.model.dto.ITSystemRoleBuildAttestationDTO;
import dk.digitalidentity.rc.attestation.service.AttestationLockService;
import dk.digitalidentity.rc.controller.mvc.xlsview.DisposableSXSSFWorkbook;

@ExtendWith(MockitoExtension.class)
@DisplayName("Role Build Xls View Tests")
class RoleBuildXlsViewTest {

	@Mock
	private AttestationLockService lockService;

	@Test
	@DisplayName("Should render rows with missing responsible users and status without failing the download")
	void buildExcelDocument_WithIncompleteRow_ShouldNotThrow() throws Exception {
		// Arrange - a row for an it-system without an attestation in the period leaves these fields unset
		ITSystemRoleBuildAttestationDTO row = new ITSystemRoleBuildAttestationDTO();
		row.setItSystemName("KMD Opus");
		row.setRole("Lønmedarbejder");
		row.setSystemRole(null);
		row.setResponsibleUserNames(null);
		row.setAttestationStatus(null);

		RoleBuildXlsView view = new RoleBuildXlsView(lockService);

		// Act + Assert - an exception here reaches the browser as a truncated xlsx (ERR_INVALID_RESPONSE)
		try (DisposableSXSSFWorkbook workbook = new DisposableSXSSFWorkbook()) {
			assertDoesNotThrow(() -> view.buildExcelDocument(model(List.of(row)), workbook, null, null));

			var sheet = workbook.getSheetAt(0);
			assertEquals("KMD Opus", sheet.getRow(4).getCell(0).getStringCellValue());
			assertEquals("", sheet.getRow(4).getCell(2).getStringCellValue());
			assertEquals("", sheet.getRow(4).getCell(3).getStringCellValue());
			assertEquals("Ikke attesteret", sheet.getRow(4).getCell(4).getStringCellValue());
		}
	}

	private Map<String, Object> model(List<ITSystemRoleBuildAttestationDTO> rows) {
		ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
		messageSource.setBasename("messages");
		messageSource.setDefaultEncoding("UTF-8");

		Map<String, Object> model = new HashMap<>();
		model.put("messageSource", messageSource);
		model.put("locale", Locale.of("da"));
		model.put("from", LocalDate.of(2025, 8, 11));
		model.put("to", LocalDate.of(2026, 8, 11));
		model.put("itSystemRoleAttestationDTO", rows);
		return model;
	}
}
