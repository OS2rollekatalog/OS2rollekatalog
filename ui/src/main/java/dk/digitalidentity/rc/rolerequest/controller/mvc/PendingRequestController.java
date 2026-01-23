package dk.digitalidentity.rc.rolerequest.controller.mvc;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.rolerequest.model.entity.RequestPostponedConstraint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import dk.digitalidentity.rc.rolerequest.service.RequestService;

@Controller
@RequestMapping("/ui/request/pending")
public class PendingRequestController {

    @Autowired
    private RequestService rolerequestService;

    record PendingRequestListItem(long id, String receiver, String action, String requester, String roleName,
								  String itSystem, String description, String constraints, String requestDate, String reason, String timeFrame) {
    }

	@GetMapping
	@Transactional(readOnly = true)
	public String pendingApprovalRequestList(Model model) {
		DateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
        List<PendingRequestListItem> pendingApprovalRequests = rolerequestService.getPendingApprovableRequests().stream().map(request -> new PendingRequestListItem(
            request.getId(),
            request.getReceiver().getName(),
            request.getRequestAction().title,
            request.getRequester().getName(),
            request.getUserRole() == null ? request.getRoleGroup().getName() : request.getUserRole().getName(),
            request.getUserRole() == null ? "(Rollebuket)" : request.getUserRole().getItSystem().getName(),
            request.getUserRole() == null ? request.getRoleGroup().getDescription() : request.getUserRole().getDescription(),
			request.getRequestPostponedConstraints().stream().map(RequestPostponedConstraint::getValue).collect(Collectors.joining(",")),
            formatter.format(request.getRequestTimestamp()),
            request.getReason(),
			formatTimeFrame(request.getStartDate(), request.getEndDate())
        )).toList();
        model.addAttribute("pendingRequests", pendingApprovalRequests);

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

}
