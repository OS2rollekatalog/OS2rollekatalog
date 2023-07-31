package dk.digitalidentity.rc.controller.api;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.api.dto.OrganisationV2DTO;
import dk.digitalidentity.rc.controller.api.exception.BadRequestException;
import dk.digitalidentity.rc.controller.api.model.OrganisationDTO;
import dk.digitalidentity.rc.controller.api.model.OrganisationImportResponse;
import dk.digitalidentity.rc.controller.api.model.UserDTO;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.KLEMapping;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.security.RequireApiOrganisationRole;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.OrganisationExporter;
import dk.digitalidentity.rc.service.OrganisationImporter;
import dk.digitalidentity.rc.service.OrganisationImporterOld;

@RequireApiOrganisationRole
@RestController
@RequestMapping("/api")
public class OrganisationApi {
	private static final Logger log = LoggerFactory.getLogger(OrganisationApi.class);

	// TODO: remove once we do not need it anymore
	@Autowired
	private OrganisationImporterOld organisationImporterOld;
	
	@Autowired
	private OrganisationImporter organisationImporter;

	@Autowired
	private OrganisationExporter organisationExporter;

	@Autowired
	private DomainService domainService;
	
	@Autowired
	private OrgUnitService orgUnitService;

	@Value("${org.errorOnOldApi:true}")
	private boolean errorOnOldApi;
	
	public record KlePayload(List<String> klePerforming, List<String> kleInterest) {}
	
	@PostMapping(value = "/orgunit/kle/{uuid}")
    public ResponseEntity<String> overwriteUserRoleAssignments(@PathVariable("uuid") String uuid, @RequestBody KlePayload klePayload) {
		OrgUnit orgUnit = orgUnitService.getByUuid(uuid);
		if (orgUnit == null) {
	        return new ResponseEntity<>("Der findes ingen enhed med UUID = " + uuid, HttpStatus.NOT_FOUND);
		}
		
		OrgUnit dummy = new OrgUnit();
		dummy.setKles(new ArrayList<>());

		if (klePayload.kleInterest() != null) {
			for (String kle : klePayload.kleInterest()) {
				KLEMapping mapping = new KLEMapping();
				mapping.setOrgUnit(orgUnit);
				mapping.setCode(kle);
				mapping.setAssignmentType(KleType.INTEREST);
				
				dummy.getKles().add(mapping);
			}
		}

		if (klePayload.klePerforming() != null) {
			for (String kle : klePayload.klePerforming()) {
				KLEMapping mapping = new KLEMapping();
				mapping.setOrgUnit(orgUnit);
				mapping.setCode(kle);
				mapping.setAssignmentType(KleType.PERFORMING);
				
				dummy.getKles().add(mapping);
			}
		}

		// TODO: not the best way to perform an update, but this is how we roll :)
		orgUnit.getKles().clear();
		orgUnit.getKles().addAll(dummy.getKles());
		orgUnitService.save(orgUnit);
		
        return new ResponseEntity<>(HttpStatus.OK);
	}
	
	@PostMapping(value = "/organisation")
	public synchronized ResponseEntity<?> importOrgUnits(@RequestBody String request) {
		if (errorOnOldApi) {
			log.error("Calling deprecated API v1");
		}

		throw new BadRequestException("This endpoint does not exist anymore - please upgrade to /api/organisation/v3");
	}
	
	@PostMapping(value = "/organisation/v2")
	@Transactional(rollbackFor = Exception.class)
	public synchronized ResponseEntity<?> importOrgUnitsV2(@RequestBody OrganisationV2DTO organisation) {
		if (errorOnOldApi) {
			log.error("Calling deprecated API v2");
		}
		
		try {
			OrganisationImportResponse response = organisationImporterOld.bigImportV2(organisation);

			if (response.containsChanges()) {
				log.info("update completed: " + response.toString());
			}

			return new ResponseEntity<>(response, HttpStatus.OK);
		}
		catch (Exception ex) {
			log.error("Import failed on v2!", ex);
			
			throw new BadRequestException(ex.getMessage());
		}
	}
	
	@PostMapping(value = "/organisation/v3")
	@Transactional(rollbackFor = Exception.class)
	public synchronized ResponseEntity<?> importOrgUnitsV3(@RequestBody OrganisationDTO organisation, @RequestParam(required = false) String domain) {
		try {
			Domain syncDomain = domainService.getDomainOrPrimary(domain);
			if (syncDomain == null) {
				return new ResponseEntity<>("Failed to find domain with name " + domain, HttpStatus.NOT_FOUND);
			}

			OrganisationImportResponse response = organisationImporter.fullSync(organisation, syncDomain);

			if (response.containsChanges()) {
				log.info("full update completed: " + response.toString());
			}

			return new ResponseEntity<>(response, HttpStatus.OK);
		}
		catch (Exception ex) {
			log.error("Import failed on v3!", ex);
			
			throw new BadRequestException(ex.getMessage());
		}
	}

	@PostMapping(value = "/organisation/v3/delta")
	@Transactional(rollbackFor = Exception.class)
	public synchronized ResponseEntity<?> importUsersDeltaV3(@RequestBody List<UserDTO> users, @RequestParam(required = false) String domain) {
		try {
			Domain syncDomain = domainService.getDomainOrPrimary(domain);
			if (syncDomain == null) {
				return new ResponseEntity<>("Failed to find domain with name " + domain, HttpStatus.NOT_FOUND);
			}

			OrganisationImportResponse response = organisationImporter.deltaSync(users, syncDomain);

			if (response.containsChanges()) {
				log.info("delta update completed: " + response.toString());
			}

			return new ResponseEntity<>(response, HttpStatus.OK);
		}
		catch (Exception ex) {
			log.error("Import failed on v3!", ex);
			
			throw new BadRequestException(ex.getMessage());
		}
	}

	@GetMapping(value = "/organisation/v3")
	@Transactional(rollbackFor = Exception.class)
	public synchronized ResponseEntity<?> getOrgUnitsHierarchy() {
		OrganisationDTO organisationDTO = organisationExporter.getOrganisationDTO();
		
		return ResponseEntity.ok(organisationDTO);
	}
}
