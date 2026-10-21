package dk.digitalidentity.rc.rolerequest.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.rolerequest.service.permission.ApprovableRequestService;
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
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.SettingsService;
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
	private final ApprovableRequestService approvableRequestService;
	private final ApproverCandidates approverCandidates;
	private final ManagerSubstituteService managerSubstituteService;

	@Transactional
	public void sendMailToRoleAssignerOnce() {
		List<RoleRequest> allRequests = getRoleRequests(false);
		if (allRequests.isEmpty()) {
			return;
		}

		EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_ROLE_ASSIGNERS);
		if (template != null && !template.isEnabled()) {
			for (RoleRequest requestApprove : allRequests) {
				requestApprove.setEmailSent(true);
			}

			roleRequestDao.saveAll(allRequests);
			return;
		}

		Map<Map<String, String>, List<RoleRequest>> emailsAndRequests = getEmailMap(allRequests);

		for (Map.Entry<Map<String, String>, List<RoleRequest>> entry : emailsAndRequests.entrySet()) {
			Map<String, String> approvers = entry.getKey();
			List<RoleRequest> requests = entry.getValue();

			if (!approvers.isEmpty()) {
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

		List<RoleRequest> requestApproves = roleRequestDao.findByStatusInAndEmailSentToServicedesk(List.of(RequestApproveStatus.REQUESTED), false);
		if (requestApproves.isEmpty()) {
			return;
		}

		EmailTemplate template = emailTemplateService.findByTemplateType(EmailTemplateType.WAITING_REQUESTS_SERVICEDESK);
		if (template != null && !template.isEnabled()) {
			requestApproves.forEach(request -> request.setEmailSentToServicedesk(true));
			roleRequestDao.saveAll(requestApproves);

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
		long start = System.currentTimeMillis();
		// possible recipients per approver type, not yet checked against ApproverRule
		Map<RoleRequest, List<ApproverCandidates.Candidate>> candidatesByRequest = approverCandidates.collect(allRequests);

		// one ApproverRule query per distinct candidate, covering all requests at once
		List<Long> requestIds = allRequests.stream().map(RoleRequest::getId).toList();
		Map<String, Set<Long>> approvableByUserUuid = new HashMap<>();
		candidatesByRequest.values().stream()
			.flatMap(List::stream)
			.map(ApproverCandidates.Candidate::approver)
			.filter(Objects::nonNull)
			.forEach(user -> approvableByUserUuid.computeIfAbsent(user.getUuid(), _ -> approvableRequestService.emailRecipientAmong(user, requestIds)));

		// requests with the same recipients share one email
		Map<Map<String, String>, List<RoleRequest>> requestsByApprovers = new HashMap<>();
		for (Map.Entry<RoleRequest, List<ApproverCandidates.Candidate>> entry : candidatesByRequest.entrySet()) {
			Map<String, String> approvers = recipients(entry.getKey(), entry.getValue(), approvableByUserUuid);
			requestsByApprovers.computeIfAbsent(approvers, _ -> new ArrayList<>()).add(entry.getKey());
		}

		log.info("Found approvers for {} pending requests among {} candidates in {} ms",
			allRequests.size(), approvableByUserUuid.size(), System.currentTimeMillis() - start);
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
		placeholderData.put(EmailTemplatePlaceholder.REQUEST_REASON, formatReason(reason));
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
			log.warn("Request id:{} has no orgunit, cannot notify manager and authorization managers", request.getId());
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
			formatReason(reason));
		placeholderData.put(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER,
			request.getUserRole() != null && request.getUserRole().getItSystem() != null ? request.getUserRole().getItSystem().getName() : "");
		placeholderData.put(EmailTemplatePlaceholder.REQUESTER_TYPE_PLACEHOLDER,
			requesterAuthority);

		// Send to manager
		User manager = orgUnit.getManager();
		sendMailToManager(manager, placeholderData, template);

		for (User substitute : managerSubstituteService.getSubstitutesForOrgUnit(orgUnit)) {
			sendMailToRecipient(substitute, placeholderData, template);
		}

		// Send to Auth managers
		List<AuthorizationManager> authorizationManagers = orgUnit.getAuthorizationManagers();
		if (authorizationManagers != null && !authorizationManagers.isEmpty()) {
			for (AuthorizationManager am : authorizationManagers) {
				sendMailToRecipient(am.getUser(), placeholderData, template);
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

	private void sendMailToRecipient(User recipient, EnumMap<EmailTemplatePlaceholder, String> placeholderData, EmailTemplate template){
		if (recipient == null || recipient.isDeleted()) {
			return;
		}

		if (StringUtils.hasLength(recipient.getEmail())) {
			placeholderData.put(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER, recipient.getName());

			String title = applyReplacers(template.getTitle(), placeholderData);
			String message = applyReplacers(template.getMessage(), placeholderData);
			emailQueueService.queueEmail(recipient.getEmail(), title, message, template, null, null);
		}
	}

	private static String formatReason(String reason) {
		return StringUtils.hasLength(reason) ? "Begrundelse: " + reason : "";
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

	private Map<String, String> recipients(RoleRequest request, List<ApproverCandidates.Candidate> candidates, Map<String, Set<Long>> approvableByUserUuid) {
		Map<String, String> mailsToSendTo = new LinkedHashMap<>();
		for (ApproverCandidates.Candidate candidate : candidates) {
			// settings emails have no approver and are always mailed
			if (candidate.approver() == null || approvableByUserUuid.get(candidate.approver().getUuid()).contains(request.getId())) {
				mailsToSendTo.put(candidate.email(), candidate.name());
			}
		}

		if (mailsToSendTo.size() > 10) {
			log.warn("Size of list of emails to send request approve email to has exceeded the 10 limit: {}", mailsToSendTo.size());
			Map<String, String> capped = new LinkedHashMap<>();
			for (Map.Entry<String, String> entry : mailsToSendTo.entrySet()) {
				if (capped.size() >= 10) {
					break;
				}
				capped.put(entry.getKey(), entry.getValue());
			}
			return capped;
		}

		// no approver found (incl. stuck INHERIT/AUTOMATIC requests), so fall back to the servicedesk
		if (mailsToSendTo.isEmpty()) {
			String serviceDeskEmail = settingsService.getRequestApproveServicedeskEmail();
			if (StringUtils.hasLength(serviceDeskEmail)) {
				mailsToSendTo.put(serviceDeskEmail, null);
			}
		}

		return mailsToSendTo;
	}
	private void inputMessage(List<RoleRequest> requestApproves, int count, Map<String, String> mailsToSendTo, EmailTemplate template) {
		for (RoleRequest requestApprove : requestApproves) {
			requestApprove.setEmailSent(true);
		}
		roleRequestDao.saveAll(requestApproves);

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
	}

	private List<RoleRequest> getRoleRequests(boolean includeAlreadyEmailed) {
		// the positions fetch join repeats root rows; RoleRequest has no equals, so this dedupes by identity
		return roleRequestDao.findPendingForApprovalEmails(includeAlreadyEmailed).stream().distinct().toList();
	}
}
