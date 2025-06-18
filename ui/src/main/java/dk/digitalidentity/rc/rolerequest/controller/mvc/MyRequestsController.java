package dk.digitalidentity.rc.rolerequest.controller.mvc;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Controller
@RequestMapping("/ui/request/myrequests")
public class MyRequestsController {

	@Autowired
	private RequestService rolerequestService;

	@Autowired
	private UserService userService;

	record PendingRequest(long id, String itSystemName, String roleName, String status, String action,
						  boolean cancelable) {
	}

	record PendingRequestGroup(String uuid, Date requestDate, String action, String recieverName, long userroleCount,
							   long rolegroupCount, List<PendingRequest> requests, String status) {
	}

	@GetMapping
	public String myRequestList(Model model) {
		User user = userService.getByUserId(SecurityUtil.getUserId());

		Map<String, List<RoleRequest>> pendingRequests = rolerequestService.getRequestsForUserByGroup(user);

		List<PendingRequestGroup> pendingRequestGroupDTOs = new ArrayList<>();

		for (String key : pendingRequests.keySet()) {
			if (Objects.equals(key, "ungrouped")) {
				//Handle requests not in a group - Make a group for each request
				for (RoleRequest request : pendingRequests.get(key)) {
					PendingRequestGroup group = new PendingRequestGroup(
						"",
						request.getRequestTimestamp(),
						request.getRequestAction().title,
						request.getReceiver().getName(),
						request.getUserRole() == null ? 0 : 1,
						request.getRoleGroup() == null ? 0 : 1,
						List.of(
							new PendingRequest(
								request.getId(),
								request.getUserRole() == null ? "(Rollebuket)" : request.getUserRole().getItSystem().getName(),
								request.getUserRole() == null ? request.getRoleGroup().getName() : request.getUserRole().getName(),
								request.getStatus().getMessage(),
								request.getRequestAction().title,
								request.getStatus() == RequestApproveStatus.REQUESTED
							)
						),
						request.getStatus().getMessage()
						);
					pendingRequestGroupDTOs.add(group);
				}
			} else {
				//handle requests grouped together
				List<RoleRequest> requests = pendingRequests.get(key);

				PendingRequestGroup group = new PendingRequestGroup(
					key,
					requests.getFirst().getRequestTimestamp(),
					requests.getFirst().getRequestAction().title,
					requests.getFirst().getReceiver().getName(),
					requests.stream().filter(request -> request.getUserRole() != null).count(),
					requests.stream().filter(request -> request.getRoleGroup() != null).count(),
					requests.stream().map(request ->
						new PendingRequest(
							request.getId(),
							request.getUserRole() == null ? "(Rollebuket)" : request.getUserRole().getItSystem().getName(),
							request.getUserRole() == null ? request.getRoleGroup().getName() : request.getUserRole().getName(),
							request.getStatus().getMessage(),
							request.getRequestAction().title,
							request.getStatus() == RequestApproveStatus.REQUESTED
						)
					).toList(),
					groupedStatus(requests)
					);
				pendingRequestGroupDTOs.add(group);
			}
		}

		model.addAttribute("pendingRequestGroups", pendingRequestGroupDTOs);

		return "requestmodule/myrequests/index";
	}

	private static String groupedStatus(final List<RoleRequest> requests) {
		if (requests.isEmpty()) {
			return "";
		}
		final RequestApproveStatus firstStatus = requests.getFirst().getStatus();
		if (requests.stream().allMatch(request -> request.getStatus().equals(firstStatus))) {
			return firstStatus.getMessage();
		}
		return "html.enum.requestapprove.status.multiple";
	}
}
