package dk.digitalidentity.rc.attestation.controller.rest;

import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentDTO;
import dk.digitalidentity.rc.attestation.service.ItSystemUsersAttestationService;
import dk.digitalidentity.rc.security.SecurityUtil;
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
public class ItSystemRoleAssignmentAttestationRestController {
	@Autowired
	private ItSystemUsersAttestationService  itSystemUsersAttestationService;

	@PostMapping("/rest/attestation/v2/itsystems/{itSystemId}/users/{userUuid}/approve")
	@Timed("attestation.controller.rest.it_system_role_assignment_attestation_rest_controller.accept_user_attestation.timer")
	public ResponseEntity<?> acceptUserAttestation(@PathVariable long itSystemId, @PathVariable String userUuid) {
		itSystemUsersAttestationService.verifyUser(itSystemId, userUuid, SecurityUtil.getUserId());
		return new ResponseEntity<>(HttpStatus.OK);
	}

	record RemarkDTO (String remarks, List<RoleAssignmentDTO> notApproved) {}
	@PostMapping("/rest/attestation/v2/itsystems/{itSystemId}/users/{userUuid}/reject")
	@Timed("attestation.controller.rest.it_system_role_assignment_attestation_rest_controller.reject_user_attestation.timer")
	public ResponseEntity<?> rejectUserAttestation(@PathVariable long itSystemId, @PathVariable String userUuid, @RequestBody RemarkDTO dto) {
		if (dto.remarks() == null || dto.remarks().trim().equals("")) {
			return new ResponseEntity<>("Der skal angives ændringsønsker", HttpStatus.BAD_REQUEST);
		}
		itSystemUsersAttestationService.rejectUser(itSystemId, userUuid, dto.remarks(), dto.notApproved(), SecurityUtil.getUserId());
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping("/rest/attestation/v2/itsystems/{itSystemId}/orgunits/{orgUnitUuid}/approve")
	@Timed("attestation.controller.rest.it_system_role_assignment_attestation_rest_controller.accept_org_unit_attestation.timer")
	public ResponseEntity<?> acceptOrgUnitAttestation(@PathVariable long itSystemId, @PathVariable String orgUnitUuid) {
		itSystemUsersAttestationService.verifyOu(itSystemId, orgUnitUuid, SecurityUtil.getUserId());
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping("/rest/attestation/v2/itsystems/{itSystemId}/orgunits/{orgUnitUuid}/reject")
	@Timed("attestation.controller.rest.it_system_role_assignment_attestation_rest_controller.reject_org_unit_attestation.timer")
	public ResponseEntity<?> rejectOrgUnitAttestation(@PathVariable long itSystemId, @PathVariable String orgUnitUuid, @RequestBody String remarks) {
		if (remarks == null || remarks.trim().equals("")) {
			return new ResponseEntity<>("Der skal angives ændringsønsker", HttpStatus.BAD_REQUEST);
		}
		itSystemUsersAttestationService.rejectOu(itSystemId, orgUnitUuid, remarks, SecurityUtil.getUserId());
		return new ResponseEntity<>(HttpStatus.OK);
	}
}
