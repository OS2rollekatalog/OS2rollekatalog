package dk.digitalidentity.rc.controller.api;

import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.Setting;
import dk.digitalidentity.rc.security.RequireApiReadAccessRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ClientService;
import dk.digitalidentity.rc.service.SettingsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RequireApiReadAccessRole
@Slf4j
@RestController
@SecurityRequirement(name = "ApiKey")
public class LogApi {

	@Autowired
	private ClientService clientService;

	@Autowired
	private SettingsService settingsService;

	@GetMapping("/api/uploadLog")
	public ResponseEntity<?> uploadLog(HttpServletRequest httpServletRequest) {
		Client client = SecurityUtil.getClient();
		if (client == null) {
			log.error("Could not extract client from request!");
			return new ResponseEntity<>("Unknown client", HttpStatus.FORBIDDEN);
		}

		boolean hasRequestedLog = false;
		Setting setting = settingsService.getByKey("REQUEST_LOG_" + client.getId());
		if (setting != null && setting.getValue().equalsIgnoreCase("true")) {
			log.info("Log has been requested for client with name " + client.getName() + " and id " + client.getId());
			hasRequestedLog = true;
			setting.setValue("false");
			settingsService.save(setting);
		}

		return new ResponseEntity<>(hasRequestedLog, HttpStatus.OK);
	}
}
