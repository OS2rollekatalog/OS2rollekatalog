
package dk.digitalidentity.rc.controller.api;

import java.util.ArrayList;
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

    record ManagerRecord (
        /*@Schema(description = "The name of the manager")*/ String name,
        /*@Schema(description = "The userId of the manager")*/ String userId,
        /*@Schema(description = "List of substitutes for this manager")*/ List<SubstituteRecord> substitutes
    ) { }

    record SubstituteRecord (
    	/*@Schema(description = "The name of the substitute")*/ String name,
        /*@Schema(description = "The userId of the substitute")*/ String userId,
        /*@Schema(description = "The uuid of the orgunit the substitute is substite for")*/ String orgUnitUuid
    ) { }
    
	@GetMapping("/api/manager")
	public ResponseEntity<List<ManagerRecord>> getManagerAssignments() {
		if (!configuration.getSubstituteManagerAPI().isEnabled()) {
			log.warn("Substitute Manager API is not enabled.");
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		List<ManagerRecord> result = new ArrayList<>();
		
		for (User manager : userService.findManagers()) {
			ManagerRecord rManager = new ManagerRecord(manager.getName(), manager.getUserId(), new ArrayList<>());
			
			for (ManagerSubstitute substitute : manager.getManagerSubstitutes()) {
				rManager.substitutes().add(new SubstituteRecord(substitute.getSubstitute().getName(), substitute.getSubstitute().getUserId(), substitute.getOrgUnit().getUuid()));
			}
		}

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

    @PostMapping("/api/manager")
	public ResponseEntity<?> addManagerSubstituteAssignment(@RequestBody ManagerRecord input) {
		if (!configuration.getSubstituteManagerAPI().isEnabled()) {
			log.warn("Substitute Manager API is not enabled.");
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		User manager = userService.getByUserId(input.userId());
		if (manager == null) {
			return new ResponseEntity<>("Det angivne userId peger ikke på en bruger", HttpStatus.NOT_FOUND);
		}
		else if (!userService.isManager(manager)) {
			return new ResponseEntity<>("Det angivne userId peger ikke på en leder", HttpStatus.BAD_REQUEST);
		}

		if (input.substitutes() == null || input.substitutes().size() != 0) {
			return new ResponseEntity<>("Angiv én og kun én stedfortræder når der tilføjes", HttpStatus.BAD_REQUEST);
		}
		
		// find substitute user
		User substitute = userService.getByUserId(input.substitutes().get(0).userId());
		if (substitute == null) {
			return new ResponseEntity<>("Kunne ikke finde en stedfortræder med det angivne brugerId", HttpStatus.NOT_FOUND);
		}
		
		// find orgUnit
		OrgUnit orgUnit = orgUnitService.getByUuid(input.substitutes().get(0).orgUnitUuid());
		if (orgUnit == null) {
			return new ResponseEntity<>("Kunne ikke finde en enhed med det angivne uuid", HttpStatus.NOT_FOUND);
		}

		ManagerSubstitute newSubstitute = new ManagerSubstitute();
		newSubstitute.setManager(manager);
		newSubstitute.setSubstitute(substitute);
		newSubstitute.setOrgUnit(orgUnit);
		newSubstitute.setAssignedTts(new Date());

		for (ManagerSubstitute existingSubstitute : manager.getManagerSubstitutes()) {
			if (Objects.equals(newSubstitute.getSubstitute().getUserId(), existingSubstitute.getSubstitute().getUserId()) &&
				Objects.equals(newSubstitute.getOrgUnit().getUuid(), existingSubstitute.getOrgUnit().getUuid())) {
				
				return new ResponseEntity<>("Allerede stedfortræder", HttpStatus.NOT_MODIFIED);
			}
		}

		manager.getManagerSubstitutes().add(newSubstitute);
		userService.save(manager);

		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/api/manager")
	public ResponseEntity<?> removeManagerSubstituteAssignment(@RequestBody ManagerRecord input) {
		if (!configuration.getSubstituteManagerAPI().isEnabled()) {
			log.warn("Substitute Manager API is not enabled.");
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		User manager = userService.getByUserId(input.userId());
		if (manager == null) {
			return new ResponseEntity<>("Det angivne userId peger ikke på en bruger", HttpStatus.NOT_FOUND);
		}
		else if (!userService.isManager(manager)) {
			return new ResponseEntity<>("Det angivne userId peger ikke på en leder", HttpStatus.BAD_REQUEST);
		}

		if (input.substitutes() == null || input.substitutes().size() != 0) {
			return new ResponseEntity<>("Angiv én og kun én stedfortræder når der fjernes", HttpStatus.BAD_REQUEST);
		}
		
		String substituteUserId = input.substitutes().get(0).userId();
		String orgUnitUuid = input.substitutes().get(0).orgUnitUuid();

		manager.getManagerSubstitutes().removeIf(mapping ->
			Objects.equals(mapping.getSubstitute().getUserId(), substituteUserId) &&
			Objects.equals(mapping.getOrgUnit().getUuid(), orgUnitUuid));

		userService.save(manager);

		return ResponseEntity.ok().build();
	}
}
