package dk.digitalidentity.rc.controller.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.rest.dto.ItSystemWithRolesDTO;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SystemRoleService;

@RestController
public class ItSystemApi {

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SystemRoleService systemRoleService;

	@GetMapping(value = "/api/itsystems")
	public ResponseEntity<List<ItSystem>> getAllItSystems() {
		List<ItSystem> all = itSystemService.getAll();

		return new ResponseEntity<>(all, HttpStatus.OK);
	}

	@GetMapping(value = "/api/itsystem/{masterID}")
	public ResponseEntity<ItSystemWithRolesDTO> getItSystem(@PathVariable("masterID") String id) {
		ItSystem itSystem = itSystemService.getByMasterID(id);
		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		ItSystemWithRolesDTO itSystemDTO = new ItSystemWithRolesDTO();
		itSystemDTO.setName(itSystem.getName());
		itSystemDTO.setMasterId(itSystem.getMasterId());
		itSystemDTO.setLastModified(itSystem.getLastModified());
		itSystemDTO.setSystemRoles(systemRoleService.getByItSystem(itSystem));

		return new ResponseEntity<>(itSystemDTO, HttpStatus.OK);
	}
}
