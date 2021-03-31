package dk.digitalidentity.rc.controller.mvc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireAssignerOrManagerRole;
import dk.digitalidentity.rc.security.RequireManagerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.RequestApproveService;
import dk.digitalidentity.rc.service.UserService;

@Controller
@RequireAssignerOrManagerRole
public class ManagerController {

	@Autowired
	private RequestApproveService requestApproveService;
	
	@Autowired
	private UserService userService;
	
	
	@RequireManagerRole
	@GetMapping("/ui/manager/substitute")
	public String getSubstitute(Model model) {
		User manager = getManager();
		if (manager == null) {
			return "redirect:/";
		}

		model.addAttribute("substitute", manager.getManagerSubstitute());
		
		return "manager/substitute";
	}

	@GetMapping("/ui/users/requests")
	public String getRequests(Model model) {
		model.addAttribute("requests", requestApproveService.getPendingRequests());
		
		return "manager/requests";
	}

	private User getManager() {
		return userService.getByUserId(SecurityUtil.getUserId());
	}
}
