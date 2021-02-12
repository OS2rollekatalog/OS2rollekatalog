package dk.digitalidentity.rc.controller.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import dk.digitalidentity.rc.controller.api.dto.OrganisationV2DTO;
import dk.digitalidentity.rc.controller.api.exception.BadRequestException;
import dk.digitalidentity.rc.controller.api.model.OrgUnitAM;
import dk.digitalidentity.rc.controller.api.model.OrganisationDTO;
import dk.digitalidentity.rc.controller.api.model.OrganisationImportResponse;
import dk.digitalidentity.rc.service.OrganisationImporter;
import dk.digitalidentity.rc.service.OrganisationImporterOld;

@RestController
@RequestMapping("/api")
public class OrganisationApi {
	private static final Logger log = LoggerFactory.getLogger(OrganisationApi.class);

	// TODO: remove once we do not need it anymore
	@Autowired
	private OrganisationImporterOld organisationImporterOld;
	
	@Autowired
	private OrganisationImporter organisationImporter;

	@PostMapping(value = "/organisation")
	@Transactional(rollbackFor = Exception.class)
	public synchronized ResponseEntity<?> importOrgUnits(@RequestBody String request) {
		log.warn("Calling deprecated API v1");

		try {
			ObjectMapper mapper = new ObjectMapper();
			OrgUnitAM rootOrgUnit = mapper.readValue(request, OrgUnitAM.class);

			OrganisationImportResponse response = organisationImporterOld.bigImport(rootOrgUnit);

			if (response.containsChanges()) {
				log.info("update completed: " + response.toString());
			}
			
			return new ResponseEntity<>(response, HttpStatus.OK);
		} catch (Exception ex) {
			log.error("Import failed on v1!", ex);
			log.warn("Bad input: " + request);
			throw new BadRequestException(ex.getMessage());
		}
	}
	
	@PostMapping(value = "/organisation/v2")
	@Transactional(rollbackFor = Exception.class)
	public synchronized ResponseEntity<?> importOrgUnitsV2(@RequestBody OrganisationV2DTO organisation) {
		log.warn("Calling deprecated API v2");
		
		try {
			OrganisationImportResponse response = organisationImporterOld.bigImportV2(organisation);

			if (response.containsChanges()) {
				log.info("update completed: " + response.toString());
			}

			return new ResponseEntity<>(response, HttpStatus.OK);
		} catch (Exception ex) {
			log.error("Import failed on v2!", ex);
			throw new BadRequestException(ex.getMessage());
		}
	}
	
	@PostMapping(value = "/organisation/v3")
	@Transactional(rollbackFor = Exception.class)
	public synchronized ResponseEntity<?> importOrgUnitsV3(@RequestBody OrganisationDTO organisation) {
		try {
			OrganisationImportResponse response = organisationImporter.fullSync(organisation);

			if (response.containsChanges()) {
				log.info("update completed: " + response.toString());
			}

			return new ResponseEntity<>(response, HttpStatus.OK);
		} catch (Exception ex) {
			log.error("Import failed on v3!", ex);
			throw new BadRequestException(ex.getMessage());
		}
	}
}
