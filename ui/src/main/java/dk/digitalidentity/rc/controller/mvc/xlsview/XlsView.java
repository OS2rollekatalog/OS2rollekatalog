package dk.digitalidentity.rc.controller.mvc.xlsview;

import java.util.ArrayList;
import java.util.Map;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.springframework.web.servlet.view.document.AbstractXlsxStreamingView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class XlsView extends AbstractXlsxStreamingView {

	@Override
	@SuppressWarnings("unchecked")
	protected void buildExcelDocument(Map<String, Object> model, Workbook workbook, HttpServletRequest request, HttpServletResponse response) throws Exception {
		ArrayList<String> headers = (ArrayList<String>) model.get("headers");
		ArrayList<ArrayList<Object>> rows = (ArrayList<ArrayList<Object>>) model.get("rows");
		String sheetName = (String) model.get("sheetName");

		Sheet sheet = workbook.createSheet(sheetName);
		if (sheet instanceof SXSSFSheet) {
			((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
		}

		Font headerFont = workbook.createFont();
		headerFont.setBold(true);

		CellStyle headerStyle = workbook.createCellStyle();
		headerStyle.setFont(headerFont);

		CellStyle rowStyle = workbook.createCellStyle();
		rowStyle.setWrapText(true);

		// create header row
		Row header = sheet.createRow(0);
		for (int i = 0; i < headers.size(); i++) {
			createCell(header, i, headers.get(i), headerStyle);
		}

		// all the other rows
		int rowCount = 1;
		for (ArrayList<Object> row : rows) {
			Row courseRow = sheet.createRow(rowCount++);
			
			for (int i = 0; i < row.size(); i++) {
				createCell(courseRow, i, row.get(i), rowStyle);
			}

			courseRow.setRowStyle(rowStyle);
		}

		for (int i = 0; i < headers.size(); i++) {
			sheet.autoSizeColumn(i);
		}
	}

	private static void createCell(Row row, int column, Object value, CellStyle style) {
		Cell cell = row.createCell(column);

		if (value instanceof Double doubleValue) {
			cell.setCellValue(doubleValue);
		}
		else if (value != null) {
			cell.setCellValue(value.toString());
		}
		else {
			cell.setCellValue("");
		}
		
		cell.setCellStyle(style);
	}
}