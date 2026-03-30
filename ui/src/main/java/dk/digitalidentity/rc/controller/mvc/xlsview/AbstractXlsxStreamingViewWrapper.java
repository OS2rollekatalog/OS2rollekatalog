package dk.digitalidentity.rc.controller.mvc.xlsview;

import java.util.Map;

import org.springframework.web.servlet.View;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public abstract class AbstractXlsxStreamingViewWrapper implements View {

	private static final String CONTENT_TYPE = "application/ms-excel";

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
