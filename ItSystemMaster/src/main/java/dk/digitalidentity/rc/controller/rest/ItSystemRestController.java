package dk.digitalidentity.rc.controller.rest;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.service.ItSystemService;

@RestController
public class ItSystemRestController {

	@Autowired
	private ItSystemService itSystemService;

	@PostMapping(value = "/rest/itsystem/name")
	public ResponseEntity<String> editItSystemName(String id, String name) {
		if (name == null || name.length() < 2) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		ItSystem itSystem = itSystemService.getByMasterID(id);
		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		ItSystem existingSystem = itSystemService.getFirstByName(name);
		if (existingSystem != null && existingSystem.getId() != itSystem.getId()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		itSystem.setLastModified(new Date());
		itSystem.setName(name);
		itSystemService.save(itSystem);

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
