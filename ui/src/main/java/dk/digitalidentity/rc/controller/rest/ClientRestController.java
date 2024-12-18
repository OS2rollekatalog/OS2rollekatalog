package dk.digitalidentity.rc.controller.rest;

import dk.digitalidentity.rc.controller.rest.model.ManagerSubstituteAssignmentDTO;
import dk.digitalidentity.rc.dao.model.ADConfiguration;
import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.json.ADConfigurationJSON;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ADConfigurationService;
import dk.digitalidentity.rc.service.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RequireAdministratorRole
@RestController
public class ClientRestController {

	@Autowired
	private ClientService clientService;

	@Autowired
	private ADConfigurationService adConfigurationService;

	@PostMapping("/rest/client/adsyncservice/{clientId}")
	@ResponseBody
	public ResponseEntity<?> updateADConfiguration(@PathVariable long clientId,  @RequestBody ADConfigurationJSON adConfigurationJSON) {
		Client client = clientService.getClientById(clientId);
		if (client == null) {
			return ResponseEntity.badRequest().build();
		}

		ADConfiguration adConfiguration = adConfigurationService.getByClient(client);
		if (adConfiguration == null) {
			return ResponseEntity.badRequest().build();
		}

		if (adConfigurationJSON == null) {
			return ResponseEntity.badRequest().build();
		}

		ADConfiguration newConfiguration = new ADConfiguration();
		newConfiguration.setClient(client);
		newConfiguration.setVersion(adConfiguration.getVersion() + 1);
		newConfiguration.setJson(adConfigurationJSON);
		newConfiguration.setUpdatedBy(SecurityUtil.getUserFullname());

		adConfigurationService.save(newConfiguration);

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
