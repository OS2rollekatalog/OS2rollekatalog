package dk.digitalidentity.rc.attestation.controller.mvc.xlsview;

import static dk.digitalidentity.rc.attestation.AttestationConstants.REPORT_LOCK_NAME;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dk.digitalidentity.rc.attestation.model.dto.ADAttestationUserDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.AttestationStatus;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.context.support.ResourceBundleMessageSource;

import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentReportRowDTO;
import dk.digitalidentity.rc.attestation.model.entity.AttestationLock;
import dk.digitalidentity.rc.attestation.service.AttestationLockService;
import dk.digitalidentity.rc.attestation.service.report.AttestationReportPaginator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RoleAssignmentXlsView extends AttestationXlsView {
	private final AttestationLockService lockService;
	private AttestationReportPaginator paginator;
	private ResourceBundleMessageSource messageSource;
	private Locale locale;
	private String ouName;
	private String itSystemName;
	private LocalDate fromDate;
	private LocalDate toDate;
	private boolean includeUsers;
	private List<ADAttestationUserDTO> adUsersAttestation;

	public RoleAssignmentXlsView(final AttestationLockService lockService) {
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
	protected void buildExcelDocument(Map<String, Object> model, Workbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {
		// Get data
		paginator = (AttestationReportPaginator) model.get("rowPaginator");
		messageSource = (ResourceBundleMessageSource) model.get("messageSource");
		locale = (Locale) model.get("locale");
		ouName = (String) model.get("orgUnitName");
		itSystemName = (String) model.get("itSystemName");
		fromDate = (LocalDate) model.get("from");
		toDate = (LocalDate) model.get("to");
		includeUsers = (boolean) (model.get("includeUsers") != null ? model.get("includeUsers") : false);
		adUsersAttestation = (List<ADAttestationUserDTO>) model.get("adUsersAttestation");

		createSharedResources(workbook);

		// Create Sheets
		lockedScope(() -> createPersonsSheet(workbook));

		if (includeUsers) {
			lockedScope(() -> createADUserSheet(workbook));
		}
	}

	private void createADUserSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet(messageSource.getMessage("attestationmodule.xls.report.orgunits.ad.title", null, locale));

		createTitleRow(sheet, messageSource.getMessage("attestationmodule.xls.report.orgunits.ad.title", null, locale));

		ArrayList<String> headers = new ArrayList<>();
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.ad.uuid", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.userName", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.userId", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.responsibleOu", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.responsibleUser", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.verifiedAt", null, locale));

		createHeaderRow(sheet, headers);

		int row = 5;
		for (ADAttestationUserDTO entry : adUsersAttestation) {
				Row dataRow = sheet.createRow(row++);
				int column = 0;

				createCell(dataRow, column++, entry.getUuid(), null);
				createCell(dataRow, column++, entry.getName(), null);
				createCell(dataRow, column++, entry.getUsername(), null);
				createCell(dataRow, column++, entry.getResponsibleOU(), null);
				createCell(dataRow, column++, entry.getResponsibleUser(), null);
				createCell(dataRow, column++, entry.getVerifiedAt() == null ? "" : entry.getVerifiedAt().toString(), null);
		}

		sheet.setColumnWidth(0, 45 * 256);
		sheet.setColumnWidth(1, 45 * 256);
		sheet.setColumnWidth(2, 15 * 256);
		sheet.setColumnWidth(3, 45 * 256);
		sheet.setColumnWidth(4, 45 * 256);
		sheet.setColumnWidth(5, 20 * 256);
	}

	private void createPersonsSheet(Workbook workbook) {
		Sheet sheet = workbook.createSheet(messageSource.getMessage("attestationmodule.xls.report.orgunits.title", null, locale));

		createTitleRow(sheet, messageSource.getMessage("attestationmodule.xls.report.orgunits.title", null, locale));
		createOUInfoRow(sheet);

		ArrayList<String> headers = new ArrayList<>();
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.userName", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.userId", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.position", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.orgUnit", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.userRole", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.postponedConstraints", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.itSystem", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.roleGroup", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.roleStatus", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.validFrom", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.validTo", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.assignedFrom", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.inherited", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.assignedThroughType", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.assignedThrough", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.responsibleOu", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.responsibleUser", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.attestationStatus", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.verifiedAt", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.verifiedBy", null, locale));
		headers.add(messageSource.getMessage("attestationmodule.xls.report.orgunits.remark", null, locale));

		createHeaderRow(sheet, headers);

		int row = 5;
		while (paginator.hasNext()) {
			for (RoleAssignmentReportRowDTO entry : paginator.next()) {
				Row dataRow = sheet.createRow(row++);
				int column = 0;

				String attestationStatusMessage = entry.getAttestationStatus().getMessage();
				if (entry.getAttestationStatus().equals(AttestationStatus.NOT_VERIFIED) && entry.getValidTo() != null && entry.getAttestationCreatedAt() != null) {
					if (entry.getValidTo().isBefore(entry.getAttestationCreatedAt())) {
						attestationStatusMessage = "attestationmodule.enums.attestationStatus.notVerified.outsideAttestation";
					}
				}

				createCell(dataRow, column++, entry.getUserName(), null);
				createCell(dataRow, column++, entry.getUserUserId(), null);
				createCell(dataRow, column++, entry.getPosition(), null);
				createCell(dataRow, column++, entry.getOrgUnit(), null);
				createCell(dataRow, column++, entry.getUserRoleName(), null);
				createCell(dataRow, column++, entry.getPostponedConstraints(), null);
				createCell(dataRow, column++, entry.getItSystemName(), null);
				createCell(dataRow, column++, entry.getRoleGroupName(), null);
				createCell(dataRow, column++, messageSource.getMessage(entry.getStatus().getMessage(), null, locale), null);
				createCell(dataRow, column++, entry.getAssignedFrom() == null ? "" : entry.getAssignedFrom().toString(), null);
				createCell(dataRow, column++, entry.getAssignedTo() == null ? "" : entry.getAssignedTo().toString(), null);
				createCell(dataRow, column++, entry.getOriginallyAssignedFrom() == null ? "" : entry.getOriginallyAssignedFrom().toString(), null);
				createCell(dataRow, column++, entry.isInherited() ? "Ja" : "Nej", null);
				createCell(dataRow, column++, entry.getAssignedThroughType(), null);
				createCell(dataRow, column++, entry.getAssignedThrough(), null);
				createCell(dataRow, column++, entry.getResponsibleOu(), null);
				createCell(dataRow, column++, entry.getResponsibleUser(), null);
				createCell(dataRow, column++, messageSource.getMessage(attestationStatusMessage, null, locale), null);
				createCell(dataRow, column++, entry.getVerifiedAt() == null ? "" : entry.getVerifiedAt().toString(), null);
				createCell(dataRow, column++, entry.getVerifiedByName() == null ? "" : entry.getVerifiedByName() + " (" + entry.getVerifiedByUserId() + ")", null);
				createCell(dataRow, column++, entry.getRemark(), null);
			}
		}
		sheet.setColumnWidth(0, 45 * 256);
		sheet.setColumnWidth(1, 25 * 256);
		sheet.setColumnWidth(2, 30 * 256);
		sheet.setColumnWidth(3, 35 * 256);
		sheet.setColumnWidth(4, 30 * 256);
		sheet.setColumnWidth(5, 30 * 256);
		sheet.setColumnWidth(6, 30 * 256);
		sheet.setColumnWidth(7, 30 * 256);
		sheet.setColumnWidth(8, 18 * 256);
		sheet.setColumnWidth(9, 18 * 256);
		sheet.setColumnWidth(10, 18 * 256);

		sheet.setColumnWidth(11, 15 * 256);
		sheet.setColumnWidth(12, 18 * 256);
		sheet.setColumnWidth(13, 18 * 256);
		sheet.setColumnWidth(14, 35 * 256);
		sheet.setColumnWidth(15, 35 * 256);

		sheet.setColumnWidth(16, 25 * 256);
		sheet.setColumnWidth(17, 25 * 256);
		sheet.setColumnWidth(18, 50 * 256);
		sheet.setColumnWidth(19, 50 * 256);

	}

	private void createHeaderRow(Sheet sheet, List<String> headers) {
		Row headerRow = sheet.createRow(4);

		int column = 0;
		for (String header : headers) {
			createCell(headerRow, column++, header, headerStyle);
		}
	}

	private void createOUInfoRow(Sheet sheet) {
		Row titleRow = sheet.createRow(1);

		if (ouName != null) {
			createCell(titleRow, 0, ouName, headerStyle);
		}
		else if (itSystemName != null) {
			createCell(titleRow, 0, itSystemName, headerStyle);
		}

		createCell(titleRow, 5, fromDate.toString(), null);
		createCell(titleRow, 6, messageSource.getMessage("attestationmodule.xls.report.orgunits.to", null, locale), null);
		createCell(titleRow, 7, toDate.toString(), null);
	}

}
