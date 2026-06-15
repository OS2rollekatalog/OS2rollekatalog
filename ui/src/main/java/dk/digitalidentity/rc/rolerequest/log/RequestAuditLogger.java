package dk.digitalidentity.rc.rolerequest.log;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.log.AuditLogContextHolder;
import dk.digitalidentity.rc.log.AuditLogger;
import dk.digitalidentity.rc.rolerequest.dao.RequestLogDao;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestLog;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestPostponedConstraint;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RequestAuditLogger {

	private static final String ARG_REQUESTER = "Anmoder";
	private static final String ARG_RECEIVER = "Modtager";
	private static final String ARG_REASON = "Begrundelse";
	private static final String ARG_START_DATE = "Startdato";
	private static final String ARG_END_DATE = "Slutdato";
	private static final String ARG_CONSTRAINTS = "Begrænsninger";
	private static final String ARG_DENIAL_REASON = "Afslagsårsag";

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
			.actingUserUuid(loggedInUser != null ? loggedInUser.getUuid() : null)
			.actingUsername(loggedInUser != null ? loggedInUser.getName() : null)
			.targetUserUuid(request.getReceiver() != null ? request.getReceiver().getUuid() : null)
			.targetUsername(request.getReceiver() != null ? request.getReceiver().getName() : null)
			.userRoleId(request.getUserRole() != null ? request.getUserRole().getId() : null)
			.roleName(request.getUserRole() != null ? request.getUserRole().getName() : null)
			.rolegroupId(request.getRoleGroup() != null ? request.getRoleGroup().getId() : null)
			.rolegroupName(request.getRoleGroup() != null ? request.getRoleGroup().getName() : null)
			.details(details)
			.build();
		requestLogDao.save(log);

		boolean isUserrole = request.getUserRole() != null;
		AuditLogContextHolder.getContext().addArgument(ARG_REQUESTER, request.getRequester().getName());
		AuditLogContextHolder.getContext().addArgument(ARG_RECEIVER, request.getReceiver().getName());
		addEventDetails(event, request, details);
		auditLogger.log(
			isUserrole ? request.getUserRole() : request.getRoleGroup(),
			toEventType(event)
		);
		AuditLogContextHolder.clearContext();

	}

	private void addEventDetails(RequestLogEvent event, RoleRequest request, String details) {
		switch (event) {
			case REQUEST -> {
				if (StringUtils.hasText(details)) {
					AuditLogContextHolder.getContext().addArgument(ARG_REASON, details);
				}
				addDateArguments(request);
			}
			case APPROVE -> {
				addDateArguments(request);
				addConstraintArguments(request);
			}
			case DENY -> {
				if (StringUtils.hasText(details)) {
					AuditLogContextHolder.getContext().addArgument(ARG_DENIAL_REASON, details);
				}
			}
			default -> { }
		}
	}

	private void addDateArguments(RoleRequest request) {
		if (request.getStartDate() != null) {
			AuditLogContextHolder.getContext().addArgument(ARG_START_DATE, request.getStartDate().toString());
		}
		if (request.getEndDate() != null) {
			AuditLogContextHolder.getContext().addArgument(ARG_END_DATE, request.getEndDate().toString());
		}
	}

	private void addConstraintArguments(RoleRequest request) {
		List<RequestPostponedConstraint> constraints = request.getRequestPostponedConstraints();
		if (constraints != null && !constraints.isEmpty()) {
			String constraintSummary = constraints.stream()
				.map(c -> c.getConstraintType().getName() + ": " + (c.getLabel() != null ? c.getLabel() : c.getValue()))
				.collect(Collectors.joining(", "));
			AuditLogContextHolder.getContext().addArgument(ARG_CONSTRAINTS, constraintSummary);
		}
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
