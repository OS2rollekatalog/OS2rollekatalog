package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.controller.mvc.viewmodel.ClientDTO;
import dk.digitalidentity.rc.controller.validator.ClientDTOValidator;
import dk.digitalidentity.rc.dao.model.ADConfiguration;
import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.dao.model.enums.ClientIntegrationType;
import dk.digitalidentity.rc.dao.model.enums.VersionStatusEnum;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.ADConfigurationService;
import dk.digitalidentity.rc.service.ClientService;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.Select2Service;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;
import java.util.UUID;

@RequireAdministratorRole
@Controller
public class ClientController {

	@Autowired
	private ClientService clientService;

	@Autowired
	private ClientDTOValidator clientDTOValidator;

	@Autowired
	private DomainService domainService;

	@Autowired
	private ADConfigurationService adConfigurationService;

	@Autowired
	private Select2Service select2Service;

	@InitBinder("clientDTO")
	public void initClientBinder(WebDataBinder binder) {
		binder.addValidators(clientDTOValidator);
	}

	@GetMapping("/ui/client/list")
	public String list(Model model) {
		List<Client> clients = clientService.findAll();

		model.addAttribute("clients", clients);

		return "client/list";
	}

	@GetMapping("/ui/client/new")
	public String newClient(Model model) {
		ClientDTO clientDTO = new ClientDTO();
		clientDTO.setApiKey(UUID.randomUUID().toString());

		model.addAttribute("client", clientDTO);
		model.addAttribute("accessRoles", AccessRole.values());
		model.addAttribute("domains", domainService.getAll());

		return "client/new";
	}

	@PostMapping("/ui/client/new")
	public String newClientPost(Model model, @Valid @ModelAttribute("client") ClientDTO clientDTO, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			model.addAttribute(bindingResult.getAllErrors());
			model.addAttribute("client", clientDTO);
			model.addAttribute("accessRoles", AccessRole.values());
			model.addAttribute("domains", domainService.getAll());

			return "client/new";
		}

		Client client = new Client();
		client.setName(clientDTO.getName());
		client.setApiKey(clientDTO.getApiKey());
		client.setAccessRole(AccessRole.valueOf(clientDTO.getAccessRole()));
		client.setVersionStatus(VersionStatusEnum.UNKNOWN);
		client.setClientIntegrationType(ClientIntegrationType.valueOf(clientDTO.getIntegration()));

		if (ClientIntegrationType.valueOf(clientDTO.getIntegration()).equals(ClientIntegrationType.AD_SYNC_SERVICE)) {
			client.setDomain(domainService.getByName(clientDTO.getDomain()));
		}

		clientService.save(client);

		return "redirect:/ui/client/list";
	}

	@GetMapping("/ui/client/view/{clientId}")
	public String list(@PathVariable long clientId, Model model) throws Exception {
		Client client = clientService.getClientById(clientId);
		if (client == null) {
			return "redirect:/ui/client/list";
		}

		ClientDTO clientDTO = new ClientDTO();
		clientDTO.setName(client.getName());
		clientDTO.setApiKey(client.getApiKey());
		clientDTO.setAccessRole(client.getAccessRole().getMessageId());
		clientDTO.setVersion(client.getVersion());
		clientDTO.setOutdated(client.getVersionStatus() == VersionStatusEnum.OUTDATED);
		clientDTO.setIntegration(client.getClientIntegrationType().getMessage());
		clientDTO.setDomain(client.getDomain() == null ? null : client.getDomain().getName());

		model.addAttribute("client", clientDTO);
		model.addAttribute("isADSyncServiceClient", client.getClientIntegrationType().equals(ClientIntegrationType.AD_SYNC_SERVICE));

		return "client/view";
	}

	@GetMapping("/ui/client/edit/{clientId}")
	public String editClient(@PathVariable long clientId, Model model) {
		Client client = clientService.getClientById(clientId);
		if (client == null) {
			return "redirect:/ui/client/list";
		}

		ClientDTO clientDTO = new ClientDTO();
		clientDTO.setId(clientId);
		clientDTO.setName(client.getName());
		clientDTO.setApiKey(client.getApiKey());
		clientDTO.setAccessRole(client.getAccessRole().name());
		clientDTO.setIntegration(client.getClientIntegrationType().name());
		clientDTO.setDomain(client.getDomain() == null ? null : client.getDomain().getName());

		model.addAttribute("client", clientDTO);
		model.addAttribute("accessRoles", AccessRole.values());
		model.addAttribute("domains", domainService.getAll());


		return "client/edit";
	}

	@PostMapping("/ui/client/edit/{clientId}")
	public String editClientPost(Model model, @Valid @ModelAttribute("client") ClientDTO clientDTO, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			model.addAttribute(bindingResult.getAllErrors());
			model.addAttribute("client", clientDTO);
			model.addAttribute("accessRoles", AccessRole.values());
			model.addAttribute("domains", domainService.getAll());

			return "client/edit";
		}

		Client client = clientService.getClientById(clientDTO.getId());
		if (client == null) {
			return "redirect:/ui/client/list";
		}

		client.setName(clientDTO.getName());
		client.setApiKey(clientDTO.getApiKey());
		client.setAccessRole(AccessRole.valueOf(clientDTO.getAccessRole()));
		client.setClientIntegrationType(ClientIntegrationType.valueOf(clientDTO.getIntegration()));

		if (ClientIntegrationType.valueOf(clientDTO.getIntegration()).equals(ClientIntegrationType.AD_SYNC_SERVICE)) {
			client.setDomain(domainService.getByName(clientDTO.getDomain()));
		}

		clientService.save(client);

		return "redirect:/ui/client/list";
	}

	@GetMapping("/ui/client/delete/{clientId}")
	public String deleteClient(@PathVariable long clientId) {
		Client client = clientService.getClientById(clientId);
		if (client != null) {
			clientService.delete(client);
		}

		return "redirect:/ui/client/list";
	}

	@GetMapping("/ui/client/adsyncservice/{clientId}")
	public String adSyncServiceClient(@PathVariable long clientId, Model model) {
		Client client = clientService.getClientById(clientId);
		if (client == null || !client.getClientIntegrationType().equals(ClientIntegrationType.AD_SYNC_SERVICE)) {
			return "redirect:/ui/client/list";
		}

		select2Service.clearCache(); // We always want new it-systems
		ADConfiguration adConfiguration = adConfigurationService.getByClient(client);
		if (adConfiguration != null) {
			model.addAttribute("configured", true);
			model.addAttribute("settings", adConfiguration.getJson());
			model.addAttribute("clientErrors", adConfiguration.getErrorMessage());
		}
		model.addAttribute("clientID", clientId);
		model.addAttribute("clientName", client.getName());
		model.addAttribute("itSystemList", select2Service.getItSystemList());
		return "client/adSyncService";
	}

}
