package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.RequestApproveDao;
import dk.digitalidentity.rc.dao.model.RequestApprove;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.model.RequestApproveManagerAction;
import dk.digitalidentity.rc.service.model.RequestApproveWrapper;
import lombok.extern.log4j.Log4j;

@Log4j
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
	private UserService userService;
	
	@Autowired
	private EmailService emailService;
	
	@Autowired
	private AccessConstraintService assignerRoleContraint;
	
	public RequestApprove getById(long id) {
		return requestApproveDao.getById(id);
	}
	
	public void delete(RequestApprove requestApprove) {
		requestApproveDao.delete(requestApprove);
	}

	public RequestApprove save(RequestApprove request) {
		return requestApproveDao.save(request);		
	}
	
	public List<RequestApprove> getPendingNotifications() {
		if (settingsService.getRequestApproveManagerAction().equals(RequestApproveManagerAction.APPROVE)) {
			return requestApproveDao.getByRoleAssignerNotifiedFalseAndStatusIn(Arrays.asList(RequestApproveStatus.MANAGER_APPROVED));
		}

		return requestApproveDao.getByRoleAssignerNotifiedFalseAndStatusIn(Arrays.asList(RequestApproveStatus.REQUESTED, RequestApproveStatus.MANAGER_NOTIFIED));
	}
	
	public List<RequestApproveWrapper> getPendingRequests() {
		List<RequestApproveStatus> stati = new ArrayList<>();
		
		if (settingsService.getRequestApproveManagerAction().equals(RequestApproveManagerAction.APPROVE)) {
			if (SecurityUtil.hasRole(Constants.ROLE_MANAGER) || SecurityUtil.hasRole(Constants.ROLE_SUBSTITUTE)) {
				stati.add(RequestApproveStatus.MANAGER_NOTIFIED);
				stati.add(RequestApproveStatus.REQUESTED);
			}
			else {
				stati.add(RequestApproveStatus.MANAGER_APPROVED);
			}
		}
		else {
			stati.add(RequestApproveStatus.MANAGER_NOTIFIED);
			stati.add(RequestApproveStatus.REQUESTED);
		}
		
		List<RequestApprove> requests = requestApproveDao.getByStatusIn(stati);		
		requests = filterRequestsByAccess(requests);

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

	// the method assumes that only those with relevant stati is given as input
	// so no stati-related filtering will happen, only access - this means that
	// wrong stati input can result in wrong output
	public List<RequestApprove> filterRequestsByAccess(List<RequestApprove> requests) {
		List<RequestApprove> result = new ArrayList<>();

		boolean isAdmin = SecurityUtil.hasRole(Constants.ROLE_ADMINISTRATOR);
		boolean isRoleAssigner = SecurityUtil.hasRole(Constants.ROLE_ASSIGNER);

		List<User> requesters = requests.stream().map(r -> r.getRequester()).collect(Collectors.toList());
		
		requesters = assignerRoleContraint.filterUsersUserCanAccess(requesters, true);

		for (RequestApprove request : requests) {
			User requester = request.getRequester();

			boolean isManager = userService.isManagerFor(requester);

			switch (request.getStatus()) {
				case ASSIGNED:
				case REJECTED:
					// these are not visible, and are always filtered (and should not be given as input to this method anyway)
					break;
				case MANAGER_APPROVED:
					// only visible for assigners and admins
					if (isAdmin) {
						result.add(request);
					}
					else if (isRoleAssigner) {
						for (User r : requesters) {
							if (r.getUuid().equals(request.getRequester().getUuid())) {
								result.add(request);
								break;
							}
						}
					}
					break;
				case REQUESTED:
				case MANAGER_NOTIFIED:
					// visible for assigners, admins and managers
					if (isAdmin || isManager) {
						result.add(request);
					}
					else if (isRoleAssigner) {
						for (User r : requesters) {
							if (r.getUuid().equals(request.getRequester().getUuid())) {
								result.add(request);
								break;
							}
						}
					}
					break;
			}
		}
		
		return result;
	}

	public List<RequestApproveWrapper> getRequestByRequester(User requester) {
		List<RequestApprove> requests = requestApproveDao.getByRequester(requester);
		
		return wrapRequests(requests);
	}

	public boolean requestUserRole(UserRole userRole, User user, String reason) {
		if (!settingsService.isRequestApproveEnabled()) {
			log.warn("User " + user.getUserId() + " attempting to request role when request/approval is turned off!");
			return false;
		}
		else if (userRole == null) {
			log.warn("User " + user.getUserId() + " attempting to request role that does not exist");
			return false;
		}
		else if (!userRoleService.canRequestRole(userRole, user)) {
			log.warn("User " + user.getUserId() + " attempting to request role that user is not allowed to request: " + userRole.getId());
			return false;
		}
		
		RequestApproveStatus status = RequestApproveStatus.REQUESTED;
		User manager = null;

		if (!settingsService.getRequestApproveManagerAction().equals(RequestApproveManagerAction.NONE)) {
			List<User> managers = userService.getManager(user);
			
			if (managers.size() > 0) {
				// TODO: should we ask the user instead?
				manager = managers.get(0);

				// try to notify manager
				status = notifyManager(manager, user, userRole.getName(), userRole.getDescription());
			}
			else {
				log.warn("Unable to find manager for " + user.getUserId() + " no notification send!");
			}
		}

		createRequest(reason, user, manager, userRole.getId(), EntityType.USERROLE, status);
		
		return true;
	}
	
	public boolean requestRoleGroup(RoleGroup roleGroup, User user, String reason) {
		if (!settingsService.isRequestApproveEnabled()) {
			log.warn("User " + user.getUserId() + " attempting to request rolegroup when request/approval is turned off!");
			return false;
		}
		else if (roleGroup == null) {
			log.warn("User " + user.getUserId() + " attempting to request rolegroup that does not exist");
			return false;
		}
		else if (!roleGroupService.canRequestRole(roleGroup, user)) {
			log.warn("User " + user.getUserId() + " attempting to request role that user is not allowed to request: " + roleGroup.getId());
			return false;
		}

		RequestApproveStatus status = RequestApproveStatus.REQUESTED;
		User manager = null;

		if (!settingsService.getRequestApproveManagerAction().equals(RequestApproveManagerAction.NONE)) {
			List<User> managers = userService.getManager(user);
			
			if (managers.size() > 0) {
				// TODO: should we ask the user instead?
				manager = managers.get(0);

				// try to notify manager
				status = notifyManager(manager, user, roleGroup.getName(), roleGroup.getDescription());
			}
			else {
				log.warn("Unable to find manager for " + user.getUserId() + " no notification send!");
			}
		}

		createRequest(reason, user, manager, roleGroup.getId(), EntityType.ROLEGROUP, status);

		return true;
	}
	
	private void createRequest(String reason, User user, User manager, long roleId, EntityType roleType, RequestApproveStatus status) {
		RequestApprove request = new RequestApprove();
		
		// is there an existing request, then overwrite it (resetting timestamps, status, etc ;))
		List<RequestApprove> requests = requestApproveDao.getByRequester(user);
		for (RequestApprove req : requests) {
			if (req.getRoleType().equals(roleType) && req.getRoleId() == roleId) {
				request = req;
				break;
			}
		}

		request.setRoleAssignerNotified(false);
		request.setManager(manager);
		request.setReason(reason);
		request.setAssigner(null);
		request.setRejectReason(null);
		request.setRequester(user);
		request.setRequestTimestamp(new Date());
		request.setRoleId(roleId);
		request.setRoleType(roleType);
		request.setStatus(status);
		request.setStatusTimestamp(new Date());
		requestApproveDao.save(request);
	}
	
	private RequestApproveStatus notifyManager(User manager, User user, String roleName, String roleDescription) {
		String subject = "";
		if (settingsService.getRequestApproveManagerAction().equals(RequestApproveManagerAction.APPROVE)) {
			subject = "[Handling krævet] Anmodning om rettigheder fra " + user.getName();
		}
		else {
			subject = "[Information] Anmodning om rettigheder fra " + user.getName();
		}

		String message = "Din medarbejder <b>" + user.getName() + "</b> har anmodet om at få tildelt rettigheden <b>" + roleName + "</b>.<br/><br/>";
		message += "<b>Rettighedens beskrivelse:<b><br/>";
		message += roleDescription;

		message += "<br/><br/><br/>";
		if (settingsService.getRequestApproveManagerAction().equals(RequestApproveManagerAction.APPROVE)) {	
			message += "Hvis du vil godkende denne anmodning, skal du logge på rollekataloget og godkende den.";
		}
		else {
			message += "Denne besked er blot til information, og du skal kun gøre noget hvis du ikke mener at medarbejderen skal have denne rettighed tildelt.";
		}
		
		return notifyManager(manager, subject, message);
	}

	private RequestApproveStatus notifyManager(User manager, String subject, String message) {

		if (manager.getEmail() != null) {
			emailService.sendMessage(manager.getEmail(), subject, message);

			return RequestApproveStatus.MANAGER_NOTIFIED;
		}
		else {
			log.warn("Manager " + manager.getUserId() + " does not have an email address, so we cannot send notification to manager!");
		}
		
		return RequestApproveStatus.REQUESTED;
	}
	
	private List<RequestApproveWrapper> wrapRequests(List<RequestApprove> requests) {
		List<RequestApproveWrapper> result = new ArrayList<>();

		for (RequestApprove request : requests) {
			RequestApproveWrapper wrapper = new RequestApproveWrapper();
			wrapper.setRequest(request);

			String reason = request.getReason()
					.replace("\\", "\\\\")
					.replace("\b", "\\b")
					.replace("\f", "\\f")
					.replace("\n", "\\n")
					.replace("\r", "\\r")
					.replace("\t", "\\t")
					.replace("\'", "\\'")
					.replace("\"", "\\\"");
			
			wrapper.setChildJson("{'id':" + request.getId() + ",'reason':'" + reason + "','userId':'" + request.getRequester().getName() + " (" + request.getRequester().getUserId() + ")" + "'}");

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
}
