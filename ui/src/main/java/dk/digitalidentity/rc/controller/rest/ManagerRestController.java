package dk.digitalidentity.rc.controller.rest;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.AttestationViewDao;
import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.AttestationView;
import dk.digitalidentity.rc.controller.rest.model.AutoCompleteResult;
import dk.digitalidentity.rc.controller.rest.model.RejectForm;
import dk.digitalidentity.rc.controller.rest.model.ValueData;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.RequestApprove;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.security.RequireAssignerOrManagerRole;
import dk.digitalidentity.rc.security.RequireManagerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.EmailService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.RequestApproveService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.RequestApproveWrapper;
import lombok.extern.log4j.Log4j;

@Log4j
@RestController
@RequireAssignerOrManagerRole
public class ManagerRestController {

	@Autowired
	private UserService userService;
	
	@Autowired
	private RequestApproveService requestApproveService;

	@Autowired
	private MessageSource messageSource;

	@Autowired
	private EmailService emailService;
	
	@Autowired
	private UserRoleService userRoleService;
	
	@Autowired
	private RoleGroupService roleGroupService;
	
	@Autowired
	private AuditLogger auditLogger;

	@Autowired
	private AttestationViewDao attestationViewDao;
	
	@Autowired
	private EmailTemplateService emailTemplateService;
	
	@Autowired
	private EmailQueueService emailQueueService;

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
	
	@RequireManagerRole
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
		
		String substituteEmail = substitute.getEmail();
		if (substituteEmail != null) {
			EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.SUBSTITUTE);
			String message = template.getMessage();
			message = message.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, substitute.getName());
			message = message.replace(EmailTemplateService.ORGUNIT_PLACEHOLDER, "en eller flere enheder");
			emailQueueService.queueEmail(substituteEmail, template.getTitle(), message, template, null);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping(value = { "/rest/manager/requests/approve/{id}" })
	public ResponseEntity<String> approveRequest(@PathVariable("id") long id, Principal principal, Locale locale) {
		User user = userService.getByUserId(principal.getName());
		if (user == null) {
			log.warn("Unable approve request for user: " + principal.getName());
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		RequestApprove request = getById(id);
		if (request == null) {
			return new ResponseEntity<>("Anmodningen findes ikke", HttpStatus.BAD_REQUEST);
		}

		// as getById() above already did security checking (constrained access), we just need to check for roles to see if
		// we can assign directly, or just approve the request
		boolean canAssign = SecurityUtil.hasRole(Constants.ROLE_ADMINISTRATOR) || SecurityUtil.hasRole(Constants.ROLE_ASSIGNER); 

		if (canAssign) {
			switch (request.getRoleType()) {
				case USERROLE:
					UserRole userRole = userRoleService.getById(request.getRoleId());
					if (userRole != null) {
						userService.addUserRole(request.getRequester(), userRole, null, null);
					}
					break;
				case ROLEGROUP:
					RoleGroup roleGroup = roleGroupService.getById(request.getRoleId());
					if (roleGroup != null) {
						userService.addRoleGroup(request.getRequester(), roleGroup, null, null);
					}
					break;
				default:
					throw new RuntimeException("Unknown roletype: " + request.getRoleType());
			}
			
			request.setStatus(RequestApproveStatus.ASSIGNED);
		}
		else {
			auditLogger.log(user, EventType.APPROVE_REQUEST, request);
			
			request.setStatus(RequestApproveStatus.MANAGER_APPROVED);
		}

		request = requestApproveService.save(request);

		notifyUser(request.getRequester(), locale, request.getStatus());

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@PostMapping(value = { "/rest/manager/requests/reject/{id}" })
	public ResponseEntity<String> rejectRequest(@PathVariable("id") long id, RejectForm rejectForm, Principal principal, Locale locale) {
		User user = userService.getByUserId(principal.getName());
		if (user == null) {
			log.warn("Unable reject request for user: " + principal.getName());
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		RequestApprove request = getById(id);
		if (request == null) {
			return new ResponseEntity<>("Anmodningen findes ikke", HttpStatus.BAD_REQUEST);
		}

		request.setStatus(RequestApproveStatus.REJECTED);
		request.setRejectReason(rejectForm.getReason());
		request = requestApproveService.save(request);

		auditLogger.log(user, EventType.REJECT_REQUEST, request);
		
		notifyUser(request.getRequester(), locale, request.getStatus());
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	private void notifyUser(User user, Locale locale, RequestApproveStatus status) {
		if (user.getEmail() == null || user.getEmail().length() == 0) {
			return;
		}
		
		String subject = "";
		switch (status) {
			case MANAGER_APPROVED:
				subject = messageSource.getMessage("email.request.notify.subject.approved", null, locale);
				break;
			case REJECTED:
				subject = messageSource.getMessage("email.request.notify.subject.rejeced", null, locale);
				break;
			case ASSIGNED:
				subject = messageSource.getMessage("email.request.notify.subject.assigned", null, locale);
				break;
			case MANAGER_NOTIFIED:
			case REQUESTED:
				throw new RuntimeException("illegal status: " + status.toString());
		}

		String message = messageSource.getMessage("email.request.notify.message", null, locale);

		emailService.sendMessage(user.getEmail(), subject, message);
	}
	
	// getPendingRequests() performs security check, so we use this wrapper method to lookup RequestApprove by id
	private RequestApprove getById(long id) {
		List<RequestApproveWrapper> pendingRequests = requestApproveService.getPendingRequests();
		for (RequestApproveWrapper pendingRequest : pendingRequests) {
			if (pendingRequest.getRequest().getId() == id) {
				return pendingRequest.getRequest();
			}
		}
		
		return null;
	}
}
