package dk.digitalidentity.rc.controller.rest;

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
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.security.RequireAssignerRole;
import dk.digitalidentity.rc.security.RequireRequesterRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RequestApproveService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Objects;

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

	@Autowired
	private ItSystemService itSystemService;

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
					if (Objects.equals(request.getRequestAction(), RequestAction.REMOVE)) {
						userService.removeUserRole(request.getRequestedFor(), userRole);
						auditLogger.log(request.getRequestedFor(), EventType.APPROVE_REQUEST, userRole);
					} else {
						userService.addUserRoleReqApprove(request.getRequestedFor(), userRole, request.getRequestApprovePostponedConstraints(), request.getOrgUnit());
						roleName = userRole.getName();
						manualItSystem = userRole.getItSystem().getSystemType().equals(ItSystemType.MANUAL);
						
						auditLogger.log(request.getRequestedFor(), EventType.APPROVE_REQUEST, userRole);
					}
				}
				break;
			case ROLEGROUP:
				RoleGroup roleGroup = roleGroupService.getById(request.getRoleId());
				if (roleGroup != null) {
					if (Objects.equals(request.getRequestAction(), RequestAction.REMOVE)) {
						userService.removeRoleGroup(request.getRequestedFor(), roleGroup);
						auditLogger.log(request.getRequestedFor(), EventType.APPROVE_REQUEST, roleGroup);
					} else {
						userService.addRoleGroup(request.getRequestedFor(), roleGroup, null, null, request.getOrgUnit());
						auditLogger.log(request.getRequestedFor(), EventType.APPROVE_REQUEST, roleGroup);
					}
					roleName = roleGroup.getName();
					
					for (RoleGroupUserRoleAssignment userRoleAssignment : roleGroup.getUserRoleAssignments()) {
						if (userRoleAssignment.getUserRole().getItSystem().getSystemType().equals(ItSystemType.MANUAL)) {
							manualItSystem = true;
							break;
						}
					}
				}
				break;
			default:
				throw new RuntimeException("Unknown roletype: " + request.getRoleType());
		}
		
		request.setStatus(RequestApproveStatus.ASSIGNED);
		request = requestApproveService.save(request);

		if (requestedFor.getEmail() != null) {
			EmailTemplate template = request.getRequestAction() == RequestAction.REMOVE
					? emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_ROLE_REQUEST_REMOVAL_USER)
					: emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_ROLE_REQUEST_USER);
			if (manualItSystem) {
				template = emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_MANUAL_ROLE_REQUEST_USER);
			}
			
			if (template.isEnabled()) {
				String title = template.getTitle();
				title = title.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), requestedFor.getName());
				title = title.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), roleName);
				String message = template.getMessage();
				message = message.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), requestedFor.getName());
				message = message.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), roleName);
				emailQueueService.queueEmail(requestedFor.getEmail(), title, message, template, null, null);
			}
			else {
				log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
			}
		}

		// notifying manager and authorizationManager
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
			case ROLEGROUP:
				RoleGroup roleGroup = roleGroupService.getById(request.getRoleId());
				if (roleGroup != null) {
					auditLogger.log(request.getRequestedFor(), EventType.REJECT_REQUEST, roleGroup);
				}
				break;
			case USERROLE:
				UserRole userRole = userRoleService.getById(request.getRoleId());
				if (userRole != null) {
					auditLogger.log(request.getRequestedFor(), EventType.REJECT_REQUEST, userRole);
				}
				break;
			default:
				log.error("Unknown roleType:" + request.getRoleType());
				break;
		}
		
		// notifying manager and authorizationManager
		OrgUnit orgUnit = request.getOrgUnit();
		EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.REJECTED_ROLE_REQUEST_MANAGER);
		notify(request, orgUnit, template);
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@PostMapping("/rest/requestapprove/requests/flipAssign/{id}")
	public ResponseEntity<?> Assign(@PathVariable long id, @RequestHeader("confirm") boolean confirm){
		RequestApprove request = requestApproveService.getById(id);
		if (request == null) {
			return ResponseEntity.notFound().build();
		}

		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return ResponseEntity.badRequest().build();
		}

		if (StringUtils.hasLength(request.getAdminUuid())) {
			if (!confirm || request.getAdminUuid().equals(user.getUuid())) {
				request.setAdminName(null);
				request.setAdminUuid(null);
			}
			else {
				request.setAdminUuid(user.getUuid());
				request.setAdminName(user.getName());
			}
		}
		else {
			request.setAdminUuid(user.getUuid());
			request.setAdminName(user.getName());
		}
		
		requestApproveService.save(request);

		return ResponseEntity.ok("");
	}
	
	@ResponseBody
	@RequireRequesterRole
	@PostMapping("/rest/requestapprove/request/role")
	public ResponseEntity<String> request(@RequestBody MultipleUserRequestDTO request, HttpServletRequest httpRequest) {
		if (!settingsService.isRequestApproveEnabled()) {
			return new ResponseEntity<>("Anmodningen ikke understøttet", HttpStatus.BAD_REQUEST);
		}

		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			log.warn("Unable make request for user: " + SecurityUtil.getUserId());
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		OrgUnit orgUnit = orgUnitService.getByUuid(request.getOrgUnitUuid());
		if (orgUnit == null) {
			return new ResponseEntity<>("Enheden blev ikke fundet", HttpStatus.BAD_REQUEST);
		}
		
		if (request.getRoleType().equals("roleGroup")) {
			RoleGroup roleGroup = roleGroupService.getById(request.getRoleId());
			if (roleGroup == null || !roleGroup.isCanRequest()) {
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			}

			for (RoleGroupUserRoleAssignment assignment : roleGroup.getUserRoleAssignments()) {
				if (assignment.getUserRole().getItSystem().isOuFilterEnabled()) {
					List<String> uuids = itSystemService.getOUFilterUuidsWithChildren(assignment.getUserRole().getItSystem());
					if (!uuids.contains(orgUnit.getUuid())) {
						return new ResponseEntity<>("Kan ikke godkende anmodningen da it-systemet er omfattet af enhedsfilter", HttpStatus.BAD_REQUEST);
					}
				}
			}

			for (String uuid : request.getSelectedUsers()) {
				User selectedUser = userService.getByUuid(uuid);
				if (selectedUser != null) {
					if(request.getAction().equals(RequestAction.REMOVE)) {
						requestApproveService.requestRoleGroupRemoval(roleGroup, user, request.getReason(), selectedUser, orgUnit);
						auditLogger.log(selectedUser, EventType.REQUEST_ROLE_REMOVAL_FOR, roleGroup);
					} else {
						requestApproveService.requestRoleGroup(roleGroup, user, request.getReason(), selectedUser, orgUnit);
						auditLogger.log(selectedUser, EventType.REQUEST_ROLE_FOR, roleGroup);
					}

				}
			}
		}
		else if (request.getRoleType().equals("userRole")) {
			UserRole userRole = userRoleService.getById(request.getRoleId());
			var roles = userRoleService.getAll();
			if (userRole == null || !userRole.isCanRequest()) {
				return new ResponseEntity<>("Der kan ikke anmodes om den givne rolle", HttpStatus.BAD_REQUEST);
			}

			if (userRole.getItSystem().isOuFilterEnabled()) {
				List<String> uuids = itSystemService.getOUFilterUuidsWithChildren(userRole.getItSystem());
				if (!uuids.contains(orgUnit.getUuid())) {
					return new ResponseEntity<>("Kan ikke godkende anmodningen da it-systemet er omfattet af enhedsfilter", HttpStatus.BAD_REQUEST);
				}
			}

			for (String uuid : request.getSelectedUsers()) {
				User selectedUser = userService.getByUuid(uuid);
				if (selectedUser != null) {
					if (Objects.equals(RequestAction.REMOVE, request.getAction())) {
						requestApproveService.requestUserRoleRemoval(userRole, user, request.getReason(), selectedUser, orgUnit);
						auditLogger.log(selectedUser, EventType.REQUEST_ROLE_REMOVAL_FOR, userRole);
					}
					else {
						requestApproveService.requestUserRole(userRole, user, request.getReason(), selectedUser, orgUnit, request.getConstraints());
						auditLogger.log(selectedUser, EventType.REQUEST_ROLE_FOR, userRole);
					}
				}
			}
		}
		else {
			log.warn("Unknown role type: " + request.getRoleType());
			return new ResponseEntity<>("Ukendt rolletype", HttpStatus.BAD_REQUEST);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	private void notify(RequestApprove request, OrgUnit orgUnit, EmailTemplate template) {
		if (template.isEnabled()) {
			final String action = request.getRequestAction() == RequestAction.ADD ? "tildelt" : "fjernet";
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
						title = title.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), manager.getName());
						title = title.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), roleName);
						title = title.replace(EmailTemplatePlaceholder.USER_PLACEHOLDER.getPlaceholder(), request.getRequestedFor().getName());
						title = title.replace(EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER.getPlaceholder(), request.getRequester().getName());
						title = title.replace(EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER.getPlaceholder(), action);
						String message = template.getMessage();
						message = message.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), manager.getName());
						message = message.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), roleName);
						message = message.replace(EmailTemplatePlaceholder.USER_PLACEHOLDER.getPlaceholder(), request.getRequestedFor().getName());
						message = message.replace(EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER.getPlaceholder(), request.getRequester().getName());
						message = message.replace(EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER.getPlaceholder(), action);
						emailQueueService.queueEmail(manager.getEmail(), title, message, template, null, null);
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
							title = title.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), authorizationManager.getName());
							title = title.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), roleName);
							title = title.replace(EmailTemplatePlaceholder.USER_PLACEHOLDER.getPlaceholder(), request.getRequestedFor().getName());
							title = title.replace(EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER.getPlaceholder(), request.getRequester().getName());
							title = title.replace(EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER.getPlaceholder(), action);
							String message = template.getMessage();
							message = message.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), authorizationManager.getName());
							message = message.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), roleName);
							message = message.replace(EmailTemplatePlaceholder.USER_PLACEHOLDER.getPlaceholder(), request.getRequestedFor().getName());
							message = message.replace(EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER.getPlaceholder(), request.getRequester().getName());
							message = message.replace(EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER.getPlaceholder(), action);
							emailQueueService.queueEmail(authorizationManager.getEmail(), title, message, template, null, null);
						}
					}
				}
			}
		}
	}
}
