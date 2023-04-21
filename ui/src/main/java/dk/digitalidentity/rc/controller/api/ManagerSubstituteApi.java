
package dk.digitalidentity.rc.controller.api;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.api.model.ManagerExtendedDTO;
import dk.digitalidentity.rc.controller.api.model.ManagerSubstituteDTO;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireApiOrganisationRole;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequireApiOrganisationRole
public class ManagerSubstituteApi {

	@Autowired
	private UserService userService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private OrgUnitService orgUnitService;

	@GetMapping("/api/manager")
	public ResponseEntity<List<ManagerExtendedDTO>> getManagerAssignments() {
		if (!configuration.getSubstituteManagerAPI().isEnabled()) {
			log.warn("Substitute Manager API is not enabled.");
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		List<ManagerExtendedDTO> users = userService.findManagers().stream().map(ManagerExtendedDTO::new).toList();

		return new ResponseEntity<>(users, HttpStatus.OK);
	}

	@PostMapping("/api/manager")
	public ResponseEntity<?> addManagerSubstituteAssignment(@RequestBody ManagerSubstituteDTO body) {
		if (!configuration.getSubstituteManagerAPI().isEnabled()) {
			log.warn("Substitute Manager API is not enabled.");
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		if (body == null) {
			return new ResponseEntity<>("Angiv managerSubstitute værdien", HttpStatus.BAD_REQUEST);
		}

		User manager = userService.getByUserId(body.getManagerUserId());
		if (manager == null) {
			return new ResponseEntity<>("Det angivne UUID peger ikke på en bruger", HttpStatus.NOT_FOUND);
		}
		else if (!userService.isManager(manager)) {
			return new ResponseEntity<>("Det angivne UUID peger ikke på en leder", HttpStatus.BAD_REQUEST);
		}

		// find substitute user
		User substitute = userService.getByUserId(body.getUserId());
		if (substitute == null) {
			return new ResponseEntity<>("Kunne ikke finde en stedfortræder med det angivne managerSubstitute som brugerId", HttpStatus.NOT_FOUND);
		}
		
		// find orgUnit
		OrgUnit orgUnit = orgUnitService.getByUuid(body.getOrgUnitUuid());
		if (orgUnit == null) {
			return new ResponseEntity<>("Could not find orgUnit for uuid:" + body.getOrgUnitUuid() + ". OrgUnit is mandatory", HttpStatus.NOT_FOUND);
		}
		
		ManagerSubstitute managerSubstituteMapping = new ManagerSubstitute();
		managerSubstituteMapping.setManager(manager);
		managerSubstituteMapping.setSubstitute(substitute);
		managerSubstituteMapping.setOrgUnit(orgUnit);
		managerSubstituteMapping.setAssignedTts(new Date());

		manager.getManagerSubstitutes().add(managerSubstituteMapping);

		userService.save(manager);

		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/api/manager")
	public ResponseEntity<?> removeManagerSubstituteAssignment(@RequestBody ManagerSubstituteDTO body) {
		if (!configuration.getSubstituteManagerAPI().isEnabled()) {
			log.warn("Substitute Manager API is not enabled.");
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		if (body == null) {
			return new ResponseEntity<>("Angiv managerSubstitute værdien - eller en null værdi hvis stedfortræderen ønskes fjernet", HttpStatus.BAD_REQUEST);
		}

		User manager = userService.getByUserId(body.getManagerUserId());
		if (manager == null) {
			return new ResponseEntity<>("Det angivne UUID peger ikke på en bruger", HttpStatus.NOT_FOUND);
		}
		else if (!userService.isManager(manager)) {
			return new ResponseEntity<>("Det angivne UUID peger ikke på en leder", HttpStatus.BAD_REQUEST);
		}

		manager.getManagerSubstitutes().removeIf(mapping -> Objects.equals(mapping.getSubstitute().getUserId(), body.getUserId()) && Objects.equals(mapping.getOrgUnit().getUuid(), body.getOrgUnitUuid()));

		userService.save(manager);

		return ResponseEntity.ok().build();
	}
}
