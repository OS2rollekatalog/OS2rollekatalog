package dk.digitalidentity.rc.rolerequest.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
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
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RequestNotifierService {
	private final EmailTemplateService emailTemplateService;
	private final EmailQueueService emailQueueService;
	private final SettingsService settingsService;
	private final RoleRequestDao roleRequestDao;
	private final UserRoleService userRoleService;
	private final SystemRoleService systemRoleService;
	private final OrgUnitService orgUnitService;
	private final ItSystemService itSystemService;
	private final RequestAuthorizedRoleService requestAuthorizedRoleService;
	private final AssignmentService assignmentService;
	private final RequestApproverResolver requestApproverResolver;

	@Transactional
	public void sendMailToRoleAssignerOnce() {
		List<RoleRequest> allRequests = getRoleRequests(false);

		if (allRequests.isEmpty()) {
			return;
		}

		Map<Map<String, String>, List<RoleRequest>> emailsAndRequests = getEmailMap(allRequests);

		for (Map.Entry<Map<String, String>, List<RoleRequest>> entry : emailsAndRequests.entrySet()) {
			Map<String, String> approvers = entry.getKey();
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

		Map<Map<String, String>, List<RoleRequest>> requestsByApprovers = getEmailMap(allRequests);

		// Send one email per unique set of approvers
		for (Map.Entry<Map<String, String>, List<RoleRequest>> entry : requestsByApprovers.entrySet()) {
			Map<String, String> approvers = entry.getKey();
			List<RoleRequest> requests = entry.getValue();

			if (!approvers.isEmpty()) {
				EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS_DAILY);
				inputMessage(requests, requests.size(), approvers, template);
			}
		}
	}

	private Map<Map<String, String>, List<RoleRequest>> getEmailMap(List<RoleRequest> allRequests) {
		// Group requests by their approval path and send separate emails
		Map<Map<String, String>, List<RoleRequest>> requestsByApprovers = new HashMap<>();

		for (RoleRequest request : allRequests) {
			Map<String, String> approvers = getEmailsToSendTo(request);
			requestsByApprovers.computeIfAbsent(approvers, _ -> new ArrayList<>()).add(request);
		}
		return requestsByApprovers;
	}

	public boolean notifyReceiverOnRequestApproval(RoleRequest request, String roleName, boolean isITSystemManual, String itSystemName, String requestAuthority) {
		User receiver = request.getReceiver();
		String reason = request.getReason();
		RequestAction action = request.getRequestAction();
		LocalDate startDate = request.getStartDate();
		LocalDate endDate = request.getEndDate();

		if (receiver.getEmail() == null) {
			return false;
		}
		EmailTemplate template = action == RequestAction.REMOVE
			? emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_ROLE_REQUEST_REMOVAL_USER)
			: emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_ROLE_REQUEST_USER);
		if (isITSystemManual) {
			template = emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_MANUAL_ROLE_REQUEST_USER);
		}

		if (!template.isEnabled()) {
			log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
			return false;
		}
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
		EnumMap<EmailTemplatePlaceholder, String> placeholderData = new EnumMap<>(EmailTemplatePlaceholder.class);
		placeholderData.put(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, receiver.getName());
		placeholderData.put(EmailTemplatePlaceholder.ROLE_NAME, roleName);
		placeholderData.put(EmailTemplatePlaceholder.REQUEST_REASON, reason);
		placeholderData.put(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, itSystemName);
		placeholderData.put(EmailTemplatePlaceholder.START_DATE,
			startDate != null ? startDate.format(dateFormatter) : LocalDate.now().format(dateFormatter));
		placeholderData.put(EmailTemplatePlaceholder.STOP_DATE,
			endDate != null ? endDate.format(dateFormatter) : "ubegrænset");
		placeholderData.put(EmailTemplatePlaceholder.REQUESTER_TYPE_PLACEHOLDER, requestAuthority);

		String title = applyReplacers(template.getTitle(), placeholderData);
		String message = applyReplacers(template.getMessage(), placeholderData);

		emailQueueService.queueEmail(receiver.getEmail(), title, message, template, null, null);

		return true;

	}

	public boolean notifyManagerOnRequestapproval(RoleRequest request, boolean isITSystemManual, String roleName, String reason, String requesterAuthority) {
		// notifying manager and authorizationManager
		OrgUnit orgUnit = request.getOrgUnit();
		EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_ROLE_REQUEST_MANAGER);
		if (isITSystemManual) {
			template = emailTemplateService.findByTemplateType(EmailTemplateType.APPROVED_MANUAL_ROLE_REQUEST_MANAGER);
		}
		return notify(request, orgUnit, template, roleName, reason, requesterAuthority);
	}

	public void notifyManagerOnRejectedRequest(final RoleRequest request, final OrgUnit orgUnit, final String roleName, String reason, String requesterAuthority) {
		final EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.REJECTED_ROLE_REQUEST_MANAGER);
		notify(request, orgUnit, template, roleName, reason, requesterAuthority);
	}

	private boolean notify(RoleRequest request, OrgUnit orgUnit, EmailTemplate template, String roleName, String reason, String requesterAuthority) {
		if (!template.isEnabled()) {
			log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
			return false;
		}
		if (orgUnit == null) {
			return false;
		}
		final String action = request.getRequestAction() == RequestAction.ADD ? "tildelt" : "fjernet";

		// Construct data for generally relevant placeholders
		EnumMap<EmailTemplatePlaceholder, String> placeholderData = new EnumMap<>(EmailTemplatePlaceholder.class);
		placeholderData.put(EmailTemplatePlaceholder.ROLE_NAME,
			roleName);
		placeholderData.put(EmailTemplatePlaceholder.USER_PLACEHOLDER,
			request.getReceiver().getName());
		placeholderData.put(EmailTemplatePlaceholder.REQUESTER_PLACEHOLDER,
			request.getRequester().getName());
		placeholderData.put(EmailTemplatePlaceholder.REQUEST_OPERATION_PLACEHOLDER,
			action);
		placeholderData.put(EmailTemplatePlaceholder.REQUEST_REASON,
			reason != null ? reason : "");
		placeholderData.put(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER,
			request.getUserRole() != null && request.getUserRole().getItSystem() != null ? request.getUserRole().getItSystem().getName() : "");
		placeholderData.put(EmailTemplatePlaceholder.REQUESTER_TYPE_PLACEHOLDER,
			requesterAuthority);

		// Send to manager
		User manager = orgUnit.getManager();
		sendMailToManager(manager, placeholderData, template);

		// Send to Auth managers
		List<AuthorizationManager> authorizationManagers = orgUnit.getAuthorizationManagers();
		if (authorizationManagers != null && !authorizationManagers.isEmpty()) {
			for (AuthorizationManager am : authorizationManagers) {
				sendMailToAuthorizationManager(am.getUser(), placeholderData, template);
			}
		}

		return true;
	}

	private void sendMailToManager(User manager, EnumMap<EmailTemplatePlaceholder, String> placeholderData, EmailTemplate template) {
		if (manager != null && StringUtils.hasLength(manager.getEmail())) {
			placeholderData.put(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, manager.getName());

			String title = applyReplacers(template.getTitle(), placeholderData);
			String message = applyReplacers(template.getMessage(), placeholderData);
			emailQueueService.queueEmail(manager.getEmail(), title, message, template, null, null);
		}
	}

	private void sendMailToAuthorizationManager(User authorizationManager, EnumMap<EmailTemplatePlaceholder, String> placeholderData, EmailTemplate template){
		if (authorizationManager == null || authorizationManager.isDeleted()) {
			return;
		}

		if (StringUtils.hasLength(authorizationManager.getEmail())) {
			placeholderData.put(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, authorizationManager.getName());

			String title = applyReplacers(template.getTitle(), placeholderData);
			String message = applyReplacers(template.getMessage(), placeholderData);
			emailQueueService.queueEmail(authorizationManager.getEmail(), title, message, template, null, null);
		}
	}

	private static String replaceTemplateWith(String original, EmailTemplatePlaceholder template, String value) {
		return original.replace(template.getPlaceholder(), value);
	}

	private static String applyReplacers(String original, Map<EmailTemplatePlaceholder, String> replacementData) {
		String result = original;
		for (Map.Entry<EmailTemplatePlaceholder, String> entry : replacementData.entrySet()) {
			result = replaceTemplateWith(result, entry.getKey(), entry.getValue());
		}
		return result;
	}

	private Map<String, String> getEmailsToSendTo(RoleRequest request) {
		Map<ApprovableBy, String> roleRequestApproverEmails = settingsService.getRoleRequestApproverEmails();
		Map<String, String> mailsToSendTo = new HashMap<>();

		for (ApprovableBy approvableBy : requestApproverResolver.resolveEffectiveOptions(request)) {
			// First check if there's a global email for THIS specific approver option
			String emailBySetting = roleRequestApproverEmails.get(approvableBy);
			if (StringUtils.hasLength(emailBySetting)) {
				mailsToSendTo.put(emailBySetting, null);
				continue;
			}

			// No global email for this approver option, find specific users
			switch (approvableBy) {
				case AUTOMATIC -> {
					// Request will be auto-approved, no email needed
				}
				case INHERIT -> {
					// Should not reach here after resolution
					log.warn("INHERIT not fully resolved for request id:{}", request.getId());
				}
				case AUTHRESPONSIBLE -> {
					OrgUnit orgUnit = request.getOrgUnit();
					if (orgUnit != null && orgUnit.getAuthorizationManagers() != null && !orgUnit.getAuthorizationManagers().isEmpty()) {
						for (AuthorizationManager manager : orgUnit.getAuthorizationManagers()) {
							User authorizationManager = manager.getUser();
							if (authorizationManager != null && authorizationManager.getEmail() != null) {
								mailsToSendTo.put(authorizationManager.getEmail(), authorizationManager.getName());
							}
						}
					}
				}
				case MANAGERORSUBSTITUTE -> {
					OrgUnit orgUnit = request.getOrgUnit();
					if (orgUnit != null) {
						Map<String, String> emails = orgUnitService.getManagerAndSubstituteEmail(orgUnit, false);
						// Anmoderen kan ikke godkende sin egen anmodning — undgå at sende mailen
						// til dem hvis de selv er manager på OU'en. Stedfortrædere er fortsat med.
						User requester = request.getRequester();
						if (requester != null && StringUtils.hasLength(requester.getEmail())) {
							emails.remove(requester.getEmail());
						}
						mailsToSendTo.putAll(emails);
					}
				}
				case AUTHORIZED -> {
					Map<String, String> authorizedEmails = getAuthorizedEmails(request);
					mailsToSendTo.putAll(authorizedEmails);
				}
				case SYSTEMRESPONSIBLE -> {
					if (request.getUserRole() != null && request.getUserRole().getItSystem() != null) {
						addSystemResponsibleEmail(request, request.getUserRole().getItSystem(), mailsToSendTo);
					} else if (request.getRoleGroup() != null && request.getRoleGroup().getUserRoleAssignments() != null) {
						for (RoleGroupUserRoleAssignment assignment : request.getRoleGroup().getUserRoleAssignments()) {
							if (assignment.getUserRole() == null || assignment.getUserRole().getItSystem() == null) {
								continue;
							}
							addSystemResponsibleEmail(request, assignment.getUserRole().getItSystem(), mailsToSendTo);
						}
					}
				}
			}
		}

		if (mailsToSendTo.size() > 10) {
			log.warn("Size of list of emails to send request approve email to has exceeded the 10 limit: {}", mailsToSendTo.size());
			return mailsToSendTo.entrySet().stream()
				.limit(10)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		}

		// Step 4: Fallback to global servicedesk email since no one else seems to be able to approve of this request
		if (mailsToSendTo.isEmpty()) {
			String serviceDeskEmail = settingsService.getRequestApproveServicedeskEmail();
			if (StringUtils.hasLength(serviceDeskEmail)) {
				mailsToSendTo.put(serviceDeskEmail, null);
			}
		}

		return mailsToSendTo;
	}

	private Map<String, String> getAuthorizedEmails(RoleRequest requestApprove) {
		ItSystem roleCatalogue = itSystemService.getFirstByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
		SystemRole requestAuthorizedRole = systemRoleService.getFirstByIdentifierAndItSystemId(Constants.ROLE_REQUESTAUTHORIZED, roleCatalogue.getId());
		Set<UserRole> allAuthorizedUserRoles = new HashSet<>(userRoleService.findAllBySystemRole(requestAuthorizedRole));

		return allAuthorizedUserRoles.stream()
			.flatMap(ur -> assignmentService.getActiveByUserRole(ur).stream())
			.map(CurrentAssignment::getUser)
			.filter(user -> isPermittedAccessToOuAndItSystem(requestApprove, user))
			.filter(user -> user.getEmail() != null)
			.collect(Collectors.toMap(User::getEmail, User::getName, (name1, name2) -> name1));
	}

	private void addSystemResponsibleEmail(RoleRequest request, ItSystem itSystem, Map<String, String> mailsToSendTo) {
		List<User> responsibles = itSystemService.getAttestationResponsibles(itSystem);
		if (responsibles.isEmpty()) {
			log.warn("ItSystem {} has no attestation responsible, cannot send system responsible email for request id:{}", itSystem.getId(), request.getId());
			return;
		}
		for (User responsible : responsibles) {
			if (responsible.getEmail() == null) {
				log.warn("Attestation responsible {} on itSystem {} has no email, cannot send system responsible email for request id:{}", responsible.getUuid(), itSystem.getId(), request.getId());
				continue;
			}
			if (requestApproverResolver.canApprove(request, responsible)) {
				mailsToSendTo.put(responsible.getEmail(), responsible.getName());
			}
		}
	}

	private boolean isPermittedAccessToOuAndItSystem(RoleRequest requestApprove, User user) {
		return requestApproverResolver.isItSystemAccessAllowed(requestApprove, user)
			&& requestApproverResolver.isOuAccessAllowed(requestApprove, user);
	}


	private void inputMessage(List<RoleRequest> requestApproves, int count, Map<String, String> mailsToSendTo, EmailTemplate template) {
		if (!template.isEnabled()) {
			log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
			return;
		}

		for (Map.Entry<String, String> recipient : mailsToSendTo.entrySet()) {
			String email = recipient.getKey();
			String name = recipient.getValue();
			String receiverName = StringUtils.hasLength(name) ? name : "rolletildeler";

			String title = template.getTitle()
				.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), receiverName)
				.replace(EmailTemplatePlaceholder.COUNT_PLACEHOLDER.getPlaceholder(), Integer.toString(count));

			String message = template.getMessage()
				.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), receiverName)
				.replace(EmailTemplatePlaceholder.COUNT_PLACEHOLDER.getPlaceholder(), Integer.toString(count));

			emailQueueService.queueEmail(email, title, message, template, null, null);
		}

		for (RoleRequest requestApprove : requestApproves) {
			requestApprove.setEmailSent(true);
		}
		roleRequestDao.saveAll(requestApproves);
	}

	private List<RoleRequest> getRoleRequests(boolean isEmailSent) {
		if (!isEmailSent) {
			return roleRequestDao.findByStatusInAndEmailSent(List.of(RequestApproveStatus.REQUESTED), false);
		}
		return roleRequestDao.findByStatus(RequestApproveStatus.REQUESTED).stream().toList();
	}
}
