package dk.digitalidentity.rc.controller.mvc.xlsview;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.context.support.ResourceBundleMessageSource;

import dk.digitalidentity.rc.controller.mvc.viewmodel.OuRoleAssignmentReportRow;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryTitle;
import dk.digitalidentity.rc.dao.history.model.HistoryUser;

/**
 * Builds the "org unit allocations" sheet, sourced from {@code historic_ou_assignment}.
 */
class OrgUnitAllocationSheetWriter {

	private final ResourceBundleMessageSource messageSource;
	private final Locale locale;
	private final CellStyle headerStyle;
	private final CellStyle wrapStyle;
	private final Map<String, HistoryUser> users;
	private final Map<String, HistoryTitle> titleMap;
	private final Map<String, HistoryOU> orgUnits;
	private final Map<String, List<OuRoleAssignmentReportRow>> ouRoleAssignments;

	OrgUnitAllocationSheetWriter(
			final ResourceBundleMessageSource messageSource,
			final Locale locale,
			final CellStyle headerStyle,
			final CellStyle wrapStyle,
			final Map<String, HistoryUser> users,
			final Map<String, HistoryTitle> titleMap,
			final Map<String, HistoryOU> orgUnits,
			final Map<String, List<OuRoleAssignmentReportRow>> ouRoleAssignments) {
		this.messageSource = messageSource;
		this.locale = locale;
		this.headerStyle = headerStyle;
		this.wrapStyle = wrapStyle;
		this.users = users;
		this.titleMap = titleMap;
		this.orgUnits = orgUnits;
		this.ouRoleAssignments = ouRoleAssignments;
	}

	void write(final Workbook workbook) {
		final Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.orgUnitAllocations.sheet.title", null, locale));

		final List<String> headers = new ArrayList<>();
		headers.add("xls.report.orgUnitAllocations.rolegroup");
		headers.add("xls.report.orgUnitAllocations.rolegroup.description");
		headers.add("xls.report.orgUnitAllocations.role.name");
		headers.add("xls.report.orgUnitAllocations.role.description");
		headers.add("xls.report.orgUnitAllocations.orgunit");
		headers.add("xls.report.orgUnitAllocations.users.excluded");
		headers.add("xls.report.orgUnitAllocations.orgunit.inheritance");
		headers.add("xls.report.orgUnitAllocations.positions");
		headers.add("xls.report.orgUnitAllocations.positions.exempt");

		createHeaderRow(sheet, headers);

		int y = 1;
		for (final Map.Entry<String, List<OuRoleAssignmentReportRow>> entry : ouRoleAssignments.entrySet()) {
			final HistoryOU ou = orgUnits.get(entry.getKey());
			if (ou == null) {
				continue;
			}

			for (final OuRoleAssignmentReportRow oura : entry.getValue()) {
				final String excludedUsers = joinExcludedUsers(oura.getExceptedUserUuids());
				final String positions = joinTitleNames(oura.getPositionTitleUuids());
				final String excludedPositions = joinTitleNames(oura.getExcludedPositionTitleUuids());

				final Row row = sheet.createRow(y++);
				int x = 0;
				ReportXlsxView.createCell(row, x++, oura.getRoleRoleGroup(), null);
				ReportXlsxView.createCell(row, x++, oura.getRoleGroupDescription(), null);
				ReportXlsxView.createCell(row, x++, oura.getRoleName(), null);
				ReportXlsxView.createCell(row, x++, oura.getRoleDescription(), null);
				ReportXlsxView.createCell(row, x++, ou.getOuName(), null);
				ReportXlsxView.createCell(row, x++, excludedUsers, wrapStyle);
				ReportXlsxView.createCell(row, x++, oura.isInherit() ? "Ja" : "Nej", null);
				ReportXlsxView.createCell(row, x++, positions, wrapStyle);
				ReportXlsxView.createCell(row, x++, excludedPositions, wrapStyle);
			}
		}
	}

	private void createHeaderRow(final Sheet sheet, final List<String> headers) {
		final Row headerRow = sheet.createRow(0);

		int column = 0;
		for (final String header : headers) {
			final String localeSpecificHeader = messageSource.getMessage(header, null, locale);
			ReportXlsxView.createCell(headerRow, column++, localeSpecificHeader, headerStyle);
		}
	}

	private String joinExcludedUsers(final List<String> userUuids) {
		if (userUuids == null) {
			return "";
		}
		return userUuids.stream()
			.map(userUuid -> users.containsKey(userUuid)
				? users.get(userUuid).getUserName() + " (" + users.get(userUuid).getUserUserId() + ")"
				: userUuid)
			.collect(Collectors.joining("\n"));
	}

	private String joinTitleNames(final List<String> titleUuids) {
		if (titleUuids == null) {
			return "";
		}
		return titleUuids.stream()
			.map(titleUuid -> titleMap.containsKey(titleUuid) ? titleMap.get(titleUuid).getTitleName() : titleUuid)
			.collect(Collectors.joining("\n"));
	}
}
