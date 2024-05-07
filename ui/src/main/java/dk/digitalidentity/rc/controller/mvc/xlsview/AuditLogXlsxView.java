package dk.digitalidentity.rc.controller.mvc.xlsview;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.AuditLogView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.view.document.AbstractXlsxStreamingView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AuditLogXlsxView extends AbstractXlsxStreamingView {

    @SuppressWarnings("unchecked")
    @Override
    protected void buildExcelDocument(Map<String, Object> model, Workbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {
    	Locale locale = (Locale) model.get("locale");
        Iterable<AuditLogView> logs = (Iterable<AuditLogView>) model.get("logs");
        ResourceBundleMessageSource messageSource = (ResourceBundleMessageSource) model.get("messagesBundle");
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // Setup shared resources
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);

        Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.report.titles.sheet.title", null, locale));
		
        ArrayList<String> headers = new ArrayList<>();
        headers.add("html.page.log.timestamp");
        headers.add("html.page.log.auditor");
        headers.add("html.page.log.event");
        headers.add("html.page.log.target");
        headers.add("html.page.log.change");

        createHeaderRow(messageSource, locale, sheet, headers, headerStyle);

        int row = 1;
        for (AuditLogView log : logs) {
            Row dataRow = sheet.createRow(row++);

            String eventType = messageSource.getMessage(log.getEventType().getMessage(), null, locale);
            String entityType = messageSource.getMessage(log.getEntityType().getMessage(), null, locale);

            createCell(dataRow, 0, dateFormatter.format(log.getTimestamp()), null);
            createCell(dataRow, 1, log.getUsername(), null);
            createCell(dataRow, 2, eventType, null);
            createCell(dataRow, 3, log.getEntityName() + " (" + entityType + ")", null);
            createCell(dataRow, 4, log.getSecondaryEntityName(), null);
        }
	}

    private static void createCell(Row header, int column, String value, CellStyle style) {
        if (value != null && value.length() > 32767) {
            value = value.substring(0, 32767 - 3) + "...";
        }

        Cell cell = header.createCell(column);
        cell.setCellValue(value);

        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void createHeaderRow(ResourceBundleMessageSource messageSource, Locale locale, Sheet sheet, List<String> headers, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);

        int column = 0;
        for (String header : headers) {
            String localeSpecificHeader = messageSource.getMessage(header, null, locale);
            createCell(headerRow, column++, localeSpecificHeader, headerStyle);
        }
    }
}
