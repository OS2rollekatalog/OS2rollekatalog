package dk.digitalidentity.rc.controller.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.api.dto.ChangeCicsPasswordDTO;
import dk.digitalidentity.rc.security.RequireApiCicsAdminRole;
import dk.digitalidentity.rc.service.cics.KspCicsService;
import dk.digitalidentity.rc.service.cics.model.KspChangePasswordResponse;

@RequireApiCicsAdminRole
@RestController
public class KspCicsApi {

	@Autowired
	private KspCicsService kspCicsService;

	@PostMapping("/api/cics/changepassword")
	@ResponseBody
	public ResponseEntity<?> changePassword(@RequestBody ChangeCicsPasswordDTO request) {
		KspChangePasswordResponse response = kspCicsService.updateKspCicsPassword(request.getUsername(), request.getNewPassword());

		if (!response.isSuccess()) {
			if (response.getHttp() != null) {
				return new ResponseEntity<>(response.getResponse(), response.getHttp());
			}
			else {
				return new ResponseEntity<>(response.getResponse(), HttpStatus.BAD_REQUEST);
			}
		}

		return ResponseEntity.ok().build();
	}
}
