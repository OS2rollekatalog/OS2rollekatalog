package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.config.SessionConstants;
import dk.digitalidentity.rc.controller.rest.model.PostponedConstraintDTO;
import dk.digitalidentity.rc.dao.RequestApproveDao;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RequestApprove;
import dk.digitalidentity.rc.dao.model.RequestApprovePostponedConstraint;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.service.model.RequestApproveWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RequestApproveService {
	
	@Autowired
	private SettingsService settingsService;
	
	@Autowired
	private UserRoleService userRoleService;
	
	@Autowired
	private RoleGroupService roleGroupService;
	
	@Autowired
	private RequestApproveDao requestApproveDao;

	@Autowired
	private EmailTemplateService emailTemplateService;
	
	@Autowired
	private EmailQueueService emailQueueService;
	
	@Autowired
	private RequestApproveService requestApproveService;
	
	@Autowired
	private AccessConstraintService accessConstraintService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private ConstraintTypeService constraintTypeService;

	public RequestApprove getById(long id) {
		return requestApproveDao.getById(id);
	}
	
	public void delete(RequestApprove requestApprove) {
		requestApproveDao.delete(requestApprove);
	}

	public RequestApprove save(RequestApprove request) {
		return requestApproveDao.save(request);		
	}

	public List<RequestApproveWrapper> getPendingRequestsAuthorizationManager() {		
		List<RequestApprove> requests = requestApproveDao.getByStatusIn(Collections.singletonList(RequestApproveStatus.REQUESTED));

		// filter in case the user has a constrained access role
		List<String> ous = accessConstraintService.getConstrainedOrgUnits(true);
		List<Long> itSystems = accessConstraintService.itSystemsUserCanEdit();
		
		if (ous == null && itSystems == null) {
			return wrapRequests(requests);
		}

		// we need to filter, so filter on OUs and ItSystems
		for (Iterator<RequestApprove> iterator = requests.iterator(); iterator.hasNext();) {
			RequestApprove request = iterator.next();
			if (ous != null && request.getOrgUnit() != null && !ous.contains(request.getOrgUnit().getUuid())) {
				iterator.remove();
			} else 
			if (itSystems != null) {
				if (request.getRoleType() == EntityType.USERROLE) {
					UserRole userRole = userRoleService.getById(request.getRoleId());
					if (!itSystems.contains(userRole.getItSystem().getId())) {
						iterator.remove();
					}
				} else if (request.getRoleType() == EntityType.ROLEGROUP) {
					RoleGroup rg = roleGroupService.getById(request.getRoleId());
					for (UserRole userRole : rg.getUserRoleAssignments().stream().map(RoleGroupUserRoleAssignment::getUserRole).toList()) {
						if (!itSystems.contains(userRole.getItSystem().getId())) {
							iterator.remove();
							break;
						}
					}
				}
			}
		}

		return wrapRequests(requests);
	}
	
	@Transactional
	public void deleteOld() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -14);
        Date before = cal.getTime();
        
		List<RequestApproveStatus> stati = new ArrayList<>();
		stati.add(RequestApproveStatus.ASSIGNED);
		stati.add(RequestApproveStatus.REJECTED);

		requestApproveDao.deleteByStatusInAndStatusTimestampBefore(stati, before);
	}

	public List<RequestApproveWrapper> getRequestByRequester(User requester) {
		List<RequestApprove> requests = requestApproveDao.getByRequester(requester);
		
		return wrapRequests(requests);
	}
	
	public boolean requestUserRoleRemoval(UserRole userRole, User requestedBy, String reason, User requestedFor, OrgUnit orgUnit) {
		if (!settingsService.isRequestApproveEnabled()) {
			log.warn("User " + requestedBy.getUserId() + " attempting to request role when request/approval is turned off!");
			return false;
		}
		else if (userRole == null) {
			log.warn("User " + requestedBy.getUserId() + " attempting to request role that does not exist");
			return false;
		}
		else if (!userRoleService.canRequestRole(userRole, requestedBy)) {
			log.warn("User " + requestedBy.getUserId() + " attempting to request role that user is not allowed to request: " + userRole.getId());
			return false;
		}
		
		RequestApproveStatus status = RequestApproveStatus.REQUESTED;
		
		createRequest(reason, requestedBy, userRole.getId(), EntityType.USERROLE, status, requestedFor, orgUnit, RequestAction.REMOVE, null);
		
		return true;
	}
	
	public boolean requestUserRole(UserRole userRole, User requestedBy, String reason, User requestedFor, OrgUnit orgUnit, List<PostponedConstraintDTO> constraints) {
		if (!settingsService.isRequestApproveEnabled()) {
			log.warn("User " + requestedBy.getUserId() + " attempting to request role when request/approval is turned off!");
			return false;
		}
		else if (userRole == null) {
			log.warn("User " + requestedBy.getUserId() + " attempting to request role that does not exist");
			return false;
		}
		else if (!userRoleService.canRequestRole(userRole, requestedBy)) {
			log.warn("User " + requestedBy.getUserId() + " attempting to request role that user is not allowed to request: " + userRole.getId());
			return false;
		}
		
		RequestApproveStatus status = RequestApproveStatus.REQUESTED;

		createRequest(reason, requestedBy, userRole.getId(), EntityType.USERROLE, status, requestedFor, orgUnit, RequestAction.ADD, constraints);
		
		return true;
	}
	public boolean requestRoleGroupRemoval(RoleGroup roleGroup, User requestedBy, String reason, User requestedFor, OrgUnit orgUnit)  {
		if (!settingsService.isRequestApproveEnabled()) {
			log.warn("User " + requestedBy.getUserId() + " attempting to request rolegroup  removal when request is turned off!");
			return false;
		}
		else if (roleGroup == null) {
			log.warn("User " + requestedBy.getUserId() + " attempting to request rolegroup removal of rolegroup that does not exist");
			return false;
		}
		else if (!roleGroupService.canRequestRole(roleGroup, requestedBy)) {
			log.warn("User " + requestedBy.getUserId() + " attempting to request role that user is not allowed to request: " + roleGroup.getId());
			return false;
		}
		
		createRequest(reason, requestedBy, roleGroup.getId(), EntityType.ROLEGROUP, RequestApproveStatus.REQUESTED, requestedFor, orgUnit, RequestAction.REMOVE, null);
		return true;
	}
	
	public boolean requestRoleGroup(RoleGroup roleGroup, User requestedBy, String reason, User requestedFor, OrgUnit orgUnit) {
		if (!settingsService.isRequestApproveEnabled()) {
			log.warn("User " + requestedBy.getUserId() + " attempting to request rolegroup when request/approval is turned off!");
			return false;
		}
		else if (roleGroup == null) {
			log.warn("User " + requestedBy.getUserId() + " attempting to request rolegroup that does not exist");
			return false;
		}
		else if (!roleGroupService.canRequestRole(roleGroup, requestedBy)) {
			log.warn("User " + requestedBy.getUserId() + " attempting to request role that user is not allowed to request: " + roleGroup.getId());
			return false;
		}
		
		RequestApproveStatus status = RequestApproveStatus.REQUESTED;
		
		createRequest(reason, requestedBy, roleGroup.getId(), EntityType.ROLEGROUP, status, requestedFor, orgUnit, RequestAction.ADD, null);

		return true;
		
	}
	
	private void createRequest(String reason, User user, long roleId, EntityType roleType, RequestApproveStatus status, User requestedFor, OrgUnit orgUnit, RequestAction requestAction, List<PostponedConstraintDTO> constraints) {
		RequestApprove request = new RequestApprove();
		boolean existingRequest = false;
		
		// is there an existing request, then overwrite it (resetting timestamps, status, etc ;))
		List<RequestApprove> requests = requestApproveDao.getByRequester(user);
		for (RequestApprove req : requests) {
			if (req.getRoleType().equals(roleType) && req.getRoleId() == roleId) {
				if (requestedFor == null) {
					request = req;
					existingRequest = true;
					break;
				}
				else {
					if (req.getRequestedFor().equals(requestedFor)) {
						request = req;
						existingRequest = true;
						break;
					}
				}
			}
		}

		request.setRoleAssignerNotified(false);
		request.setReason(reason);
		request.setAssigner(null);
		request.setRejectReason(null);
		request.setRequester(user);
		request.setRequestTimestamp(new Date());
		request.setRoleId(roleId);
		request.setRoleType(roleType);
		request.setStatus(status);
		request.setStatusTimestamp(new Date());
		request.setOrgUnit(orgUnit);
		request.setRequestAction(requestAction);
		
		if (requestedFor == null) {
			request.setRequestedFor(user);
		}
		else {
			request.setRequestedFor(requestedFor);
		}

		if (request.getRequestApprovePostponedConstraints() == null) {
			request.setRequestApprovePostponedConstraints(new ArrayList<>());
		}

		if (existingRequest) {
			request.getRequestApprovePostponedConstraints().clear();
		}

		if (constraints != null && !constraints.isEmpty()) {
			List<RequestApprovePostponedConstraint> postponedConstraintsForRequest = new ArrayList<>();
			for (PostponedConstraintDTO postponedConstraintDTO : constraints) {
				SystemRole systemRole = systemRoleService.getById(postponedConstraintDTO.getSystemRoleId());
				ConstraintType constraintType = constraintTypeService.getByUuid(postponedConstraintDTO.getConstraintTypeUuid());
				if (systemRole == null || constraintType == null) {
					continue;
				}

				RequestApprovePostponedConstraint postponedConstraint = new RequestApprovePostponedConstraint();
				postponedConstraint.setConstraintType(constraintType);
				postponedConstraint.setSystemRole(systemRole);
				postponedConstraint.setValue(postponedConstraintDTO.getValue());
				postponedConstraint.setRequestApprove(request);

				postponedConstraintsForRequest.add(postponedConstraint);
			}


			request.getRequestApprovePostponedConstraints().addAll(postponedConstraintsForRequest);
		}
		
		requestApproveDao.save(request);
	}

	private List<RequestApproveWrapper> wrapRequests(List<RequestApprove> requests) {
		List<RequestApproveWrapper> result = new ArrayList<>();

		for (RequestApprove request : requests) {
			RequestApproveWrapper wrapper = new RequestApproveWrapper();
			wrapper.setRequest(request);
			wrapper.setRequestTimestamp(request.getRequestTimestamp());

			String reason = replaceForJSON(request.getReason());

			String postponedConstraints = "";
			if (request.getRequestApprovePostponedConstraints() != null && !request.getRequestApprovePostponedConstraints().isEmpty()) {
				StringBuilder builder = new StringBuilder();
				for (RequestApprovePostponedConstraint requestApprovePostponedConstraint : request.getRequestApprovePostponedConstraints()) {
					String constraint = "<b>" + requestApprovePostponedConstraint.getConstraintType().getName() + ":</b> " + requestApprovePostponedConstraint.getValue() + "<br/>";
					builder.append(replaceForJSON(constraint));
				}
				postponedConstraints = builder.toString();
			}
			
			wrapper.setChildJson("{'id':" + request.getId() + ",'reason':'" + reason + "','userId':'" + request.getRequester().getName() + " (" + request.getRequester().getUserId() + ")','action':'" + request.getRequestAction().name() + "','constraints':'" + postponedConstraints + "'}");

			switch (request.getRoleType()) {
				case USERROLE:
					UserRole userRole = userRoleService.getById(request.getRoleId());
					if (userRole != null) {
						wrapper.setUserRole(userRole);
						wrapper.setRoleName(userRole.getName());
						wrapper.setItSystemName(userRole.getItSystem().getName());
						wrapper.setRoleDescription(userRole.getDescription());
					}
					break;
				case ROLEGROUP:
					RoleGroup roleGroup = roleGroupService.getById(request.getRoleId());
					if (roleGroup != null) {
						wrapper.setRoleGroup(roleGroup);
						wrapper.setRoleName(roleGroup.getName());
						wrapper.setRoleDescription(roleGroup.getDescription());
						wrapper.setItSystemName("(rollebuket)");
					}
					break;
				default:
					throw new RuntimeException("Unexpected roleType: " + request.getRoleType());
			}
			
			if (wrapper.getRoleName() != null) {
				result.add(wrapper);
			}
		}
		
		return result;
	}

	private String replaceForJSON(String string) {
		return string.replace("\\", "\\\\")
				.replace("\b", "\\b")
				.replace("\f", "\\f")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t")
				.replace("\'", "\\'")
				.replace("\"", "\\\"");
	}

	@Transactional
	public void sendMailToRoleAssigner() {
		List<RequestApproveStatus> stati = new ArrayList<>();
		stati.add(RequestApproveStatus.REQUESTED);
		List<RequestApprove> requestApproves = requestApproveDao.getByStatusIn(stati).stream().filter(r -> !r.isEmailSent()).collect(Collectors.toList());
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
					for (RequestApprove requestApprove : requestApproves) {
						requestApprove.setEmailSent(true);
						requestApproveService.save(requestApprove);
					}
				}
				else {
					log.info("Email template with type " + template.getTemplateType() + " is disabled. Email was not sent.");
				}
			}
		}
	}

	// TODO: optimize
	public long count() {
		return getPendingRequestsAuthorizationManager().size();
	}

	@Transactional
	public void setCount(HttpServletRequest request) {
		long count = requestApproveService.count();
		if (count > 0) {
			request.getSession().setAttribute(SessionConstants.SESSION_REQUEST_COUNT, count);
		}
	}
}
