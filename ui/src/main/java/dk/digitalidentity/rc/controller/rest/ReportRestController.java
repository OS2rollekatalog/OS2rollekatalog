package dk.digitalidentity.rc.controller.rest;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.microsoft.sqlserver.jdbc.StringUtils;

import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
import dk.digitalidentity.rc.dao.model.ReportTemplate;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.service.HistoryService;
import dk.digitalidentity.rc.service.ReportTemplateService;

@RequireReadAccessRole
@RestController
public class ReportRestController {

	@Autowired
	private HistoryService historyService;

	@Autowired
	private ReportTemplateService reportTemplateService;

	@PostMapping(value = "/rest/report/save-template")
	public ResponseEntity<HttpStatus> saveTemplate(@RequestBody ReportForm reportForm) {
		ReportTemplate reportTemplate = new ReportTemplate();
		reportTemplate.setName(reportForm.getName());
		reportTemplate.setShowUsers(reportForm.isShowUsers());
		reportTemplate.setShowTitles(reportForm.isShowTitles());
		reportTemplate.setShowOUs(reportForm.isShowOUs());
		reportTemplate.setShowUserRoles(reportForm.isShowUserRoles());
		reportTemplate.setShowKLE(reportForm.isShowKLE());
		reportTemplate.setShowItSystems(reportForm.isShowItSystems());
		reportTemplate.setShowInactiveUsers(reportForm.isShowInactiveUsers());
		
		if (reportForm.getItsystemFilter() != null && reportForm.getItsystemFilter().length > 0) {
			StringBuilder builder = new StringBuilder();

			for (Long id : reportForm.getItsystemFilter()) {
				if (builder.length() > 0) {
					builder.append(",");
				}

				builder.append(id);
			}

			reportTemplate.setItsystemFilter(builder.toString());
		}

		if (!StringUtils.isEmpty(reportForm.getManagerFilter())) {
			reportTemplate.setManagerFilter(reportForm.getManagerFilter());
		}

		if (reportForm.getUnitFilter() != null && reportForm.getUnitFilter().length > 0) {
			StringBuilder builder = new StringBuilder();
			
			for (String id : reportForm.getUnitFilter()) {
				if (builder.length() > 0) {
					builder.append(",");
				}
				
				builder.append(id);
			}

			reportTemplate.setUnitFilter(builder.toString());
		}

		reportTemplateService.saveTemplate(reportTemplate);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping(value = "/rest/report/delete-template/{id}")
	public ResponseEntity<HttpStatus> deleteTemplate(@PathVariable("id") Long id) {
		ReportTemplate reportTemplate = reportTemplateService.getById(id);
		if (reportTemplate != null) {
			reportTemplateService.deleteTemplate(reportTemplate);
		}
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@PostMapping(value = "/rest/report/getous/{date}")
	public List<OUListForm> getAllOus(@PathVariable("date") String dateStr) {
		LocalDate date = null;
		try {
			date = LocalDate.parse(dateStr);
		}
		catch (DateTimeException e) {
			return new ArrayList<>();
		}

		return parseOuTree(date);
	}
	
	private List<OUListForm> parseOuTree(LocalDate localDate) {
		return historyService
				.getOUs(localDate)
				.values()
				.stream()
				.map(OUListForm::new)
				.collect(Collectors.toList());
	}
}
