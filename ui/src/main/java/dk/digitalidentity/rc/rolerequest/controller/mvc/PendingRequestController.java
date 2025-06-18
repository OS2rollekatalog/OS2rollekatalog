package dk.digitalidentity.rc.rolerequest.controller.mvc;

import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

@Controller
@RequestMapping("/ui/request/pending")
public class PendingRequestController {

    @Autowired
    private RequestService rolerequestService;

    record PendingRequestListItem(long id, String reciever, String action, String requester, String roleName,
                                  String itSystem, String description, String requestDate, String reason) {
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
            formatter.format(request.getRequestTimestamp()),
            request.getReason()
        )).toList();

        model.addAttribute("pendingRequests", pendingApprovalRequests);

        return "requestmodule/pending/index";
    }

//    record PendingRequestDetail(long id, String requester, String reason, String actionName) {
//    }
//    @GetMapping("/details/{requestId}")
//    public String requestDetails(Model model, @PathVariable long requestId) {
//
//
//        RoleRequest roleRequest = rolerequestService.getRoleRequestById(requestId).orElseThrow();
//        PendingRequestDetail request = new PendingRequestDetail(roleRequest.getId(), roleRequest.getRequester().getName(), roleRequest.getReason(), roleRequest.getRequestAction().title);
//
//
//        model.addAttribute("request", request);
//
//        return "requestmodule/pending/fragments/details :: pendingRequestDetail";
//    }
}
