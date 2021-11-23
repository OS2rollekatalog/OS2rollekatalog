package dk.digitalidentity.rc.controller.mvc;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireRequesterRole;
import dk.digitalidentity.rc.service.RequestApproveService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.log4j.Log4j;

@Log4j
@Controller
public class MyController {

	@Autowired
	private UserService userService;
	
	@Autowired
	private RequestApproveService requestApproveService;

	@GetMapping("/ui/my")
	public String my(Model model, Principal principal) {
		User user = userService.getByUserId(principal.getName());
		if (user == null) {
			log.warn("principal: " + principal.getName() + " does not exist");
			return "redirect:/";
		}

		model.addAttribute("user", user);
		model.addAttribute("assignments", userService.getAllUserRoleAndRoleGroupAssignments(user));
		
		return "users/my";
	}
	
	@RequireRequesterRole
	@GetMapping("/ui/my/requests")
	public String myRequests(Model model, Principal principal) {
		User user = userService.getByUserId(principal.getName());
		if (user == null) {
			log.warn("principal: " + principal.getName() + " does not exist");
			return "redirect:/";
		}

		model.addAttribute("user", user);
		model.addAttribute("requests", requestApproveService.getRequestByRequester(user));

		return "users/myrequests";
	}
}
