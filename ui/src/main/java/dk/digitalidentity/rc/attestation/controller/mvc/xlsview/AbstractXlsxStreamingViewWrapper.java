package dk.digitalidentity.rc.attestation.controller.mvc.xlsview;

import java.io.IOException;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.web.servlet.view.document.AbstractXlsxStreamingView;

import jakarta.servlet.http.HttpServletResponse;

// the super class does not dispose in case of errors, we handle this by catching exceptions and trying to dispose silently
public abstract class AbstractXlsxStreamingViewWrapper extends AbstractXlsxStreamingView {

	@Override
	protected void renderWorkbook(Workbook workbook, HttpServletResponse response) throws IOException {
		try {
			super.renderWorkbook(workbook, response);
		}
		catch (Exception ex) {
			try {
				((SXSSFWorkbook) workbook).dispose();
			}
			catch (Exception ignored) {
				;
			}
		}
	}
}
