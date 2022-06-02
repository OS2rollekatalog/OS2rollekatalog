package dk.digitalidentity.rc.controller.mvc.xlsview;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.view.document.AbstractXlsxView;

import dk.digitalidentity.rc.dao.model.User;

public class ManagersXlsxView extends AbstractXlsxView {

    @SuppressWarnings("unchecked")
    @Override
    protected void buildExcelDocument(Map<String, Object> model, Workbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {
    	Locale locale = (Locale) model.get("locale");
        Iterable<User> managers = (Iterable<User>) model.get("managers");
        ResourceBundleMessageSource messageSource = (ResourceBundleMessageSource) model.get("messagesBundle");
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // Setup shared resources
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);

        Sheet sheet = workbook.createSheet(messageSource.getMessage("xls.managers.sheet.title", null, locale));
		
        ArrayList<String> headers = new ArrayList<>();
        headers.add("html.page.manager.uuid");
        headers.add("html.page.manager.name");
        headers.add("html.page.manager.user");
        headers.add("html.page.manager.substitute.uuid");
        headers.add("html.page.manager.substitute.name");
        headers.add("html.page.manager.substitute.user");
        headers.add("html.page.manager.substitute.date");
        headers.add("html.page.manager.substitute.assigned_by");

        createHeaderRow(messageSource, locale, sheet, headers, headerStyle);

        int row = 1;
        for (User manager : managers) {
            Row dataRow = sheet.createRow(row++);

            String assignedBy = "";
            if (manager.getSubstituteAssignedBy() == null && manager.getManagerSubstitute() != null) {
            	assignedBy = manager.getName() + "(" + manager.getUserId() + ")";
            }
            else if (manager.getSubstituteAssignedBy() != null && manager.getManagerSubstitute() != null) {
            	assignedBy = manager.getSubstituteAssignedBy();
            }
            
            createCell(dataRow, 0, manager.getUuid(), null);
            createCell(dataRow, 1, manager.getName(), null);
            createCell(dataRow, 2, manager.getUserId(), null);
            createCell(dataRow, 3, manager.getManagerSubstitute() == null ? "" : manager.getManagerSubstitute().getUuid(), null);
            createCell(dataRow, 4, manager.getManagerSubstitute() == null ? "" : manager.getManagerSubstitute().getName(), null);
            createCell(dataRow, 5, manager.getManagerSubstitute() == null ? "" : manager.getManagerSubstitute().getUserId(), null);
            createCell(dataRow, 6, (manager.getSubstituteAssignedTts() != null) ? dateFormatter.format(manager.getSubstituteAssignedTts()) : "", null);
            createCell(dataRow, 7, assignedBy, null);
        }
        
        format(sheet);
	}

	private void format(Sheet sheet) {
		sheet.autoSizeColumn(0);
		sheet.autoSizeColumn(1);
		sheet.autoSizeColumn(2);
		sheet.autoSizeColumn(3);
		sheet.autoSizeColumn(4);
		sheet.autoSizeColumn(5);
		sheet.autoSizeColumn(6);
		sheet.autoSizeColumn(7);
	}

    private static void createCell(Row header, int column, String value, CellStyle style) {
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
