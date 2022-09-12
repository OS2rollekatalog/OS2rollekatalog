package dk.digitalidentity.rc.controller.rest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.AttestationViewDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.AttestationView;
import dk.digitalidentity.rc.controller.rest.model.AutoCompleteResult;
import dk.digitalidentity.rc.controller.rest.model.ValueData;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.security.RequireAdministratorOrManagerRole;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.security.RequireAssignerOrManagerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequireAssignerOrManagerRole
public class ManagerRestController {

	@Autowired
	private UserService userService;
	
	@Autowired
	private AttestationViewDao attestationViewDao;
	
	@Autowired
	private EmailTemplateService emailTemplateService;
	
	@Autowired
	private EmailQueueService emailQueueService;
	
	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired AuditLogger auditLogger;

	@RequireAdministratorRole
	@PostMapping("/rest/manager/attestation/list")
	public DataTablesOutput<AttestationView> list(@Valid @RequestBody DataTablesInput input, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			DataTablesOutput<AttestationView> error = new DataTablesOutput<>();
			error.setError(bindingResult.toString());

			return error;
		}

		return attestationViewDao.findAll(input);
	}
	
	@GetMapping("/rest/manager/substitute/search/person")
	@ResponseBody
	public ResponseEntity<?> searchPerson(@RequestParam("query") String term) {
		List<User> users = userService.findTop10ByName(term);

		List<ValueData> suggestions = new ArrayList<>();
		for (User user : users) {
			StringBuilder builder = new StringBuilder();
			builder.append(user.getName());
			builder.append(" (");
			builder.append(user.getUserId());
			builder.append(")");

			ValueData vd = new ValueData();
			vd.setValue(builder.toString());
			vd.setData(user.getUuid());
			
			suggestions.add(vd);
		}

		AutoCompleteResult result = new AutoCompleteResult();
		result.setSuggestions(suggestions);

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@RequireAdministratorOrManagerRole
	@PostMapping("/rest/manager/substitute/save")
	@ResponseBody
	public ResponseEntity<HttpStatus> saveSubstitute(@RequestBody String personUuid, @RequestParam(required = false) String managerUuid) {
		User manager = null;
		if (managerUuid == null) {
			manager = userService.getByUserId(SecurityUtil.getUserId());
		}
		else {
			manager = userService.getByUuid(managerUuid);
		}

		// we need a manager to set the substitute on, otherwise...
		if (manager == null) {
			return ResponseEntity.badRequest().build();
		}

		// someone needs to be logged in for this to work
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (loggedInUser == null) {
			return ResponseEntity.badRequest().build();
		}

		// only for managers and admins - not substitutes...
		if (!SecurityUtil.hasRole(Constants.ROLE_MANAGER) && !SecurityUtil.hasRole(Constants.ROLE_ADMINISTRATOR)) {
			return ResponseEntity.badRequest().build();
		}

		User substitute = userService.getByUuid(personUuid); // null is clearing ;)
		if (substitute != null && substitute.isDeleted()) {
			substitute = null; // do not pick inactive substitutes
		}

		auditLogger.log(manager, EventType.ADMIN_ASSIGNED_MANAGER_SUBSTITUTE, substitute);			

		manager.setSubstituteAssignedBy((substitute != null) ? (loggedInUser.getName() + " (" + loggedInUser.getUserId() + ")") : null);
		manager.setManagerSubstitute(substitute);
		manager.setSubstituteAssignedTts(new Date());

		userService.save(manager);

		// if a substitute was removed, we do not need to send an email
		if (substitute == null) {
			return new ResponseEntity<>(HttpStatus.OK);
		}
		
		// inform the substitute by email
		List<OrgUnit> orgUnits = orgUnitService.getByManagerMatchingUser(manager);
		StringBuilder builder = new StringBuilder();
		if (orgUnits != null && orgUnits.size() > 0) {
			for (OrgUnit orgUnit : orgUnits) {
				if (builder.length() > 0) {
					builder.append(", ");
				}
				
				builder.append(orgUnit.getName());
			}
		}
		
		String substituteEmail = substitute.getEmail();
		if (substituteEmail != null) {
			EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.SUBSTITUTE);

			if (template.isEnabled()) {
				String title = template.getTitle();
				title = title.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, substitute.getName());
				title = title.replace(EmailTemplateService.MANAGER_PLACEHOLDER, manager.getName());
				title = title.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, builder.toString());

				String message = template.getMessage();
				message = message.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, substitute.getName());
				message = message.replace(EmailTemplateService.MANAGER_PLACEHOLDER, manager.getName());
				message = message.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, builder.toString());
				emailQueueService.queueEmail(substituteEmail, title, message, template, null);
			}
			else {
				log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
			}
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
