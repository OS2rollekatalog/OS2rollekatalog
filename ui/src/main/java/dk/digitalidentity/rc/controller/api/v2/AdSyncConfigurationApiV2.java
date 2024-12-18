package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.dao.model.ADConfiguration;
import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.json.ADConfigurationJSON;
import dk.digitalidentity.rc.security.RequireApiADSyncServiceRole;
import dk.digitalidentity.rc.security.RequireApiReadAccessRole;
import dk.digitalidentity.rc.service.ADConfigurationService;
import dk.digitalidentity.rc.service.ClientService;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.PendingADUpdateService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequireApiADSyncServiceRole
@Slf4j
@RestController
@SecurityRequirement(name = "ApiKey")
public class AdSyncConfigurationApiV2 {

	@Autowired
	private PendingADUpdateService pendingADUpdateService;

	@Autowired
	private UserService userService;
	
	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private DomainService domainService;

	@Autowired
	private ClientService clientService;

	@Autowired
	private ADConfigurationService adConfigurationService;


	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Returns the ADSyncService Configuration"),
			@ApiResponse(responseCode = "204", description = "A configuration has not yet been set up"),
			@ApiResponse(responseCode = "404", description = "No client found")})
	@Operation(summary = "Returns the configuration for ADSyncService for the given domain")
	@GetMapping("/api/v2/ad/getConfiguration/{domain}")
	public ResponseEntity<?> getConfiguration(@PathVariable String domain) {
		Domain realDomain = domainService.getByName(domain);
		Client client = clientService.getClientByDomain(realDomain);
		if (client == null) {
			return ResponseEntity.notFound().build();
		}

		ADConfiguration adConfiguration = adConfigurationService.getByClient(client);
		if (adConfiguration == null || adConfiguration.getJson() == null) {
			return ResponseEntity.noContent().build();
		}

		return ResponseEntity.ok().body(adConfiguration.getJson());
	}

	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Received and saved the ADConfiguration"),
			@ApiResponse(responseCode = "404", description = "No client found"),
			@ApiResponse(responseCode = "409", description = "A configuration for this domain already exists")})
	@Operation(summary = "Receives the ADConfiguration from the Client")
	@PostMapping("/api/v2/ad/writeConfiguration/{domain}")
	public ResponseEntity<HttpStatus> writeConfiguration(@PathVariable String domain, @RequestBody ADConfigurationJSON settings) {
		Domain realDomain = domainService.getByName(domain);
		Client client = clientService.getClientByDomain(realDomain);
		if (client == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		ADConfiguration adConfiguration = adConfigurationService.getByClient(client);
		if (adConfiguration != null) {
			log.error("Client from domain {} tried to bootstrap ADConfiguration, but has configuration already", domain);
			return new ResponseEntity<>(HttpStatus.CONFLICT);
		}

		ADConfiguration newADConfiguration = new ADConfiguration();
		newADConfiguration.setClient(client);
		newADConfiguration.setJson(settings);
		newADConfiguration.setVersion(1);
		newADConfiguration.setUpdatedBy("ADSyncService fra domænet: " + domain);
		adConfigurationService.save(newADConfiguration);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Received and saved error from client"),
			@ApiResponse(responseCode = "204", description = "A configuration has not yet been set up"),
			@ApiResponse(responseCode = "404", description = "No client found")})
	@Operation(summary = "Receives errors from the client if ADSyncService can't translate the configuration")
	@PostMapping("/api/v2/ad/error/{domain}")
	public ResponseEntity<HttpStatus> error(@PathVariable String domain, @RequestBody String errorMessage) {
		Domain realDomain = domainService.getByName(domain);
		Client client = clientService.getClientByDomain(realDomain);
		if (client == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		ADConfiguration adConfiguration = adConfigurationService.getByClient(client);
		if (adConfiguration == null) {
			log.error("Client from domain {} tried to report error, but has no configuration. Error: {}", domain, errorMessage);
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		}

		adConfiguration.setErrorMessage(errorMessage);
		adConfigurationService.save(adConfiguration);

		log.error("Received ADSyncService error from domain {}. Message: {}", domain, errorMessage);

		return new ResponseEntity<>(HttpStatus.OK);
	}


}
