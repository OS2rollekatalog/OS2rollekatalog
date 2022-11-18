package dk.digitalidentity.rc.controller.rest;

import java.security.Principal;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.controller.rest.model.MultipleUserRequestDTO;
import dk.digitalidentity.rc.controller.rest.model.RejectForm;
import dk.digitalidentity.rc.dao.model.AuthorizationManager;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RequestApprove;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.security.RequireAssignerRole;
import dk.digitalidentity.rc.security.RequireRequesterRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RequestApproveService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.WhoCanRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class RequestApproveRestController {

	@Autowired
	private UserService userService;
	
	@Autowired
	private RequestApproveService requestApproveService;

	@Autowired
	private UserRoleService userRoleService;
	
	@Autowired
	private RoleGroupService roleGroupService;
	
	@Autowired
	private AuditLogger auditLogger;
	
	@Autowired
	private SettingsService settingsService;
	
	@Autowired
	private EmailTemplateService emailTemplateService;
	
	@Autowired
	private EmailQueueService emailQueueService;

	@Autowired
	private OrgUnitService orgUnitService;

	@RequireAssignerRole
	@PostMapping("/rest/requestapprove/requests/approve/{id}")
	public ResponseEntity<String> approveRequest(@PathVariable("id") long id, Principal principal) {
		if (!settingsService.isRequestApproveEnabled()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		User user = userService.getByUserId(principal.getName());
		if (user == null) {
			log.warn("Unable approve request for user: " + principal.getName());
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		RequestApprove request = requestApproveService.getById(id);
		if (request == null) {
			return new ResponseEntity<>("Anmodningen findes ikke", HttpStatus.BAD_REQUEST);
		}
		
		User requestedFor = request.getRequestedFor();
		if (requestedFor == null) {
			return new ResponseEntity<>("Der er ikke valgt en modtager af rollen", HttpStatus.BAD_REQUEST);
		}
		
		if (requestedFor.isDeleted() == true) {
			return new ResponseEntity<>("Der er valgt en inaktiv modtager af rollen", HttpStatus.BAD_REQUEST);
		}

		boolean manualItSystem = false;
		String roleName = "";
		switch (request.getRoleType()) {
			case USERROLE:
				UserRole userRole = userRoleService.getById(request.getRoleId());
				if (userRole != null) {
					userService.addUserRole(request.getRequestedFor(), userRole, null, null);
					roleName = userRole.getName();
					manualItSystem = userRole.getItSystem().getSystemType().equals(ItSystemType.MANUAL);
					
					auditLogger.log(request.getRequestedFor(), EventType.APPROVE_REQUEST, userRole);
				}
				break;
			case ROLEGROUP:
				RoleGroup roleGroup = roleGroupService.getById(request.getRoleId());
				if (roleGroup != null) {
					userService.addRoleGroup(request.getRequestedFor(), roleGroup, null, null);
					roleName = roleGroup.getName();
					
					for (RoleGroupUserRoleAssignment userRoleAssignment : roleGroup.getUserRoleAssignments()) {
						if (userRoleAssignment.getUserRole().getItSystem().getSystemType().equals(ItSystemType.MANUAL)) {
							manualItSystem = true;
							break;
						}
					}
					
					auditLogger.log(request.getRequestedFor(), EventType.APPROVE_REQUEST, roleGroup);
				}
				break;
			default:
				throw new RuntimeException("Unknown roletype: " + request.getRoleType());
		}
		
		request.setStatus(RequestApproveStatus.ASSIGNED);
		request = requestApproveService.save(request);
		
		if (requestedFor.getEmail() != null) {
			EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_ROLE_REQUEST_USER);
			if (manualItSystem) {
				template = emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_MANUAL_ROLE_REQUEST_USER);
			}
			
			if (template.isEnabled()) {
				String title = template.getTitle();
				title = title.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, requestedFor.getName());
				title = title.replace(EmailTemplateService.ROLE_PLACEHOLDER, roleName);
				String message = template.getMessage();
				message = message.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, requestedFor.getName());
				message = message.replace(EmailTemplateService.ROLE_PLACEHOLDER, roleName);
				emailQueueService.queueEmail(requestedFor.getEmail(), title, message, template, null);
			}
			else {
				log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
			}
		}
		
		//Notifying manager and authorizationManager
		OrgUnit orgUnit = request.getOrgUnit();
		EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_ROLE_REQUEST_MANAGER);
		if (manualItSystem) {
			template = emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_MANUAL_ROLE_REQUEST_MANAGER);
		}
		notify(request, orgUnit, template);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequireAssignerRole
	@PostMapping("/rest/requestapprove/requests/reject/{id}")
	public ResponseEntity<String> rejectRequest(@PathVariable("id") long id, RejectForm rejectForm, Principal principal) {
		if (!settingsService.isRequestApproveEnabled()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		User user = userService.getByUserId(principal.getName());
		if (user == null) {
			log.warn("Unable reject request for user: " + principal.getName());
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		RequestApprove request = requestApproveService.getById(id);
		if (request == null) {
			return new ResponseEntity<>("Anmodningen findes ikke", HttpStatus.BAD_REQUEST);
		}

		request.setStatus(RequestApproveStatus.REJECTED);
		request.setRejectReason(rejectForm.getReason());
		request = requestApproveService.save(request);
		
		
		switch(request.getRoleType()) {
			case USERROLE:
				RoleGroup roleGroup = roleGroupService.getById(request.getRoleId());
				if (roleGroup != null) {
					auditLogger.log(request.getRequestedFor(), EventType.REJECT_REQUEST, roleGroup);
				}
				break;
			case ROLEGROUP:
				UserRole userRole = userRoleService.getById(request.getRoleId());
				if (userRole != null) {
					auditLogger.log(request.getRequestedFor(), EventType.REJECT_REQUEST, userRole);
				}
				break;
			default:
				log.error("Unknown roleType:" + request.getRoleType());
				break;
		}
		
		// Notifying manager and authorizationManager
		OrgUnit orgUnit = request.getOrgUnit();
		EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.REJECTED_ROLE_REQUEST_MANAGER);
		notify(request, orgUnit, template);
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@ResponseBody
	@RequireRequesterRole
	@PostMapping("/rest/requestapprove/request/role")
	public ResponseEntity<String> request(@RequestBody MultipleUserRequestDTO request, HttpServletRequest httpRequest) {
		if (!settingsService.getRequestApproveWho().equals(WhoCanRequest.AUTHORIZATION_MANAGER) || !settingsService.isRequestApproveEnabled()) {
			return new ResponseEntity<>("Anmodningen ikke understøttet", HttpStatus.BAD_REQUEST);
		}

		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			log.warn("Unable make request for user: " + SecurityUtil.getUserId());
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		OrgUnit orgUnit = orgUnitService.getByUuid(request.getOrgUnitUuid());
		if (orgUnit == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		if (request.getRoleType().equals("roleGroup")) {
			RoleGroup roleGroup = roleGroupService.getById(request.getRoleId());
			if (roleGroup == null) {
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			}
			
			for (String uuid : request.getSelectedUsers()) {
				User selectedUser = userService.getByUuid(uuid);
				if (selectedUser != null) {
					requestApproveService.requestRoleGroup(roleGroup, user, request.getReason(), selectedUser, orgUnit);

					auditLogger.log(selectedUser, EventType.REQUEST_ROLE_FOR, roleGroup);
				}
			}
		}
		else if (request.getRoleType().equals("userRole")) {
			UserRole userRole = userRoleService.getById(request.getRoleId());
			if (userRole == null) {
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			}

			for (String uuid : request.getSelectedUsers()) {
				User selectedUser = userService.getByUuid(uuid);
				if (selectedUser != null) {
					requestApproveService.requestUserRole(userRole, user, request.getReason(), selectedUser, orgUnit);

					auditLogger.log(selectedUser, EventType.REQUEST_ROLE_FOR, userRole);
				}
			}
		}
		else {
			log.warn("Unknown role type: " + request.getRoleType());
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	private void notify(RequestApprove request, OrgUnit orgUnit, EmailTemplate template) {
		if (template.isEnabled()) {
			if (orgUnit != null) {
				String roleName = "";
				if (request.getRoleType().equals(EntityType.USERROLE)) {
					UserRole userRole = userRoleService.getById(request.getRoleId());
					if (userRole != null) {
						roleName= userRole.getName();
					}
				}
				else if (request.getRoleType().equals(EntityType.ROLEGROUP)) {
					RoleGroup roleGroup = roleGroupService.getById(request.getRoleId());
					if (roleGroup != null) {
						roleName= roleGroup.getName();
					}
				}
				
				User manager = orgUnit.getManager();
				List<AuthorizationManager> authorizationManagers = orgUnit.getAuthorizationManagers();

				if (manager != null) {
					if (StringUtils.hasLength(manager.getEmail())) {
						String title = template.getTitle();
						title = title.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, manager.getName());
						title = title.replace(EmailTemplateService.ROLE_PLACEHOLDER, roleName);
						title = title.replace(EmailTemplateService.USER_PLACEHOLDER, request.getRequestedFor().getName());
						String message = template.getMessage();
						message = message.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, manager.getName());
						message = message.replace(EmailTemplateService.ROLE_PLACEHOLDER, roleName);
						message = message.replace(EmailTemplateService.USER_PLACEHOLDER, request.getRequestedFor().getName());
						emailQueueService.queueEmail(manager.getEmail(), title, message, template, null);
					}
				}
				
				if (authorizationManagers != null && authorizationManagers.size() > 0) {
					for (AuthorizationManager am : authorizationManagers) {
						User authorizationManager = am.getUser();
						if (authorizationManager == null || authorizationManager.isDeleted() == true) {
							continue;
						}

						if (StringUtils.hasLength(authorizationManager.getEmail())) {
							String title = template.getTitle();
							title = title.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, authorizationManager.getName());
							title = title.replace(EmailTemplateService.ROLE_PLACEHOLDER, roleName);
							title = title.replace(EmailTemplateService.USER_PLACEHOLDER, request.getRequestedFor().getName());
							String message = template.getMessage();
							message = message.replace(EmailTemplateService.RECEIVER_PLACEHOLDER, authorizationManager.getName());
							message = message.replace(EmailTemplateService.ROLE_PLACEHOLDER, roleName);
							message = message.replace(EmailTemplateService.USER_PLACEHOLDER, request.getRequestedFor().getName());
							emailQueueService.queueEmail(authorizationManager.getEmail(), title, message, template, null);
						}
					}
				}
			}
		}
	}
}
