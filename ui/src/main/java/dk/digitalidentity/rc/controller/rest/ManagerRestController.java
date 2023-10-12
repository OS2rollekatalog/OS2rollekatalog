package dk.digitalidentity.rc.controller.rest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.validation.Valid;

import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
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
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.AttestationViewDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.AttestationView;
import dk.digitalidentity.rc.controller.rest.model.AutoCompleteResult;
import dk.digitalidentity.rc.controller.rest.model.ManagerSubstituteAssignmentDTO;
import dk.digitalidentity.rc.controller.rest.model.ValueData;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
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
import dk.digitalidentity.rc.service.ManagerSubstituteService;
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
	private ManagerSubstituteService managerSubstituteService;
	
	@Autowired
	private EmailTemplateService emailTemplateService;
	
	@Autowired
	private EmailQueueService emailQueueService;
	
	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private RoleCatalogueConfiguration roleCatalogueConfiguration;

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
	@PostMapping("/rest/manager/substitute/add")
	@ResponseBody
	public ResponseEntity<?> addSubstitute(@RequestBody ManagerSubstituteAssignmentDTO body) {
		if (roleCatalogueConfiguration.getSubstituteManagerAPI().isEnabled()) {
			return ResponseEntity.badRequest().build();
		}

		User manager = null;
		if (body.getManager() == null) {
			manager = userService.getByUserId(SecurityUtil.getUserId());
		}
		else {
			manager = userService.getByUuid(body.getManager().getUuid());
		}

		// we need a manager to add the substitute on, otherwise...
		if (manager == null) {
			return ResponseEntity.badRequest().build();
		}
		
		OrgUnit orgUnit = orgUnitService.getByUuid(body.getOrgUnit().getUuid());
		if (orgUnit == null) {
			return ResponseEntity.badRequest().build();
		}

		User substitute = userService.getByUuid(body.getSubstitute().getUuid());
		if (substitute == null) {
			return ResponseEntity.badRequest().build();
		}

		// substitute cannot be the same as manager
		if (Objects.equals(substitute.getUuid(), manager.getUuid())) {
			return ResponseEntity.badRequest().build();
		}

		// someone needs to be logged in for this to work
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		if (loggedInUser == null) {
			return ResponseEntity.badRequest().build();
		}

		// only admins can change on other users
		if (!SecurityUtil.hasRole(Constants.ROLE_ADMINISTRATOR) && !Objects.equals(loggedInUser.getUuid(), manager.getUuid())) {
			return ResponseEntity.badRequest().build();
		}

		// check if substitute already assigned to selected orgUnit
		if (manager.getManagerSubstitutes().stream().anyMatch(m -> m.getSubstitute().equals(substitute) && m.getOrgUnit().equals(orgUnit))) {
			return ResponseEntity.badRequest().body(substitute.getName() + " er allerede tildelt som stedfortræder i " + orgUnit.getName());
		}

		auditLogger.log(manager, EventType.ADMIN_ASSIGNED_MANAGER_SUBSTITUTE, substitute);
		
		ManagerSubstitute managerSubstituteMapping = new ManagerSubstitute();
		managerSubstituteMapping.setManager(manager);
		managerSubstituteMapping.setSubstitute(substitute);
		managerSubstituteMapping.setOrgUnit(orgUnit);
		managerSubstituteMapping.setAssignedBy(loggedInUser.getName() + " (" + loggedInUser.getUserId() + ")");
		managerSubstituteMapping.setAssignedTts(new Date());
		
		manager.getManagerSubstitutes().add(managerSubstituteMapping);

		userService.save(manager);

		String substituteEmail = substitute.getEmail();
		if (substituteEmail != null) {
			EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.SUBSTITUTE);

			if (template.isEnabled()) {
				String title = template.getTitle();
				title = title.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), substitute.getName());
				title = title.replace(EmailTemplatePlaceholder.MANAGER_PLACEHOLDER.getPlaceholder(), manager.getName());
				title = title.replace(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER.getPlaceholder(), orgUnit.getName());

				String message = template.getMessage();
				message = message.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), substitute.getName());
				message = message.replace(EmailTemplatePlaceholder.MANAGER_PLACEHOLDER.getPlaceholder(), manager.getName());
				message = message.replace(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER.getPlaceholder(), orgUnit.getName());
				emailQueueService.queueEmail(substituteEmail, title, message, template, null);
			}
			else {
				log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
			}
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAdministratorOrManagerRole
	@PostMapping("/rest/manager/substitute/remove")
	@ResponseBody
	public ResponseEntity<?> removeSubstitute(@RequestBody ManagerSubstituteAssignmentDTO body) {
		if (roleCatalogueConfiguration.getSubstituteManagerAPI().isEnabled()) {
			return ResponseEntity.badRequest().build();
		}

		if (body == null) {
			return ResponseEntity.badRequest().build();
		}

		managerSubstituteService.deleteById(body.getId());

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
