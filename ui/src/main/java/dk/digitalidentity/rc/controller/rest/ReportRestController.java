package dk.digitalidentity.rc.controller.rest;

import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
import dk.digitalidentity.rc.controller.rest.model.UserListDTO;
import dk.digitalidentity.rc.dao.model.ReportTemplate;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.RequireReportAccessRole;
import dk.digitalidentity.rc.service.HistoryService;
import dk.digitalidentity.rc.service.ReportTemplateService;
import dk.digitalidentity.rc.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@RequireReportAccessRole
@RestController
public class ReportRestController {

	@Autowired
	private HistoryService historyService;

	@Autowired
	private ReportTemplateService reportTemplateService;

	@Autowired
	private MessageSource messageSource;

	@Autowired
	private UserService userService;

	@Autowired
	private AccessConstraintService accessConstraintService;

	@PostMapping(value = "/rest/report/save-template")
	public ResponseEntity<HttpStatus> saveTemplate(@RequestBody ReportForm reportForm) {
		ReportTemplate reportTemplate = new ReportTemplate();
		reportTemplate.setName(reportForm.getName());
		reportTemplate.setShowUsers(reportForm.isShowUsers());
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

		if (StringUtils.hasLength(reportForm.getManagerFilter())) {
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

	@GetMapping(value = "/rest/report/assign/users/{id}")
	public ResponseEntity<?> getAllUsers(Locale locale, @PathVariable("id") Long id) {
		String in = messageSource.getMessage("html.word.in", null, locale);

		List<User> users = userService.getAllThin();
		users = accessConstraintService.filterUsersUserCanAccess(users, false);

		List<UserListDTO> userDTOs = users
				.stream()
				.map(u -> new UserListDTO(u, in))
				.collect(Collectors.toList());
		
		ReportTemplate reportTemplate = reportTemplateService.getById(id);
		if (reportTemplate == null) {
			return ResponseEntity.badRequest().body("Ukendt skabelon");
		}
		List<User> assignedUsers = reportTemplate.getUsers();

		//Set assigned = true on all userDTO that are also in assignedUsers
		userDTOs.stream().filter(dto -> assignedUsers.stream().anyMatch(au -> Objects.equals(au.getUuid(), dto.getUuid()))).forEach((a) -> { a.setAssigned(true); });

		return new ResponseEntity<List<UserListDTO>>(userDTOs, HttpStatus.OK);
	}

	@PostMapping(value = "/rest/report/assign/toggleUser")
	public ResponseEntity<?> assignUser(String uuid, Long templateId) {
		ReportTemplate reportTemplate = reportTemplateService.getById(templateId);
		if (reportTemplate == null) {
			return ResponseEntity.badRequest().body("Ukendt skabelon");
		}

		User user = userService.getByUuid(uuid);
		if (user == null) {
			return ResponseEntity.badRequest().body("Ukendt bruger");
		}

		if (reportTemplate.getUsers().contains(user)) {
			reportTemplate.getUsers().remove(user);
		}
		else {
			reportTemplate.getUsers().add(user);
		}

		reportTemplateService.saveTemplate(reportTemplate);

		return ResponseEntity.ok("");
	}
}
