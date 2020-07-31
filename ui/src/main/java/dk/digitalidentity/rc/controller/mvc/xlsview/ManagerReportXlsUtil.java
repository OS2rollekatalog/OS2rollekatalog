package dk.digitalidentity.rc.controller.mvc.xlsview;

import java.util.Map;

import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;

@Component
public class ManagerReportXlsUtil extends ReportXlsView {

	public void buildWorkbook(Map<String, Object> model, Workbook workbook) throws Exception {
		buildExcelDocument(model, workbook, null, null);
	}
}
