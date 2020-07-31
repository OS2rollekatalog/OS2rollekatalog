package dk.digitalidentity.rc.controller.mvc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.service.SystemRoleService;

@RequireReadAccessRole
@Controller
public class SystemRoleController {

	@Autowired
	private SystemRoleService systemRoleService;

	@GetMapping(value = { "/ui/systemrole/{id}/userroles" })
	public String editItSystem(Model model, @PathVariable("id") long id) {
		SystemRole systemRole = systemRoleService.getById(id);
		if (systemRole == null) {
			return "redirect:/ui/itsystem/list";
		}

		model.addAttribute("itSystem", systemRole.getItSystem());
		model.addAttribute("userRoles", systemRoleService.userRolesWithSystemRole(systemRole));

		return "systemrole/userroles";
	}
}
