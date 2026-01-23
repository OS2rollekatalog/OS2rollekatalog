package dk.digitalidentity.rc.controller.mvc;

import java.security.Principal;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@RequiredArgsConstructor
@Slf4j
@Controller
public class MyController {
	private final UserService userService;

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

}
