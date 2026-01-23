package dk.digitalidentity.rc.rolerequest.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.AuthorizationManager;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.rolerequest.dao.RoleRequestDao;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.UserWithRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestNotifierService {
	private final EmailTemplateService emailTemplateService;
	private final EmailQueueService emailQueueService;
	private final SettingsService settingsService;
	private final RoleRequestDao roleRequestDao;
	private final UserService userService;
	private final UserRoleService userRoleService;
	private final SystemRoleService systemRoleService;
	private final OrgUnitService orgUnitService;
	private final ItSystemService itSystemService;
	private final RequestAuthorizedRoleService requestAuthorizedRoleService;

	@Transactional
	public void sendMailToRoleAssignerOnce() {
		List<RoleRequest> allRequests = getRoleRequests(false);

		if (allRequests.isEmpty()) {
			return;
		}

		Map<Set<String>, List<RoleRequest>> emailsAndRequests = getEmailMap(allRequests);

		for (Map.Entry<Set<String>, List<RoleRequest>> entry : emailsAndRequests.entrySet()) {
			Set<String> approvers = entry.getKey();
			List<RoleRequest> requests = entry.getValue();

			if (!approvers.isEmpty()) {
				EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS);
				inputMessage(requests, requests.size(), approvers, template);
			}
		}
	}

	@Transactional
	public void sendWaitingRequestsMailToServiceDeskOnce() {
		String servicedeskEmail = settingsService.getRequestApproveServicedeskEmail();
		if (!StringUtils.hasLength(servicedeskEmail)) {
			return;
		}

		List<RoleRequest> requestApproves = roleRequestDao.findByStatusInAndEmailSentToServicedesk(
			List.of(RequestApproveStatus.REQUESTED), false);

		if (requestApproves.isEmpty()) {
			return;
		}

		EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_SERVICEDESK);
		if (!template.isEnabled()) {
			log.info("Email template with type {} is disabled. Email was not sent.", template.getTemplateType());
			return;
		}

		int count = requestApproves.size();
		String title = template.getTitle()
			.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), "rolletildeler")
			.replace(EmailTemplatePlaceholder.COUNT_PLACEHOLDER.getPlaceholder(), Integer.toString(count));
		String message = template.getMessage()
			.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), "rolletildeler")
			.replace(EmailTemplatePlaceholder.COUNT_PLACEHOLDER.getPlaceholder(), Integer.toString(count));

		emailQueueService.queueEmail(servicedeskEmail, title, message, template, null, null);

		requestApproves.forEach(request -> request.setEmailSentToServicedesk(true));
		roleRequestDao.saveAll(requestApproves);
	}

	@Transactional
	public void sendMailToRoleAssignerOncePerDay() {
		List<RoleRequest> allRequests = getRoleRequests(true);

		if (allRequests.isEmpty()) {
			return;
		}

		Map<Set<String>, List<RoleRequest>> requestsByApprovers = getEmailMap(allRequests);

		// Send one email per unique set of approvers
		for (Map.Entry<Set<String>, List<RoleRequest>> entry : requestsByApprovers.entrySet()) {
			Set<String> approvers = entry.getKey();
			List<RoleRequest> requests = entry.getValue();

			if (!approvers.isEmpty()) {
				EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS_DAILY);
				inputMessage(requests, requests.size(), approvers, template);
			}
		}
	}

	private Map<Set<String>, List<RoleRequest>> getEmailMap(List<RoleRequest> allRequests) {
		// Group requests by their approval path and send separate emails
		Map<Set<String>, List<RoleRequest>> requestsByApprovers = new HashMap<>();

		for (RoleRequest request : allRequests) {
			Set<String> approvers = getEmailsToSendTo(request);
			requestsByApprovers.computeIfAbsent(approvers, k -> new ArrayList<>()).add(request);
		}
		return requestsByApprovers;
	}

	public boolean notifyReceiverOnRequestApproval(User receiver, RequestAction action, String roleName, boolean isITSystemManual, LocalDate startDate, LocalDate endDate, String reason) {
		if (receiver.getEmail() == null) {
			return false;
		}
		EmailTemplate template = action == RequestAction.REMOVE
			? emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_ROLE_REQUEST_REMOVAL_USER)
			: emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_ROLE_REQUEST_USER);
		if (isITSystemManual) {
			template = emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_MANUAL_ROLE_REQUEST_USER);
		}

		if (template.isEnabled()) {
			DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
			String title = template.getTitle();
			title = title.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), receiver.getName());
			title = title.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), roleName);
			title = title.replace(EmailTemplatePlaceholder.REQUEST_REASON.getPlaceholder(), reason);
			String message = template.getMessage();
			message = message.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), receiver.getName());
			message = message.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), roleName);
			message = message.replace(EmailTemplatePlaceholder.START_DATE.getPlaceholder(),
				startDate != null ? startDate.format(dateFormatter) : LocalDate.now().format(dateFormatter));
			message = message.replace(EmailTemplatePlaceholder.STOP_DATE.getPlaceholder(),
				startDate != null ? endDate.format(dateFormatter) : "ubegrænset");
			message = message.replace(EmailTemplatePlaceholder.REQUEST_REASON.getPlaceholder(), reason);
			emailQueueService.queueEmail(receiver.getEmail(), title, message, template, null, null);
		} else {
			log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
			return false;
		}
		return true;

	}

	public boolean notifyManagerOnRequestapproval(RoleRequest request, boolean isITSystemManual, String roleName, String reason) {
		// notifying manager and authorizationManager
		OrgUnit orgUnit = request.getOrgUnit();
		EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_ROLE_REQUEST_MANAGER);
		if (isITSystemManual) {
			template = emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_MANUAL_ROLE_REQUEST_MANAGER);
		}
		return notify(request, orgUnit, template, roleName, reason);
	}

	public void notifyManagerOnRejectedRequest(final RoleRequest request, final OrgUnit orgUnit, final String roleName, String reason) {
		final EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.REJECTED_ROLE_REQUEST_MANAGER);
		notify(request, orgUnit, template, roleName, reason);
	}

	private boolean notify(RoleRequest request, OrgUnit orgUnit, EmailTemplate template, String roleName, String reason) {
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
				title = title.replace(EmailTemplatePlaceholder.REQUEST_REASON.getPlaceholder(), reason);
				String message = template.getMessage();
				message = message.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), manager.getName());
				message = message.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), roleName);
				message = message.replace(EmailTemplatePlaceholder.USER_PLACEHOLDER.getPlaceholder(), request.getReceiver().getName());
				message = message.replace(EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER.getPlaceholder(), request.getRequester().getName());
				message = message.replace(EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER.getPlaceholder(), action);
				message = message.replace(EmailTemplatePlaceholder.REQUEST_REASON.getPlaceholder(), reason);
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

	private Set<String> getEmailsToSendTo(RoleRequest request) {
		Map<ApprovableBy, String> roleRequestApproverEmails = settingsService.getRoleRequestApproverEmails();
		Set<String> mailsToSendTo = new HashSet<>();

		for (ApprovableBy approvableBy : request.getApproverOption()) {
			// First check if there's a global email for THIS specific approver option
			String emailBySetting = roleRequestApproverEmails.get(approvableBy);
			if (StringUtils.hasLength(emailBySetting)) {
				mailsToSendTo.add(emailBySetting);
				continue;
			}

			// No global email for this approver option, find specific users
			switch (approvableBy) {
				case AUTOMATIC, INHERIT -> {
					// Here, they are either automatically getting approved or the Servicedesk gets an email
					log.error("Automatic or inherited approval requests should not be present, request id:{}", request.getId());
				}
				case AUTHRESPONSIBLE -> {
					OrgUnit orgUnit = request.getOrgUnit();
					if (orgUnit != null && orgUnit.getAuthorizationManagers() != null && !orgUnit.getAuthorizationManagers().isEmpty()) {
						for (AuthorizationManager manager : orgUnit.getAuthorizationManagers()) {
							User authorizationManager = manager.getUser();
							if (authorizationManager != null && authorizationManager.getEmail() != null) {
								mailsToSendTo.add(authorizationManager.getEmail());
							}
						}
					}
				}
				case MANAGERORSUBSTITUTE -> {
					OrgUnit orgUnit = request.getOrgUnit();
					if (orgUnit != null) {
						mailsToSendTo.addAll(orgUnitService.getManagerAndSubstituteEmail(orgUnit, false).keySet());
					}
				}
				case AUTHORIZED -> {
					Set<String> authorizedEmails = getAuthorizedEmails(request);
					mailsToSendTo.addAll(authorizedEmails);
				}
				case ADMINISTRATOR -> mailsToSendTo.addAll(getAdminEmails());
				case SYSTEMRESPONSIBLE -> {
					if (request.getUserRole() != null && request.getUserRole().getItSystem() != null) {
						ItSystem itSystem = request.getUserRole().getItSystem();
						String itSystemEmail = itSystem.getSystemOwner() != null ? itSystem.getSystemOwner().getEmail() : null;
						if (itSystemEmail != null) {
							mailsToSendTo.add(itSystemEmail);
						}
					}
				}
			}
		}
		if (mailsToSendTo.size() > 10) {
			log.warn("Size of list of emails to send request approve email to has exceeded the 10 limit: {}", mailsToSendTo.size());
			return mailsToSendTo.stream().limit(10).collect(Collectors.toSet());
		}
		// Step 4: Fallback to global servicedesk email since no one else seems to be able to approve of this request
		if (mailsToSendTo.isEmpty()) {
			String serviceDeskEmail = settingsService.getRequestApproveServicedeskEmail();
			if (StringUtils.hasLength(serviceDeskEmail)) {
				mailsToSendTo.add(serviceDeskEmail);
			}
		}
		return mailsToSendTo;
	}

	private Set<String> getAuthorizedEmails(RoleRequest requestApprove) {
		ItSystem roleCatalogue = itSystemService.getFirstByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
		SystemRole requestAuthorizedRole = systemRoleService.getFirstByIdentifierAndItSystemId(Constants.ROLE_REQUESTAUTHORIZED, roleCatalogue.getId());
		Set<UserRole> allAuthorizedUserRoles = new HashSet<>(userRoleService.findAllBySystemRole(requestAuthorizedRole));
		return allAuthorizedUserRoles.stream()
			.flatMap(ur -> userService.getUsersWithUserRole(ur, true).stream())
			.map(UserWithRole::getUser)
			.filter(user -> isPermittedAccessToOuAndItSystem(requestApprove, user))
			.map(User::getEmail)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
	}

	private boolean isPermittedAccessToOuAndItSystem(RoleRequest requestApprove, User user) {
		final RequestAuthorizedRoleService.LimitedToItSystems accessibleItSystem = requestAuthorizedRoleService.accessibleItsSystems(user);
		final RequestAuthorizedRoleService.LimitedToOrgUnits accessibleOUs = requestAuthorizedRoleService.accessibleOrgUnits(user);
		final boolean itSystemAccessAllowed = accessibleItSystem.type() == RequestAuthorizedRoleService.LimitedToType.ALL
			|| (requestApprove.getUserRole() != null && accessibleItSystem.itSystems().contains(requestApprove.getUserRole().getItSystem().getId()));
		final boolean ouAccessAllowed = accessibleOUs.type() == RequestAuthorizedRoleService.LimitedToType.ALL
			|| accessibleOUs.orgUnits().contains(requestApprove.getOrgUnit().getUuid());
		return itSystemAccessAllowed && ouAccessAllowed;
	}

	private Set<String> getAdminEmails() {
		ItSystem roleCatalogue = itSystemService.getFirstByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
		SystemRole adminRole = systemRoleService.getFirstByIdentifierAndItSystemId(Constants.ROLE_ADMINISTRATOR_ID, roleCatalogue.getId());
		Set<UserRole> allUserRolesWithAdministrator = new HashSet<>(userRoleService.findAllBySystemRole(adminRole));
		return allUserRolesWithAdministrator.stream()
			.flatMap(ur -> userService.getUsersWithUserRole(ur, true).stream())
			.map(UserWithRole::getUser)
			.map(User::getEmail)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
	}

	private void inputMessage(List<RoleRequest> requestApproves, int count, Set<String> mailsToSendTo, EmailTemplate template) {
		if (template.isEnabled()) {
			String title = template.getTitle();
			title = title.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), "rolletildeler");
			title = title.replace(EmailTemplatePlaceholder.COUNT_PLACEHOLDER.getPlaceholder(), Integer.toString(count));
			String message = template.getMessage();
			message = message.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), "rolletildeler");
			message = message.replace(EmailTemplatePlaceholder.COUNT_PLACEHOLDER.getPlaceholder(), Integer.toString(count));
			for (String recipient : mailsToSendTo) {
				emailQueueService.queueEmail(recipient, title, message, template, null, null);
			}
			for (RoleRequest requestApprove : requestApproves) {
				requestApprove.setEmailSent(true);
				roleRequestDao.save(requestApprove);
			}
		} else {
			log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
		}
	}

	private List<RoleRequest> getRoleRequests(boolean isEmailSent) {
		if (!isEmailSent) {
			return roleRequestDao.findByStatusInAndEmailSent(List.of(RequestApproveStatus.REQUESTED), false);
		}
		return roleRequestDao.findByStatus(RequestApproveStatus.REQUESTED).stream().toList();
	}
}
