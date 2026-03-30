package dk.digitalidentity.rc.attestation.controller.mvc.xlsview;

import dk.digitalidentity.rc.attestation.model.dto.ITSystemRoleBuildAttestationDTO;
import dk.digitalidentity.rc.attestation.model.entity.AttestationLock;
import dk.digitalidentity.rc.attestation.service.AttestationLockService;
import dk.digitalidentity.rc.controller.mvc.xlsview.DisposableSXSSFWorkbook;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dk.digitalidentity.rc.attestation.AttestationConstants.REPORT_LOCK_NAME;

public class RoleBuildXlsView extends AttestationXlsView {
	private final AttestationLockService lockService;
	private ResourceBundleMessageSource messageSource;
	private Locale locale;
	private LocalDate fromDate;
	private LocalDate toDate;
	private List<ITSystemRoleBuildAttestationDTO> itSystemRoleAttestationDTO;

	public RoleBuildXlsView(final AttestationLockService lockService) {
		this.lockService = lockService;
	}

	/**
	 * Ensure only one report can be generated at the time.
	 * Will acquire a row lock on an {@link AttestationLock}
	 */
	public void lockedScope(Runnable r) {
		try {
			lockService.acquireLock(REPORT_LOCK_NAME);
			r.run();
		} finally {
			lockService.releaseLock(REPORT_LOCK_NAME);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void buildExcelDocument(Map<String, ?> model, DisposableSXSSFWorkbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {
		// Get data
		messageSource = (ResourceBundleMessageSource) model.get("messageSource");
		locale = (Locale) model.get("locale");
		fromDate = (LocalDate) model.get("from");
		toDate = (LocalDate) model.get("to");
		itSystemRoleAttestationDTO = (List<ITSystemRoleBuildAttestationDTO>) model.get("itSystemRoleAttestationDTO");

		createSharedResources(workbook);

		lockedScope(() -> createRoleBuildSheet(workbook));
	}

	private void createRoleBuildSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet(messageSource.getMessage("attestationmodule.xls.report.rolebuild.title", null, locale));

		createTitleRow(sheet, messageSource.getMessage("attestationmodule.xls.report.rolebuild.title", null, locale));
		createInfoRow(sheet);

		ArrayList<String> headers = new ArrayList<>();
		headers.add(messageSource.getMessage("attestationmodule.xls.report.rolebuild.itsystem", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.rolebuild.userRole", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.rolebuild.systemRole", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.rolebuild.ResponsibleUser", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.rolebuild.status", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.rolebuild.date", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.rolebuild.performedBy", null, locale));

		createHeaderRow(sheet, headers);

		int row = 4;
		for (ITSystemRoleBuildAttestationDTO entry : itSystemRoleAttestationDTO) {
			Row dataRow = sheet.createRow(row++);
			int column = 0;

			createCell(dataRow, column++, entry.getItSystemName(), null);
			createCell(dataRow, column++, entry.getRole(), null);
			createCell(dataRow, column++, String.join("\n", entry.getSystemRole()), null);
			createCell(dataRow, column++, entry.getResponsibleUser(), null);
			createCell(dataRow, column++, messageSource.getMessage(entry.getAttestationStatus().getMessage(), null, locale), null);
			createCell(dataRow, column++, entry.getAttestationDate() == null ? "" : entry.getAttestationDate().toString(), null);
			createCell(dataRow, column++, entry.getPerformedBy(), null);
		}

		sheet.setColumnWidth(0, 45 * 256);
		sheet.setColumnWidth(1, 45 * 256);
		sheet.setColumnWidth(2, 45 * 256);
		sheet.setColumnWidth(3, 20 * 256);
		sheet.setColumnWidth(4, 45 * 256);
		sheet.setColumnWidth(5, 20 * 256);
		sheet.setColumnWidth(6, 20 * 256);
	}

	private void createHeaderRow(Sheet sheet, List<String> headers) {
		Row headerRow = sheet.createRow(3);

		int column = 0;
		for (String header : headers) {
			createCell(headerRow, column++, header, headerStyle);
		}
	}

	private void createInfoRow(Sheet sheet) {
		Row titleRow = sheet.createRow(1);

		createCell(titleRow, 0, fromDate.toString(), null);
		createCell(titleRow, 1, messageSource.getMessage("attestationmodule.xls.report.rolebuild.to", null, locale), null);
		createCell(titleRow, 2, toDate.toString(), null);
	}

	@Override
	protected String getFilename() {
		return "rolleopbygning_alle.xlsx";
	}
}
