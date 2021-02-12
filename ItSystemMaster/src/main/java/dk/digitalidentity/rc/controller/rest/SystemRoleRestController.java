package dk.digitalidentity.rc.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.service.SystemRoleService;

@RestController
public class SystemRoleRestController {

	@Autowired
	private SystemRoleService systemRoleService;

	@PostMapping(value = "/rest/systemrole/delete/{id}")
	@ResponseBody
	public HttpEntity<String> deleteSystemRole(Model model, @PathVariable("id") long id) {
		systemRoleService.delete(id);

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
