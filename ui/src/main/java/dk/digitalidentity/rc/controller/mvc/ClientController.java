package dk.digitalidentity.rc.controller.mvc;

import java.util.List;
import java.util.UUID;

import javax.validation.Valid;

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

import dk.digitalidentity.rc.controller.mvc.viewmodel.ClientDTO;
import dk.digitalidentity.rc.controller.validator.ClientDTOValidator;
import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.ClientService;

@RequireAdministratorRole
@Controller
public class ClientController {

	@Autowired
	private ClientService clientService;

	@Autowired
	private ClientDTOValidator clientDTOValidator;

	@InitBinder
	public void initClientBinder(WebDataBinder binder) {
		binder.setValidator(clientDTOValidator);
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

		return "client/new";
	}

	@PostMapping("/ui/client/new")
	public String newClientPost(Model model, @Valid @ModelAttribute("client") ClientDTO clientDTO, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			model.addAttribute(bindingResult.getAllErrors());
			model.addAttribute("client", clientDTO);
			model.addAttribute("accessRoles", AccessRole.values());

			return "client/new";
		}

		Client client = new Client();
		client.setName(clientDTO.getName());
		client.setApiKey(clientDTO.getApiKey());
		client.setAccessRole(AccessRole.valueOf(clientDTO.getAccessRole()));

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

		model.addAttribute("client", clientDTO);

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

		model.addAttribute("client", clientDTO);
		model.addAttribute("accessRoles", AccessRole.values());

		return "client/edit";
	}

	@PostMapping("/ui/client/edit/{clientId}")
	public String editClientPost(Model model, @Valid @ModelAttribute("client") ClientDTO clientDTO, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			model.addAttribute(bindingResult.getAllErrors());
			model.addAttribute("client", clientDTO);
			model.addAttribute("accessRoles", AccessRole.values());

			return "client/edit";
		}

		Client client = clientService.getClientById(clientDTO.getId());
		if (client == null) {
			return "redirect:/ui/client/list";
		}

		client.setName(clientDTO.getName());
		client.setApiKey(clientDTO.getApiKey());
		client.setAccessRole(AccessRole.valueOf(clientDTO.getAccessRole()));

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
}
