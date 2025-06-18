package dk.digitalidentity.rc.rolerequest.controller.mvc.xlsxView;

import dk.digitalidentity.rc.rolerequest.model.entity.RequestLog;
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

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RequestLogXlsxView extends AbstractXlsxStreamingView {

    @SuppressWarnings("unchecked")
    @Override
    protected void buildExcelDocument(Map<String, Object> model, Workbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {
    	Locale locale = (Locale) model.get("locale");
        Iterable<RequestLog> logs = (Iterable<RequestLog>) model.get("logs");
        ResourceBundleMessageSource messageSource = (ResourceBundleMessageSource) model.get("messagesBundle");

        // Setup shared resources
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);

        Sheet sheet = workbook.createSheet(messageSource.getMessage("requestmodule.xslx.log.sheet.title", null, locale));

        ArrayList<String> headers = new ArrayList<>();
        headers.add("requestmodule.html.log.table.header.timestamp");
        headers.add("requestmodule.html.log.table.header.actinguser");
        headers.add("requestmodule.html.log.table.header.affecteduser");
        headers.add("requestmodule.html.log.table.header.action");
        headers.add("requestmodule.html.log.table.header.rolename");
        headers.add("requestmodule.html.log.table.header.itsystem");
        headers.add("requestmodule.html.log.table.header.details");

        createHeaderRow(messageSource, locale, sheet, headers, headerStyle);

        int row = 1;
        for (RequestLog log : logs) {
            Row dataRow = sheet.createRow(row++);

            createCell(dataRow, 0, log.getRequestTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) , null);
            createCell(dataRow, 1, log.getActingUser().getName(), null);
            createCell(dataRow, 2, log.getTargetUser().getName(), null);
            createCell(dataRow, 3, messageSource.getMessage(log.getRequestEvent().getMessage(), null, locale), null);
            createCell(dataRow, 4, log.getUserRole() == null ? log.getRoleGroup().getName() : log.getUserRole().getName(), null);
            createCell(dataRow, 5, log.getUserRole() == null ? "(Rollebuket)" : log.getUserRole().getItSystem().getName(), null);
            createCell(dataRow, 5, log.getDetails(), null);
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
