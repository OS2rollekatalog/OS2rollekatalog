package dk.digitalidentity.rc.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
import dk.digitalidentity.rc.dao.model.ReportTemplate;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.service.ReportTemplateService;

@RequireReadAccessRole
@RestController
public class ReportRestController {

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
}
