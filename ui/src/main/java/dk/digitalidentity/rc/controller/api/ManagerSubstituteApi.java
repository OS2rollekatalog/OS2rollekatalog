
package dk.digitalidentity.rc.controller.api;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.api.model.ManagerExtendedDTO;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireApiOrganisationRole;
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

	@GetMapping("/api/manager")
	public ResponseEntity<List<ManagerExtendedDTO>> getManagerAssignments() {
		if (!configuration.getSubstituteManagerAPI().isEnabled()) {
			log.warn("Substitute Manager API is not enabled.");
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		List<ManagerExtendedDTO> users = userService.findManagers().stream().map(u -> new ManagerExtendedDTO(u)).collect(Collectors.toList());

		return new ResponseEntity<>(users, HttpStatus.OK);
	}

	@PostMapping("/api/manager/{uuid}")
	public ResponseEntity<?> setManagerAssignment(@RequestBody ManagerExtendedDTO body, @PathVariable(name = "uuid") String uuid) {
		if (!configuration.getSubstituteManagerAPI().isEnabled()) {
			log.warn("Substitute Manager API is not enabled.");
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		User user = userService.getByUuid(uuid);
		if (user == null) {
			return new ResponseEntity<>("Det angivne UUID peger ikke på en bruger", HttpStatus.NOT_FOUND);
		}
		else if (!userService.isManager(user)) {
			return new ResponseEntity<>("Det angivne UUID peger ikke på en leder", HttpStatus.BAD_REQUEST);		
		}

		if (body == null) {
			return new ResponseEntity<>("Angiv managerSubstitute værdien - eller en null værdi hvis stedfortræderen ønskes fjernet", HttpStatus.BAD_REQUEST);
		}

		if (body.getManagerSubstitute() != null) {
			User subManager = userService.getByUserId(body.getManagerSubstitute());
			if (subManager == null) {
				return new ResponseEntity<>("Kunne ikke finde en stedfortræder med det angivne managerSubstitute som brugerId", HttpStatus.NOT_FOUND);
			}

			user.setManagerSubstitute(subManager);
		}
		else {
			user.setManagerSubstitute(null);
		}

		userService.save(user);

		return ResponseEntity.ok().build();
	}
}
