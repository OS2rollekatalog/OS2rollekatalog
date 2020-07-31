package dk.digitalidentity.rc.controller.rest;

import java.util.Date;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SystemRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemRoleRestController {

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SystemRoleService systemRoleService;

	@PostMapping(value = "/rest/systemrole/delete/{id}")
	@ResponseBody
	public HttpEntity<String> deleteSystemRole(Model model, @PathVariable("id") long id) {
		SystemRole systemRole = systemRoleService.getById(id);
		if (systemRole == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		ItSystem itSystem = systemRole.getItSystem();
		itSystem.setLastModified(new Date());
		itSystemService.save(itSystem);

		systemRoleService.delete(systemRole);

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
