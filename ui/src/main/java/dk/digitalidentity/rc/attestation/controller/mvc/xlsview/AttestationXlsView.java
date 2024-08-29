package dk.digitalidentity.rc.attestation.controller.mvc.xlsview;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.web.servlet.view.document.AbstractXlsxStreamingView;

abstract public class AttestationXlsView extends AbstractXlsxStreamingView {
    protected CellStyle headerStyle;
    protected CellStyle titleStyle;

    protected void createSharedResources(Workbook workbook) {

        // Setup shared resources
        final Font headerFont = workbook.createFont();
        headerFont.setBold(true);

        headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);

        final Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        final short height = 400;
        titleFont.setFontHeight(height);

        titleStyle = workbook.createCellStyle();
        titleStyle.setFont(titleFont);
    }

    protected static void createCell(Row header, int column, String value, CellStyle style) {
        final Cell cell = header.createCell(column);
        cell.setCellValue(value);

        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    protected void createTitleRow(final Sheet sheet, final String title) {
        final Row titleRow = sheet.createRow(0);
        createCell(titleRow, 0, title, titleStyle);
    }
}
