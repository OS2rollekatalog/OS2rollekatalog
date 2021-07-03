package dk.digitalidentity.rc.controller.rest;

import java.util.ArrayList;
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
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.security.RequireAssignerOrManagerRole;
import dk.digitalidentity.rc.security.RequireManagerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.log4j.Log4j;

@Log4j
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

	@RequireManagerRole
	@PostMapping("/rest/manager/substitute/save")
	@ResponseBody
	public ResponseEntity<HttpStatus> saveSubstitute(@RequestBody String personUuid) {
		User manager = userService.getByUserId(SecurityUtil.getUserId());
		if (manager == null) {
			return ResponseEntity.badRequest().build();
		}

		// only for managers - not substitutes...
		if (!SecurityUtil.hasRole(Constants.ROLE_MANAGER)) {
			return ResponseEntity.badRequest().build();
		}

		User substitute = userService.getByUuid(personUuid);
		manager.setManagerSubstitute(substitute);

		userService.save(manager);
		
		//if the substitute is removed, it is null and will therefore cause a null pointer exception at line 170
		if (substitute == null) {
			return new ResponseEntity<>(HttpStatus.OK);
		}
		
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
				String message = template.getMessage();
				message = message.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, substitute.getName());
				message = message.replace(EmailTemplateService.MANAGER_PLACEHOLDER, manager.getName());
				message = message.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, builder.toString());
				emailQueueService.queueEmail(substituteEmail, template.getTitle(), message, template, null);
			} else {
				log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
			}
			
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
