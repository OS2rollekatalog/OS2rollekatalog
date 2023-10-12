package dk.digitalidentity.rc.attestation.controller.rest;

import dk.digitalidentity.rc.attestation.service.ItSystemUserRolesAttestationService;
import dk.digitalidentity.rc.security.RequireItSystemResponsibleRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequireItSystemResponsibleRole
@RestController
public class ItSystemAttestationRestController {

	@Autowired
	private ItSystemUserRolesAttestationService attestationService;

	@PostMapping("/rest/attestation/v2/itsystems/{itSystemId}/userroles/{userRoleId}/approve")
	@Timed("attestation.controller.rest.it_system_attestation_rest_controller.save_global_role1.timer")
	public ResponseEntity<?> saveGlobalRole(@PathVariable Long itSystemId, @PathVariable Long userRoleId) {
		attestationService.verifyUserRole(itSystemId, userRoleId, SecurityUtil.getUserId());
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping("/rest/attestation/v2/itsystems/{itSystemId}/userroles/{userRoleId}/reject")
	@Timed("attestation.controller.rest.it_system_attestation_rest_controller.save_global_role2.timer")
	public ResponseEntity<?> saveGlobalRole(@PathVariable Long itSystemId, @PathVariable Long userRoleId, @RequestBody String remarks) {
		attestationService.rejectUserRole(itSystemId, userRoleId, SecurityUtil.getUserId(), remarks);
		return new ResponseEntity<>(HttpStatus.OK);
	}


}
