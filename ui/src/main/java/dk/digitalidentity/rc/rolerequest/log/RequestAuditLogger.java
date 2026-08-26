package dk.digitalidentity.rc.rolerequest.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
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

	@Autowired
	private ObjectMapper objectMapper;

	public void logRequest(RequestLogEvent event, RoleRequest request, String details, User actingUser) {
		doLogRequest(event, request, details, actingUser);
	}

	public void logRequest(RequestLogEvent event, RoleRequest request, String details) {
		User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
		User actingUser = loggedInUser != null ? loggedInUser : request.getRequester();
		doLogRequest(event, request, details, actingUser);
	}

	private void doLogRequest(RequestLogEvent event, RoleRequest request, String details, User actingUser) {

		boolean isUserrole = request.getUserRole() != null;
		AuditLogContextHolder.getContext().addArgument(ARG_REQUESTER, request.getRequester() != null ? request.getRequester().getName() : null);
		AuditLogContextHolder.getContext().addArgument(ARG_RECEIVER, request.getReceiver() != null ? request.getReceiver().getName() : null);
		addEventDetails(event, request, details);

		RequestLog log = RequestLog.builder()
			.requestEvent(event)
			.requestTimestamp(LocalDateTime.now())
			.actingUserUuid(actingUser != null ? actingUser.getUuid() : null)
			.actingUsername(actingUser != null ? actingUser.getName() : null)
			.actingUserId(actingUser != null ? actingUser.getUserId() : null)
			.targetUserUuid(request.getReceiver() != null ? request.getReceiver().getUuid() : null)
			.targetUsername(request.getReceiver() != null ? request.getReceiver().getName() : null)
			.targetUserId(request.getReceiver() != null ? request.getReceiver().getUserId() : null)
			.userRoleId(request.getUserRole() != null ? request.getUserRole().getId() : null)
			.roleName(request.getUserRole() != null ? request.getUserRole().getName() : null)
			.rolegroupId(request.getRoleGroup() != null ? request.getRoleGroup().getId() : null)
			.rolegroupName(request.getRoleGroup() != null ? request.getRoleGroup().getName() : null)
			.details(buildDetails())
			.detailsJson(buildDetailsJson(request))
			.build();
		requestLogDao.save(log);

		auditLogger.log(
			isUserrole ? request.getUserRole() : request.getRoleGroup(),
			toEventType(event)
		);
		AuditLogContextHolder.clearContext();

	}

	private static String buildDetails() {
		Map<String, String> arguments = AuditLogContextHolder.getContext().getArguments();
		if (arguments == null || arguments.isEmpty()) {
			return null;
		}
		return arguments.entrySet().stream()
			.map(e -> e.getKey() + "=" + e.getValue())
			.collect(Collectors.joining(", "));
	}

	private String buildDetailsJson(RoleRequest request) {
		Map<String, String> arguments = AuditLogContextHolder.getContext().getArguments();
		if (arguments == null || arguments.isEmpty()) {
			return null;
		}

		List<DetailEntry> entries = new ArrayList<>();
		for (Map.Entry<String, String> argument : arguments.entrySet()) {
			if (argument.getKey().equals(ARG_CONSTRAINTS)) {
				entries.addAll(buildConstraintEntries(request));
			}
			else {
				entries.add(new DetailEntry(argument.getKey(), argument.getValue()));
			}
		}

		try {
			return objectMapper.writeValueAsString(entries);
		}
		catch (JsonProcessingException e) {
			log.warn("Could not serialize request log details to JSON", e);
			return null;
		}
	}

	private List<DetailEntry> buildConstraintEntries(RoleRequest request) {
		List<RequestPostponedConstraint> constraints = request.getRequestPostponedConstraints();
		if (constraints == null || constraints.isEmpty()) {
			return List.of();
		}
		return constraints.stream()
			.map(c -> new DetailEntry(ARG_CONSTRAINTS + " (" + c.getConstraintType().getName() + ")", c.getLabel() != null ? c.getLabel() : c.getValue()))
			.toList();
	}

	private record DetailEntry(String label, String value) {
	}

	private void addEventDetails(RequestLogEvent event, RoleRequest request, String details) {
		switch (event) {
			case REQUEST -> {
				if (StringUtils.hasText(details)) {
					AuditLogContextHolder.getContext().addArgument(ARG_REASON, details);
				}
				addDateArguments(request);
				addConstraintArguments(request);
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
