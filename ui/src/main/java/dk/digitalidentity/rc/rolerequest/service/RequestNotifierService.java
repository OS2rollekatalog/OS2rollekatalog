package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.dao.model.AuthorizationManager;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.rolerequest.dao.RoleRequestDao;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestNotifierService {
	private final EmailTemplateService emailTemplateService;
	private final EmailQueueService emailQueueService;
	private final SettingsService settingsService;
	private final RoleRequestDao roleRequestDao;

	@Transactional
	public void sendMailToRoleAssigner() {
		List<RequestApproveStatus> stati = new ArrayList<>();
		stati.add(RequestApproveStatus.REQUESTED);
		List<RoleRequest> requestApproves = roleRequestDao.findByStatusInAndEmailSent(stati, false);
		int count = requestApproves.size();

		if (count != 0) {
			if (StringUtils.hasLength(settingsService.getRequestApproveServicedeskEmail())) {
				EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS);
				if (template.isEnabled()) {
					String title = template.getTitle();
					title = title.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), "rolletildeler");
					title = title.replace(EmailTemplatePlaceholder.COUNT_PLACEHOLDER.getPlaceholder(), Integer.toString(count));
					String message = template.getMessage();
					message = message.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), "rolletildeler");
					message = message.replace(EmailTemplatePlaceholder.COUNT_PLACEHOLDER.getPlaceholder(), Integer.toString(count));
					emailQueueService.queueEmail(settingsService.getRequestApproveServicedeskEmail(), title, message, template, null, null);
					for (RoleRequest requestApprove : requestApproves) {
						requestApprove.setEmailSent(true);
						roleRequestDao.save(requestApprove);
					}
				} else {
					log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
				}
			}
		}
	}

	public boolean notifyRecieverOnRequestApproval(User reciever, RequestAction action, String roleName, boolean isITSystemManual) {
		if (reciever.getEmail() == null) {
			return false;
		}
		EmailTemplate template = action == RequestAction.REMOVE
			? emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_ROLE_REQUEST_REMOVAL_USER)
			: emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_ROLE_REQUEST_USER);
		if (isITSystemManual) {
			template = emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_MANUAL_ROLE_REQUEST_USER);
		}

		if (template.isEnabled()) {
			String title = template.getTitle();
			title = title.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), reciever.getName());
			title = title.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), roleName);
			String message = template.getMessage();
			message = message.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), reciever.getName());
			message = message.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), roleName);
			emailQueueService.queueEmail(reciever.getEmail(), title, message, template, null, null);
		} else {
			log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
			return false;
		}
		return true;

	}

	public boolean notifyManagerOnRequestapproval(RoleRequest request, boolean isITSystemManual, String roleName) {
		// notifying manager and authorizationManager
		OrgUnit orgUnit = request.getOrgUnit();
		EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_ROLE_REQUEST_MANAGER);
		if (isITSystemManual) {
			template = emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_MANUAL_ROLE_REQUEST_MANAGER);
		}
		return notify(request, orgUnit, template, roleName);
	}

	public void notifyManagerOnRejectedRequest(final RoleRequest request, final OrgUnit orgUnit, final String roleName) {
		final EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.REJECTED_ROLE_REQUEST_MANAGER);
		notify(request, orgUnit, template, roleName);
	}

	private boolean notify(RoleRequest request, OrgUnit orgUnit, EmailTemplate template, String roleName) {
		if (!template.isEnabled()) {
			return false;
		}
		if (orgUnit == null) {
			return false;
		}
		final String action = request.getRequestAction() == RequestAction.ADD ? "tildelt" : "fjernet";
		User manager = orgUnit.getManager();
		List<AuthorizationManager> authorizationManagers = orgUnit.getAuthorizationManagers();

		if (manager != null) {
			if (StringUtils.hasLength(manager.getEmail())) {
				String title = template.getTitle();
				title = title.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), manager.getName());
				title = title.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), roleName);
				title = title.replace(EmailTemplatePlaceholder.USER_PLACEHOLDER.getPlaceholder(), request.getReceiver().getName());
				title = title.replace(EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER.getPlaceholder(), request.getRequester().getName());
				title = title.replace(EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER.getPlaceholder(), action);
				String message = template.getMessage();
				message = message.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), manager.getName());
				message = message.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), roleName);
				message = message.replace(EmailTemplatePlaceholder.USER_PLACEHOLDER.getPlaceholder(), request.getReceiver().getName());
				message = message.replace(EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER.getPlaceholder(), request.getRequester().getName());
				message = message.replace(EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER.getPlaceholder(), action);
				emailQueueService.queueEmail(manager.getEmail(), title, message, template, null, null);
			}
		}

		if (authorizationManagers != null && !authorizationManagers.isEmpty()) {
			for (AuthorizationManager am : authorizationManagers) {
				User authorizationManager = am.getUser();
				if (authorizationManager == null || authorizationManager.isDeleted()) {
					continue;
				}

				if (StringUtils.hasLength(authorizationManager.getEmail())) {
					String title = template.getTitle();
					title = title.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), authorizationManager.getName());
					title = title.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), roleName);
					title = title.replace(EmailTemplatePlaceholder.USER_PLACEHOLDER.getPlaceholder(), request.getReceiver().getName());
					title = title.replace(EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER.getPlaceholder(), request.getRequester().getName());
					title = title.replace(EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER.getPlaceholder(), action);
					String message = template.getMessage();
					message = message.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), authorizationManager.getName());
					message = message.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), roleName);
					message = message.replace(EmailTemplatePlaceholder.USER_PLACEHOLDER.getPlaceholder(), request.getReceiver().getName());
					message = message.replace(EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER.getPlaceholder(), request.getRequester().getName());
					message = message.replace(EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER.getPlaceholder(), action);
					emailQueueService.queueEmail(authorizationManager.getEmail(), title, message, template, null, null);
				}
			}
		}

		return true;
	}

}
