package dk.digitalidentity.rc.attestation.controller.rest;

import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentDTO;
import dk.digitalidentity.rc.attestation.service.OrganisationAttestationService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.SettingsService;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class OrgUnitAttestationRestController {

	@Autowired
	private SettingsService settingsService;

	@Autowired
	private OrganisationAttestationService organisationAttestationService;

	@PostMapping("/rest/attestation/v2/orgunits/{orgUnitUuid}/users/{userUuid}/approve")
	@Timed("attestation.controller.rest.org_unit_attestation_rest_controller.accept_user_attestation.timer")
	public ResponseEntity<?> acceptUserAttestation(@PathVariable String orgUnitUuid, @PathVariable String userUuid) {
		organisationAttestationService.verifyUser(orgUnitUuid, userUuid, SecurityUtil.getUserId());
		return new ResponseEntity<>(HttpStatus.OK);
	}

	record RemarkDTO (String remarks, List<RoleAssignmentDTO> notApproved) {}
	@PostMapping("/rest/attestation/v2/orgunits/{orgUnitUuid}/users/{userUuid}/reject")
	@Timed("attestation.controller.rest.org_unit_attestation_rest_controller.reject_user_attestation.timer")
	public ResponseEntity<?> rejectUserAttestation(@PathVariable String orgUnitUuid, @PathVariable String userUuid, @RequestBody RemarkDTO dto) {
		if (dto.remarks() == null || dto.remarks().trim().equals("")) {
			return new ResponseEntity<>("Der skal angives ændringsønsker", HttpStatus.BAD_REQUEST);
		}
		organisationAttestationService.rejectUser(orgUnitUuid, userUuid, SecurityUtil.getUserId(), dto.remarks(), dto.notApproved());
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping("/rest/attestation/v2/orgunits/{orgUnitUuid}/users/{userUuid}/delete")
	@Timed("attestation.controller.rest.org_unit_attestation_rest_controller.request_delete_user_attestation.timer")
	public ResponseEntity<?> requestDeleteUserAttestation(@PathVariable String orgUnitUuid, @PathVariable String userUuid) {
		if (!settingsService.isADAttestationEnabled()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		organisationAttestationService.requestAdRemoval(orgUnitUuid, userUuid, SecurityUtil.getUserId());
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping("/rest/attestation/v2/orgunits/{orgUnitUuid}/roles/approve")
	@Timed("attestation.controller.rest.org_unit_attestation_rest_controller.accept_org_unit_roles_attestation.timer")
	public ResponseEntity<?> acceptOrgUnitRolesAttestation(@PathVariable String orgUnitUuid) {
		organisationAttestationService.acceptOrgUnitRoles(orgUnitUuid, SecurityUtil.getUserId());
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping("/rest/attestation/v2/orgunits/{orgUnitUuid}/roles/reject")
	@Timed("attestation.controller.rest.org_unit_attestation_rest_controller.reject_org_unit_roles_attestation.timer")
	public ResponseEntity<?> rejectOrgUnitRolesAttestation(@PathVariable String orgUnitUuid, @RequestBody String remarks) {
		organisationAttestationService.rejectOrgUnitRoles(orgUnitUuid, SecurityUtil.getUserId(), remarks);
		return new ResponseEntity<>(HttpStatus.OK);
	}


}
