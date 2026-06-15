package dk.digitalidentity.rc.controller.mvc.xlsview;

import java.util.Map;

import org.springframework.web.servlet.View;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public abstract class AbstractXlsxStreamingViewWrapper implements View {

	// xlsx files produced by POI must use the OOXML spreadsheet MIME type, not the legacy .xls "application/ms-excel".
	// This restores the content-type Spring's AbstractXlsxStreamingView set before this wrapper stopped extending it.
	private static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	@Override
	public String getContentType() {
		return CONTENT_TYPE;
	}

	@Override
	public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
		response.setContentType(getContentType());
		response.setHeader("Content-Disposition", "attachment; filename=\"" + getFilename() + "\"");

		try (DisposableSXSSFWorkbook workbook = new DisposableSXSSFWorkbook()) {
			buildExcelDocument(model, workbook, request, response);
			workbook.write(response.getOutputStream());
		}
	}

	protected abstract void buildExcelDocument(Map<String, ?> model, DisposableSXSSFWorkbook workbook,
		HttpServletRequest request, HttpServletResponse response) throws Exception;

	protected abstract String getFilename();
}
