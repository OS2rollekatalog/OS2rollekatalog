package dk.digitalidentity.rc.controller.mvc;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireManualEffectuationSystemOwnerOrAdministratorRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserService;
import lombok.RequiredArgsConstructor;

@RequireManualEffectuationSystemOwnerOrAdministratorRole
@Controller
@RequiredArgsConstructor
public class ManualAssignmentEffectuationController {
	private final UserService userService;
	private final ItSystemService itSystemService;

	@GetMapping(value = "/ui/manualeffectuation")
	public String list() {
		if (!SecurityUtil.hasDirectAdminRole()) {
			User user = userService.getByUserId(SecurityUtil.getUserId());
			if (user == null || itSystemService.findBySystemOwner(user).isEmpty()) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN);
			}
		}

		return "manualeffectuation/list";
	}
}
