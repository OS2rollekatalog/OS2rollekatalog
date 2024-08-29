package dk.digitalidentity.rc.attestation.controller.mvc.xlsview;

import dk.digitalidentity.rc.attestation.model.dto.AttestationRunDTO;
import dk.digitalidentity.rc.attestation.model.dto.AttestationStatusListDTO;
import dk.digitalidentity.rc.dao.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RunOverviewXlsView extends AttestationXlsView {

    @Override
    @SuppressWarnings({"DuplicatedCode", "NullableProblems"})
    protected void buildExcelDocument(Map<String, Object> model, Workbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {
        createSharedResources(workbook);

        final AttestationRunDTO run = (AttestationRunDTO) model.get("run");

        createOrgUnitsOverview(workbook, run);
        createItSystemOverview(workbook, run);
    }

    private void createOrgUnitsOverview(final Workbook workbook, final AttestationRunDTO run) {
        final Sheet sheet = workbook.createSheet("Enheder");
        createTitleRow(sheet, "Enheder");
        createInfoRow(sheet, run);
        createHeaderRow(sheet, List.of("Enhed", "Sti", "Manager", "Stedfortrædere", "Status"));
        int row = 4;
        for (final AttestationStatusListDTO status : run.getOuStatus()) {
            final Row dataRow = sheet.createRow(row++);
            int column = 0;

            createCell(dataRow, column++, status.getName(), null);
            createCell(dataRow, column++, status.getPath(), null);
            createCell(dataRow, column++, status.getManager() != null ? status.getManager().getName() : "", null);
            createCell(dataRow, column++, status.getSubstitutes() != null ? status.getSubstitutes().stream().map(User::getName).collect(Collectors.joining(", ")) : "", null);
            createCell(dataRow, column++, status.getStatus().getCaption(), null);
        }
        sheet.setColumnWidth(0, 40 * 256);
        sheet.setColumnWidth(1, 70 * 256);
        sheet.setColumnWidth(2, 35 * 256);
        sheet.setColumnWidth(3, 40 * 256);
        sheet.setColumnWidth(4, 30 * 256);
    }

    private void createItSystemOverview(final Workbook workbook, final AttestationRunDTO run) {
        Sheet sheet = workbook.createSheet("IT-systemer");
        createTitleRow(sheet, "IT-systemer");
        createInfoRow(sheet, run);
        createHeaderRow(sheet, List.of("IT-system", "Type", "Ansvarlig", "Status"));

        int row = 4;
        for (final AttestationStatusListDTO status : run.getSystemStatus()) {
            final Row dataRow = sheet.createRow(row++);
            int column = 0;

            createCell(dataRow, column++, status.getName(), null);
            createCell(dataRow, column++, status.getPath(), null);
            createCell(dataRow, column++, status.getResponsibleUser() != null ? status.getResponsibleUser().getName() : "", null);
            createCell(dataRow, column++, status.getStatus().getCaption(), null);
        }
        sheet.setColumnWidth(0, 45 * 256);
        sheet.setColumnWidth(1, 45 * 256);
        sheet.setColumnWidth(2, 45 * 256);
        sheet.setColumnWidth(3, 30 * 256);
    }


    private void createInfoRow(final Sheet sheet, final AttestationRunDTO run) {
        final Row titleRow = sheet.createRow(1);

        createCell(titleRow, 0, "Udtrukket", null);
        createCell(titleRow, 1, run.getCreatedAt().toString(), null);
        createCell(titleRow, 2, "Deadline", null);
        createCell(titleRow, 3, run.getDeadline().toString(), null);
    }

    private void createHeaderRow(final Sheet sheet, final List<String> headers) {
        Row headerRow = sheet.createRow(3);

        int column = 0;
        for (String header : headers) {
            createCell(headerRow, column++, header, headerStyle);
        }
    }

}
