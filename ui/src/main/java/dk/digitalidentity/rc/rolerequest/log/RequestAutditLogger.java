package dk.digitalidentity.rc.rolerequest.log;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.log.AuditLogContextHolder;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.rolerequest.dao.RequestLogDao;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestLog;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class RequestAutditLogger {

	@Autowired
	private UserService userService;

	@Autowired
	private RequestLogDao requestLogDao;

	@Autowired
	private AuditLogger auditLogger;


	public void logRequest(RequestLogEvent event, RoleRequest request, String details) {
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());

		RequestLog log = RequestLog.builder()
			.requestEvent(event)
			.requestTimestamp(LocalDateTime.now())
			.actingUser(loggedInUser)
			.targetUser(request.getReceiver())
			.userRole(request.getUserRole())
			.roleGroup(request.getRoleGroup())
			.details(details)
			.build();
		requestLogDao.save(log);

		boolean isUserrole = request.getUserRole() != null;
		AuditLogContextHolder.getContext().addArgument("Anmoder", request.getRequester().getName());
		AuditLogContextHolder.getContext().addArgument("Modtager", request.getReceiver().getName());
		auditLogger.log(
			isUserrole ? request.getUserRole() : request.getRoleGroup(),
			toEventType(event)
		);
		AuditLogContextHolder.clearContext();

	}

	public EventType toEventType(RequestLogEvent event) {
		return switch (event) {
			case REMOVE -> EventType.REQUEST_ROLE_REMOVAL_FOR;
			case CANCEL -> EventType.CANCEL_REQUEST;
			case APPROVE -> EventType.APPROVE_REQUEST;
			case REQUEST -> EventType.REQUEST_ROLE_FOR;
			case DENY -> EventType.REJECT_REQUEST;
		};
	}
}
