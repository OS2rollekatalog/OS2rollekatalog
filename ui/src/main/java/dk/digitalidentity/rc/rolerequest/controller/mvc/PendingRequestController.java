package dk.digitalidentity.rc.rolerequest.controller.mvc;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestPostponedConstraint;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/ui/request/pending")
public class PendingRequestController {

    @Autowired
    private RequestService rolerequestService;

    @Autowired
    private UserService userService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private ItSystemService itSystemService;

    record PendingRequestListItem(long id, String receiver, String action, String requester, String roleName,
								  String itSystem, String description, String constraints, String requestDate, String reason, String timeFrame, String assignedTo) {
    }

	@GetMapping
	@Transactional(readOnly = true)
	public String pendingApprovalRequestList(Model model) {
		Set<RoleRequest> pendingRequests = rolerequestService.getPendingApprovableRequests();

		List<String> neededOuUuids = pendingRequests.stream()
			.flatMap(r -> r.getRequestPostponedConstraints().stream())
			.filter(c -> c.getConstraintType().getEntityId().equals(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID))
			.flatMap(c -> Arrays.stream(c.getValue().split(",")))
			.map(String::trim)
			.filter(s -> !s.isBlank())
			.distinct()
			.toList();
		Map<String, OrgUnit> ouMap = orgUnitService.getByUuidIn(neededOuUuids).stream()
			.collect(Collectors.toMap(OrgUnit::getUuid, o -> o));

		List<Long> neededItSystemIds = pendingRequests.stream()
			.flatMap(r -> r.getRequestPostponedConstraints().stream())
			.filter(c -> c.getConstraintType().getEntityId().equals(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID))
			.flatMap(c -> Arrays.stream(c.getValue().split(",")))
			.map(String::trim)
			.filter(s -> !s.isBlank())
			.flatMap(s -> {
				try {
					return Stream.of(Long.parseLong(s));
				} catch (NumberFormatException e) {
					log.warn("Malformed IT system constraint value, expected a numeric ID but got: '{}'", s);
					return Stream.empty();
				}
			})
			.distinct()
			.toList();
		Map<Long, ItSystem> itSystemMap = itSystemService.findAllByIdIn(neededItSystemIds).stream()
			.collect(Collectors.toMap(ItSystem::getId, i -> i));

		DateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
        List<PendingRequestListItem> pendingApprovalRequests = rolerequestService.getPendingApprovableRequests().stream().map(request -> new PendingRequestListItem(
            request.getId(),
            request.getReceiver().getName(),
            request.getRequestAction().title,
            request.getRequester().getName(),
            request.getUserRole() == null ? request.getRoleGroup().getName() : request.getUserRole().getName(),
            request.getUserRole() == null ? "(Rollebuket)" : request.getUserRole().getItSystem().getName(),
            request.getUserRole() == null ? request.getRoleGroup().getDescription() : request.getUserRole().getDescription(),
			formatConstraints(request.getRequestPostponedConstraints(), ouMap, itSystemMap),
            formatter.format(request.getRequestTimestamp()),
            request.getReason(),
			formatTimeFrame(request.getStartDate(), request.getEndDate()),
			request.getAssignedTo()
        )).toList();
        model.addAttribute("pendingRequests", pendingApprovalRequests);

        User currentUser = userService.getByUserId(SecurityUtil.getUserId());
        model.addAttribute("currentUserName", currentUser != null ? currentUser.getName() : "");

        return "requestmodule/pending/index";
    }
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private String formatTimeFrame(LocalDate startDate, LocalDate endDate) {
		if (startDate == null && endDate == null) return "Ikke angivet";
		if (startDate != null && endDate != null)
			return startDate.format(DATE_FORMATTER) + " - " + endDate.format(DATE_FORMATTER);
		if (startDate != null)
			return startDate.format(DATE_FORMATTER) + " - ubegrænset";
		return "nu - " + endDate.format(DATE_FORMATTER);
	}

	/**
	 * Formats the constraint to show value and label, if a label exists. Otherwise returns the value.
	 */
	private String formatConstraints(List<RequestPostponedConstraint> constraints, Map<String, OrgUnit> ouMap, Map<Long, ItSystem> itSystemMap) {
		return constraints.stream()
			.collect(Collectors.groupingBy(c -> c.getConstraintType().getEntityId()))
			.entrySet().stream()
			.map(entry -> {
				String entityId = entry.getKey();
				List<RequestPostponedConstraint> group = entry.getValue();
				if (entityId.equals(Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID)) {
					return formatUiConstraints(group, ouMap);
				}
				if (entityId.equals(Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID)) {
					return formatItSystemConstraints(group, itSystemMap);
				}
				return group.stream().map(this::formatConstraint).distinct().collect(Collectors.joining(", "));
			})
			.collect(Collectors.joining(", "));
	}

	private String formatItSystemConstraints(List<RequestPostponedConstraint> group, Map<Long, ItSystem> itSystemMap) {
		List<Long> ids = group.stream()
			.flatMap(c -> Arrays.stream(c.getValue().split(",")))
			.map(String::trim)
			.filter(s -> !s.isBlank())
			.distinct()
			.flatMap(s -> {
				try {
					return java.util.stream.Stream.of(Long.parseLong(s));
				} catch (NumberFormatException e) {
					log.warn("Malformed IT system constraint value, expected a numeric ID but got: '{}'", s);
					return java.util.stream.Stream.empty();
				}
			})
			.toList();
		return ids.stream()
			.map(itSystemMap::get)
			.filter(Objects::nonNull)
			.map(ItSystem::getName)
			.collect(Collectors.joining(", "));
	}

	private String formatUiConstraints(List<RequestPostponedConstraint> group, Map<String, OrgUnit> ouMap) {
		List<String> uuids = group.stream()
			.flatMap(c -> Arrays.stream(c.getValue().split(",")))
			.map(String::trim)
			.filter(s -> !s.isBlank())
			.distinct()
			.toList();

		return uuids.stream().map(ouMap::get)
			.filter(Objects::nonNull)
			.map(OrgUnit::getName)
			.collect(Collectors.joining(", "));
	}

	private String formatConstraint(RequestPostponedConstraint constraint) {
		if (constraint.getLabel() != null && !constraint.getLabel().isBlank()) {
			return constraint.getValue() + " (" + constraint.getLabel() + ")";
		}
		return constraint.getValue();
	}

}
